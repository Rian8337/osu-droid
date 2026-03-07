package ru.nsu.ccfit.zuev.osu.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.cert.Certificate
import java.security.spec.ECGenParameterSpec

/**
 * Manages hardware-backed key attestation using the Android KeyStore system.
 *
 * On devices with StrongBox (dedicated security chip), the key pair is generated and stored
 * inside StrongBox. On devices with only a TEE (Trusted Execution Environment), it falls back
 * to TEE-backed storage. Both provide hardware-level protection that prevents the private key
 * from ever leaving secure hardware — even a rooted device or Frida injection cannot extract it.
 *
 * The attestation certificate chain produced during key generation is signed by the hardware
 * itself and chains up to a Google Hardware Attestation Root CA. The server verifies this chain
 * to confirm the request originated from a genuine, unmodified APK on real Android hardware.
 *
 * Key lifecycle:
 * - A fresh key pair is generated for each login session using the server-provided challenge nonce.
 * - The challenge is embedded in the attestation extension (OID 1.3.6.1.4.1.11129.2.1.17),
 *   making each chain cryptographically bound to a single login attempt (replay-proof).
 * - The old key alias is deleted before each new key is generated.
 */
object HardwareAttestationManager {

    private const val TAG = "HardwareAttestation"

    /** KeyStore alias for the current session's attestation key pair. */
    const val KEY_ALIAS = "osu_attest_key"

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"

    /**
     * Whether this device supports hardware key attestation at all.
     * True for all devices on API 24+ with a TEE; StrongBox additionally requires API 28+.
     * Since minSdkVersion is 24, this should be universally true in practice.
     */
    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    /**
     * Generates a new EC (secp256r1) key pair inside secure hardware, embedding [challenge] as
     * the attestation challenge. The resulting certificate chain is hardware-signed proof that
     * this key was created inside a TEE/StrongBox on a real Android device running the
     * legitimate osu!droid APK.
     *
     * Attempts StrongBox first (API 28+); falls back silently to TEE if StrongBox is
     * unavailable (e.g. most mid-range devices).
     *
     * Any existing key under [KEY_ALIAS] is deleted first so each login produces a fresh,
     * challenge-bound attestation.
     *
     * @param challenge  Raw bytes of the server-issued nonce (16–32 bytes recommended).
     * @throws Exception if key generation fails on both StrongBox and TEE.
     */
    @Throws(Exception::class)
    fun generateKeyPair(challenge: ByteArray) {
        // Remove any stale key so we always have a fresh, challenge-bound pair.
        deleteKey()

        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAttestationChallenge(challenge)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Try StrongBox first — strongest hardware guarantee.
            try {
                val strongBoxSpec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setAttestationChallenge(challenge)
                    .setIsStrongBoxBacked(true)
                    .build()

                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE_PROVIDER)
                    .apply { initialize(strongBoxSpec) }
                    .generateKeyPair()

                AttestationState.keyGeneratedAt = System.currentTimeMillis()
                Log.i(TAG, "Key generated in StrongBox.")
                return
            } catch (e: StrongBoxUnavailableException) {
                Log.w(TAG, "StrongBox unavailable, falling back to TEE: ${e.message}")
            }
        }

        // Fall back to TEE-backed key generation.
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE_PROVIDER)
            .apply { initialize(spec) }
            .generateKeyPair()

        AttestationState.keyGeneratedAt = System.currentTimeMillis()
        Log.i(TAG, "Key generated in TEE.")
    }

    /**
     * Retrieves the certificate chain for the current attestation key and encodes it as a
     * single Base64 string where each certificate is PEM-encoded and concatenated.
     *
     * The chain typically contains 3–4 certificates:
     *   [0] Leaf (contains attestation extension with challenge, packageName, signatureDigests)
     *   [1] Intermediate (Google batch attestation key or intermediate CA)
     *   [2] Google Hardware Attestation Root CA
     *
     * The server must validate this chain against the Google Root CA and parse the leaf's
     * attestation extension (OID 1.3.6.1.4.1.11129.2.1.17) to verify:
     *   - attestationChallenge matches the nonce it issued
     *   - packageName == "ru.nsu.ccfit.zuev.osuplus.tournament"
     *   - signatureDigests contains the SHA-256 of the legitimate signing certificate
     *   - deviceLocked == true (bootloader locked — strongly recommended to check)
     *
     * @return PEM chain string, or null if the key does not exist yet.
     */
    fun getAttestationChain(): String? {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).also { it.load(null) }
        val chain: Array<Certificate> = keyStore.getCertificateChain(KEY_ALIAS) ?: return null

        return buildString {
            for (cert in chain) {
                append("-----BEGIN CERTIFICATE-----\n")
                append(Base64.encodeToString(cert.encoded, Base64.NO_WRAP))
                append("\n-----END CERTIFICATE-----\n")
            }
        }
    }

    /**
     * Signs [data] with the hardware-backed private key using SHA256withECDSA and returns the
     * signature as a Base64 string (NO_WRAP).
     *
     * This is used on score submission: the server verifies the signature against the public key
     * it extracted from the attestation chain during login, proving the submission comes from
     * the same hardware-attested device that authenticated.
     *
     * @param data  The bytes to sign (e.g. scoreData + beatmapHash + userID concatenated).
     * @return Base64-encoded DER signature, or null if the key does not exist.
     * @throws Exception if signing fails.
     */
    @Throws(Exception::class)
    fun signData(data: ByteArray): String? {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).also { it.load(null) }
        val privateKey = keyStore.getKey(KEY_ALIAS, null) ?: run {
            Log.e(TAG, "signData: private key not found in KeyStore.")
            return null
        }

        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey as java.security.PrivateKey)
        signature.update(data)
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
    }

    /**
     * Returns true if a key currently exists under [KEY_ALIAS] in the AndroidKeyStore.
     */
    fun hasKey(): Boolean {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).also { it.load(null) }
        return keyStore.containsAlias(KEY_ALIAS)
    }

    /**
     * Deletes the key entry under [KEY_ALIAS] from the AndroidKeyStore, if it exists.
     * Called automatically before [generateKeyPair] to ensure a clean state.
     */
    fun deleteKey() {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).also { it.load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
                Log.d(TAG, "Old attestation key deleted.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete old key: ${e.message}")
        }
    }
}

