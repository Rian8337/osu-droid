package ru.nsu.ccfit.zuev.osu.security

/**
 * Holds attestation data for the current login session.
 *
 * This is populated once during login:
 *  1. The server issues a challenge nonce via the challenge endpoint.
 *  2. [HardwareAttestationManager.generateKeyPair] creates a hardware-backed key using the nonce.
 *  3. [attestationChain] is fetched and sent to the server with the login request.
 *  4. On successful login, [sessionAttestationReady] is set to true, meaning the private key
 *     is ready to sign score submissions for the duration of this session.
 *
 * The chain is only sent once (at login). Subsequent score submissions only send a
 * [HardwareAttestationManager.signData] signature, keeping the payload small.
 *
 * Key TTL: A generated key pair is considered valid for [KEY_TTL_MS] (15 minutes). If the player
 * logs in again within that window, the existing key and chain are reused — no new key generation
 * or challenge fetch is needed. After expiry, the next login triggers a full re-attestation.
 */
object AttestationState {

    /** Minimum lifetime of a generated key pair before a new one is required on re-login. */
    const val KEY_TTL_MS: Long = 15 * 60 * 1000L // 15 minutes

    /** The raw challenge bytes received from the server. Cleared after key generation. */
    @Volatile
    var pendingChallenge: ByteArray? = null

    /**
     * The PEM-encoded certificate chain retrieved after key generation.
     * Sent to the server once during login. Cached here to avoid re-reading KeyStore.
     */
    @Volatile
    var attestationChain: String? = null

    /**
     * True once the attestation key pair has been generated and the chain successfully
     * submitted to and accepted by the server during login. Score submissions use this
     * flag to decide whether to include an attestation signature.
     */
    @Volatile
    var sessionAttestationReady: Boolean = false

    /**
     * Wall-clock time (from [System.currentTimeMillis]) at which the current key pair was
     * generated. Null if no key has been generated in this process lifetime.
     */
    @Volatile
    var keyGeneratedAt: Long? = null

    /**
     * Returns true if the current key is still within the [KEY_TTL_MS] validity window,
     * meaning the existing attestation chain can be reused on re-login without generating
     * a new key pair or fetching a new challenge.
     */
    fun isKeyStillValid(): Boolean {
        val generatedAt = keyGeneratedAt ?: return false
        return (System.currentTimeMillis() - generatedAt) < KEY_TTL_MS
    }

    /**
     * Clears all session attestation state. Called on logout or login failure to ensure
     * stale attestation data is never reused across sessions.
     */
    fun clear() {
        pendingChallenge = null
        attestationChain = null
        sessionAttestationReady = false
        keyGeneratedAt = null
        HardwareAttestationManager.deleteKey()
    }
}


