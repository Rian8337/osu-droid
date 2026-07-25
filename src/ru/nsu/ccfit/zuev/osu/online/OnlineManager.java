package ru.nsu.ccfit.zuev.osu.online;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.osudroid.data.BeatmapInfo;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;

import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.anddev.andengine.util.Debug;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import android.util.Base64;

import ru.nsu.ccfit.zuev.osu.Config;
import ru.nsu.ccfit.zuev.osu.ResourceManager;
import org.json.JSONException;
import org.json.JSONObject;
import ru.nsu.ccfit.zuev.osu.*;
import ru.nsu.ccfit.zuev.osu.helper.FileUtils;
import ru.nsu.ccfit.zuev.osu.helper.MD5Calculator;
import ru.nsu.ccfit.zuev.osu.online.PostBuilder.RequestException;
import ru.nsu.ccfit.zuev.osu.scoring.StatisticV2;
import ru.nsu.ccfit.zuev.osu.security.AttestationState;
import ru.nsu.ccfit.zuev.osu.security.HardwareAttestationManager;

public class OnlineManager {
    private static final String TAG = "OnlineManager";

    public static final String hostname = "osudroid.kansenindex.dev";
    public static final String endpoint = "https://" + hostname + "/api/droid/";
    public static final String updateEndpoint = endpoint + "update";

    /**
     * Endpoint that issues a one-time challenge nonce for hardware attestation.
     * The server stores the nonce with a short TTL (~60 s) and validates it during login.
     */
    public static final String attestationChallengeEndpoint = endpoint + "getAttestationChallenge";

    private static final String onlineVersion = "49";
    public static final String defaultAvatarURL = getAvatarURL(0);
    public static final String profileBannerEndpoint = "https://" + hostname + "/user/banner/";

    public static final OkHttpClient client = new OkHttpClient();

    private static OnlineManager instance = null;
    private String failMessage = "";

    private boolean stayOnline = true;
    private String sessionId = "";
    private long userId = -1L;

    private String username = "";
    private String password = "";
    private String deviceID = "";
    private long rank = 0;
    private long score = 0;
    private float accuracy = 0;
    private float pp = 0;
    private String avatarURL = "";
    private String profileBannerURL = "";
    private int mapRank;

    public static OnlineManager getInstance() {
        if (instance == null) {
            instance = new OnlineManager();
        }
        return instance;
    }

    public static String getReplayURL(int userID, String hash) {
        return endpoint + "getReplay?userID=" + userID + "&hash=" + hash;
    }

    public static String getAvatarURL(long userId) {
        return "https://" + hostname + "/user/avatar/" + userId + ".png";
    }

    public static String getProfileBannerURL(long userId) {
        return profileBannerEndpoint + userId + ".png";
    }

    public void init() {
        this.stayOnline = Config.isStayOnline();
        this.username = Config.getOnlineUsername();
        this.password = Config.getOnlinePassword();
        this.deviceID = Config.getOnlineDeviceID();
    }

    private ArrayList<String> sendRequest(PostBuilder post, String url) throws OnlineManagerException {
        ArrayList<String> response;
        try {
            response = post.requestWithAttempts(url, 3);
        } catch (RequestException e) {
            Debug.e(e.getMessage(), e);
            failMessage = "Cannot connect to server";
            throw new OnlineManagerException("Cannot connect to server", e);
        }
        failMessage = "";

        //TODO debug code
		/*Debug.i("Received " + response.size() + " lines");
		for(String str: response)
		{
			Debug.i(str);
		}*/

        if (response.size() == 0 || response.get(0).length() == 0) {
            failMessage = "Got empty response";
            Debug.i("Received empty response!");
            return null;
        }

        if (!response.get(0).equals("SUCCESS")) {
            Debug.i("sendRequest response code:  " + response.get(0));
            if (response.size() >= 2) {
                failMessage = response.get(1);
            } else
                failMessage = "Unknown server error";
            Debug.i("Received fail: " + failMessage);
            return null;
        }


        return response;
    }

    public boolean logIn() throws OnlineManagerException {
        return logIn(username, password);
    }

    public boolean logIn(String username) throws OnlineManagerException {
        return logIn(username, password);
    }

    /**
     * Fetches a one-time attestation challenge nonce from the server.
     *
     * The server returns a JSON object: {"challenge": "<base64-encoded nonce>"}
     * The nonce is decoded and stored in AttestationState.pendingChallenge for
     * use by {@link HardwareAttestationManager#generateKeyPair(byte[])}.
     *
     * This is called before login so the key pair is generated with a fresh, server-issued
     * challenge that is cryptographically bound to the resulting attestation chain.
     *
     * Failures here are non-fatal: login will proceed without attestation, and the server
     * can decide whether to accept or reject unattempted attestation based on its policy.
     */
    private void fetchAttestationChallenge() {
        if (!HardwareAttestationManager.INSTANCE.isSupported()) {
            Log.w(TAG, "Hardware attestation not supported on this device.");
            return;
        }
        try {
            Request request = new Request.Builder()
                    .url(attestationChallengeEndpoint)
                    .get()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.w(TAG, "fetchAttestationChallenge: non-success HTTP " + response.code());
                    return;
                }
                JSONObject json = new JSONObject(response.body().string());
                String challengeB64 = json.optString("challenge", "");
                if (challengeB64.isEmpty()) {
                    Log.w(TAG, "fetchAttestationChallenge: empty challenge in response.");
                    return;
                }
                // Decode and store — will be consumed by generateKeyPair() below.
                AttestationState.INSTANCE.setPendingChallenge(
                        Base64.decode(challengeB64, Base64.DEFAULT));
                Log.i(TAG, "Attestation challenge received (" + challengeB64.length() + " b64 chars).");
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchAttestationChallenge failed: " + e.getMessage(), e);
            // Non-fatal — login continues without attestation.
        }
    }

    /**
     * Attempts to generate a hardware-attested key pair using the challenge previously stored by
     * {@link #fetchAttestationChallenge()}.  On success, caches the PEM chain in
     * {@link AttestationState#getAttestationChain()} so it can be sent with the login request.
     *
     * Failures are non-fatal: login will proceed without attestation parameters.
     */
    private void prepareAttestationKeyPair() {
        byte[] challenge = AttestationState.INSTANCE.getPendingChallenge();
        if (challenge == null) {
            return;
        }
        try {
            HardwareAttestationManager.INSTANCE.generateKeyPair(challenge);
            String chain = HardwareAttestationManager.INSTANCE.getAttestationChain();
            AttestationState.INSTANCE.setAttestationChain(chain);
            AttestationState.INSTANCE.setPendingChallenge(null); // consumed
            Log.i(TAG, "Attestation key pair ready. Chain length: "
                    + (chain != null ? chain.length() : 0) + " chars.");
        } catch (Exception e) {
            Log.e(TAG, "prepareAttestationKeyPair failed: " + e.getMessage(), e);
            AttestationState.INSTANCE.setPendingChallenge(null);
        }
    }

    /**
     * Builds and executes the raw login POST, returning the OkHttp {@link Response}.
     *
     * <p>The caller is responsible for closing the response body. Unlike {@link #sendRequest},
     * this method returns the raw response regardless of HTTP status code so that the caller
     * can inspect JSON error bodies (e.g. {@code 401 CHALLENGE_EXPIRED}) before deciding
     * whether to retry.
     *
     * @param chain PEM attestation chain to include, or {@code null} to omit.
     * @return the raw HTTP response.
     * @throws OnlineManagerException if the network call itself fails.
     */
    private Response sendLoginRequest(String chain) throws OnlineManagerException {
        String hashedPassword = MD5Calculator.getStringMD5(
                escapeHTMLSpecialCharacters(addSlashes(String.valueOf(password).trim())) + "taikotaiko"
        );

        // Build the values string for HMAC signing — mirrors exactly what PostBuilder does:
        // each URL-encoded value joined by "_" in the order the params are added.
        StringBuilder signValues = new StringBuilder();
        try {
            signValues.append(java.net.URLEncoder.encode(username, "UTF-8"));
            signValues.append("_").append(java.net.URLEncoder.encode(hashedPassword, "UTF-8"));
            signValues.append("_").append(java.net.URLEncoder.encode(onlineVersion, "UTF-8"));
            if (chain != null) {
                signValues.append("_").append(java.net.URLEncoder.encode(chain, "UTF-8"));
            }
        } catch (java.io.UnsupportedEncodingException ignored) {}

        okhttp3.FormBody.Builder formBuilder = new okhttp3.FormBody.Builder();
        formBuilder.add("username", username);
        formBuilder.add("password", hashedPassword);
        formBuilder.add("version", onlineVersion);
        if (chain != null) {
            formBuilder.add("attestationChain", chain);
            Log.i(TAG, "sendLoginRequest: attaching attestation chain (" + chain.length() + " chars).");
        }

        // Append the HMAC sign param last, same as PostBuilder.requestWithAttempts().
        String sign = SecurityUtils.signRequest(signValues.toString());
        if (sign != null) {
            formBuilder.add("sign", sign);
        }

        Request request = new Request.Builder()
                .url(endpoint + "login")
                .post(formBuilder.build())
                .build();

        try {
            return client.newCall(request).execute();
        } catch (IOException e) {
            failMessage = "Cannot connect to server";
            throw new OnlineManagerException("Cannot connect to server", e);
        }
    }

    public synchronized boolean logIn(String username, String password) throws OnlineManagerException {
        this.username = username;
        this.password = password;

        // --- Hardware Attestation: Step 1 — get challenge & generate key pair ---
        // If a key was generated less than KEY_TTL_MS (15 min) ago, reuse it — no new
        // challenge fetch or key generation is needed. This avoids the overhead of key
        // generation on rapid re-logins (e.g. screen-off/on or brief session expiry).
        // After the TTL expires, clear and do a full re-attestation.
        boolean reusingExistingKey = AttestationState.INSTANCE.isKeyStillValid()
                && AttestationState.INSTANCE.getAttestationChain() != null;
        if (!reusingExistingKey) {
            AttestationState.INSTANCE.clear();
            fetchAttestationChallenge();
            prepareAttestationKeyPair();
        } else {
            Log.i(TAG, "Reusing existing attestation key (within 15-min TTL).");
        }
        // -----------------------------------------------------------------------

        String chain = AttestationState.INSTANCE.getAttestationChain();

        // --- Hardware Attestation: Step 2 — send login with attestation chain ---
        // We use a raw OkHttp call instead of sendRequest() so we can inspect the HTTP
        // status and JSON body directly. This lets us detect 401 CHALLENGE_EXPIRED (which
        // happens when the server restarted and discarded its in-memory challenge store)
        // and transparently re-attest before retrying — without surfacing the error to the
        // player at all.
        ArrayList<String> response = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try (Response httpResponse = sendLoginRequest(chain)) {
                int httpCode = httpResponse.code();
                Log.i(TAG, "logIn attempt " + (attempt + 1) + ": HTTP " + httpCode);

                if (httpResponse.body() == null) {
                    failMessage = "Server error: HTTP " + httpCode;
                    return false;
                }

                String bodyStr = httpResponse.body().string();
                Log.d(TAG, "logIn response body: " + bodyStr);
                boolean isChallengeExpired = false;

                if (httpCode == 401) {
                    // New protocol: server returns HTTP 401 + JSON { "code": "CHALLENGE_EXPIRED" }.
                    try {
                        JSONObject json = new JSONObject(bodyStr);
                        String code = json.optString("code");
                        Log.w(TAG, "logIn 401 — code=" + code);
                        isChallengeExpired = "CHALLENGE_EXPIRED".equals(code);
                    } catch (JSONException e) {
                        Log.e(TAG, "logIn 401 — failed to parse JSON body: " + e.getMessage());
                    }
                } else if (httpResponse.isSuccessful()) {
                    // Legacy / current server protocol: HTTP 200 with body "FAIL\n<message>".
                    // Detect challenge expiry by checking the failure message text so that
                    // re-attestation fires even before the server is updated to return 401+JSON.
                    BufferedReader reader = new BufferedReader(new StringReader(bodyStr));
                    ArrayList<String> lines = new ArrayList<>();
                    String line;
                    while ((line = reader.readLine()) != null) lines.add(line);

                    if (!lines.isEmpty() && "FAIL".equals(lines.get(0))) {
                        String reason = lines.size() >= 2 ? lines.get(1) : "";
                        if (reason.toLowerCase().contains("challenge") &&
                                (reason.toLowerCase().contains("expired") ||
                                 reason.toLowerCase().contains("not issued") ||
                                 reason.toLowerCase().contains("not found"))) {
                            Log.w(TAG, "logIn FAIL — detected challenge expiry in message: " + reason);
                            isChallengeExpired = true;
                        } else {
                            // Normal FAIL (wrong password, etc.) — propagate as-is.
                            failMessage = reason.isEmpty() ? "Unknown server error" : reason;
                            Log.w(TAG, "logIn FAIL — " + failMessage);
                            return false;
                        }
                    } else {
                        // SUCCESS path — capture lines for session parsing below.
                        Log.i(TAG, "logIn SUCCESS on attempt " + (attempt + 1));
                        response = lines;
                        break;
                    }
                } else {
                    failMessage = "Server error: HTTP " + httpCode;
                    Log.e(TAG, "logIn unexpected HTTP " + httpCode + ": " + bodyStr);
                    return false;
                }

                if (isChallengeExpired && attempt == 0) {
                    // The chain we sent has an expired or missing challenge (e.g. server
                    // restarted, or the key was generated before the challenge flow existed).
                    // Force a full re-attestation cycle and retry the login once.
                    Log.w(TAG, "Challenge expired — clearing state and re-attesting for retry...");
                    AttestationState.INSTANCE.clear();
                    fetchAttestationChallenge();
                    prepareAttestationKeyPair();
                    chain = AttestationState.INSTANCE.getAttestationChain();

                    if (chain == null) {
                        // Challenge fetch or key generation failed — no point retrying.
                        failMessage = "Attestation failed (could not obtain new challenge)";
                        Log.e(TAG, "Re-attestation failed: chain is null after retry fetch.");
                        return false;
                    }

                    Log.i(TAG, "Re-attestation complete. New chain length: "
                            + chain.length() + ". Retrying login...");
                    continue;
                }

                // Either not a challenge expiry, or already retried — treat as failure.
                failMessage = "Attestation failed";
                Log.e(TAG, "logIn failed after " + (attempt + 1) + " attempt(s). isChallengeExpired="
                        + isChallengeExpired + " body=" + bodyStr);
                return false;

            } catch (IOException e) {
                failMessage = "Cannot connect to server";
                throw new OnlineManagerException("Cannot connect to server", e);
            }
        }
        // -----------------------------------------------------------------------

        if (response == null || response.size() < 2) {
            failMessage = "Invalid server response";
            return false;
        }

        String[] params = response.get(1).split("\\s+");
        if (params.length < 7) {
            failMessage = "Invalid server response";
            return false;
        }
        userId = Long.parseLong(params[0]);
        sessionId = params[1];
        rank = Integer.parseInt(params[2]);
        score = Long.parseLong(params[3]);
        pp = Float.parseFloat(params[4]);
        accuracy = Float.parseFloat(params[5]);
        this.username = params[6];
        if (params.length >= 8) {
            avatarURL = params[7];
        } else {
            avatarURL = "";
        }
        profileBannerURL = getProfileBannerURL(userId);

        // --- Hardware Attestation: Step 3 — mark session as attested ---
        // If we sent a chain and the server accepted it (login succeeded), the private key is
        // now usable for signing score submissions. The server associates the session ID with
        // the leaf public key extracted from the chain.
        if (chain != null) {
            AttestationState.INSTANCE.setSessionAttestationReady(true);
            Log.i(TAG, "Session attestation active for userId=" + userId);
        }
        // ----------------------------------------------------------------

        return true;
    }

    public boolean sendRecord(BeatmapInfo beatmap, String scoreData, String replayPath) throws OnlineManagerException {
        Debug.i("Sending record...");

        File replayFile = new File(replayPath);
        if (!replayFile.exists()) {
            failMessage = "Replay file not found";
            Debug.e("Replay file not found");
            return false;
        }

        var post = new FormDataPostBuilder();
        post.addParam("userID", String.valueOf(userId));
        post.addParam("sessionId", sessionId);
        post.addParam("hash", beatmap.getMD5());
        post.addParam("data", scoreData);
        post.addParam("version", onlineVersion);
        post.addParam("cs", String.valueOf(beatmap.getCircleSize()));
        post.addParam("ar", String.valueOf(beatmap.getApproachRate()));
        post.addParam("od", String.valueOf(beatmap.getOverallDifficulty()));
        post.addParam("hp", String.valueOf(beatmap.getHpDrainRate()));

        // --- Hardware Attestation: sign the score submission ---
        // The payload signed is "userID|beatmapHash|scoreData" — a canonical string that
        // uniquely identifies this exact submission. The server verifies this signature
        // using the public key it stored when the attestation chain was submitted at login.
        // This prevents score injection: an attacker would need the hardware-backed private
        // key, which never leaves the TEE/StrongBox.
        if (AttestationState.INSTANCE.getSessionAttestationReady()) {
            try {
                String sigPayload = userId + "|" + beatmap.getMD5() + "|" + scoreData;
                String sig = HardwareAttestationManager.INSTANCE.signData(
                        sigPayload.getBytes(StandardCharsets.UTF_8));
                if (sig != null) {
                    post.addParam("attestationSignature", sig);
                    Log.i(TAG, "Attestation signature attached to score submission.");
                }
            } catch (Exception e) {
                // Non-fatal: submission proceeds without the signature.
                // The server may choose to flag or reject unsigned submissions based on policy.
                Log.e(TAG, "Failed to sign score submission: " + e.getMessage(), e);
            }
        }
        // -------------------------------------------------------

        MediaType replayMime = MediaType.parse("application/octet-stream");
        RequestBody replayFileBody = RequestBody.create(replayFile, replayMime);

        post.addParam("replayFile", replayFile.getName(), replayFileBody);
        post.addParam("replayFileChecksum", FileUtils.getSHA256Checksum(replayFile));

        ArrayList<String> response = sendRequest(post, endpoint + "submit");

        if (response == null) {
            return false;
        }

        if (failMessage.equals("Invalid record data"))
            return false;

        if (response.size() < 2) {
            failMessage = "Invalid server response";
            return false;
        }

        String[] resp = response.get(1).split("\\s+");
        if (resp.length < 4) {
            failMessage = "Invalid server response";
            return false;
        }

        rank = Integer.parseInt(resp[0]);
        score = Long.parseLong(resp[1]);
        accuracy = Float.parseFloat(resp[2]);
        pp = Float.parseFloat(resp[3]);

        return true;
    }

    public ArrayList<String> sendPlaySettings(StatisticV2 stat, final String hash) throws OnlineManagerException {
        PostBuilder post = new URLEncodedPostBuilder();
        post.addParam("userID", String.valueOf(userId));
        post.addParam("sessionId", sessionId);
        post.addParam("mods", stat.getMod().serializeMods().toString());
        post.addParam("hash", hash);
        post.addParam("isSliderLock", Config.isRemoveSliderLock() ? "1" : "0");

        return sendRequest(post, endpoint + "verifyPlaySettings");
    }

    public ArrayList<String> getTop(final String hash) throws OnlineManagerException {
        PostBuilder post = new URLEncodedPostBuilder();
        post.addParam("hash", hash);

        ArrayList<String> response = sendRequest(post, endpoint + "getLeaderboard");

        if (response == null) {
            return new ArrayList<>();
        }

        response.remove(0);

        return response;
    }

    public RankedStatus getBeatmapStatus(String md5) throws OnlineManagerException {
        var builder = new Request.Builder().url("https://osu.direct/api/v2/md5/" + md5);
        var request = builder.build();

        try (var response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                var json = new JSONObject(response.body().string());
                return RankedStatus.valueOf(json.optInt("ranked"));
            }
        } catch (final IOException e) {
            Debug.e("getBeatmapStatus IOException " + e.getMessage(), e);
        } catch (final JSONException e) {
            Debug.e("getBeatmapStatus JSONException " + e.getMessage(), e);
        } catch (final IllegalArgumentException e) {
            Debug.e("getBeatmapStatus IllegalArgumentException " + e.getMessage(), e);
        }

        return null;
    }

    public boolean loadAvatarToTextureManager() {
        return loadAvatarToTextureManager(avatarURL);
    }

    public boolean loadProfileBannerToTextureManager() {
        return loadProfileBannerToTextureManager(profileBannerURL);
    }

    public boolean loadAvatarToTextureManager(String avatarURL) {
        if (avatarURL == null || avatarURL.isEmpty()) return false;

        String filename = MD5Calculator.getStringMD5(avatarURL);
        Debug.i("Loading avatar from " + avatarURL);
        Debug.i("filename = " + filename);
        File picfile = new File(Config.getCachePath(), filename);
        OnlineFileOperator.downloadFile(avatarURL, picfile.getAbsolutePath(), true);

        var bitmap = loadFileToBitmap(picfile);
        int imageWidth = 0, imageHeight = 0;

        if (bitmap != null) {
            imageWidth = bitmap.getWidth();
            imageHeight = bitmap.getHeight();
        }

        if (imageWidth * imageHeight > 0) {
            // Avatar has been cached locally
            ResourceManager.getInstance().loadHighQualityFile(filename, picfile);
            if (ResourceManager.getInstance().getAvatarTextureIfLoaded(avatarURL) != null) {
                return true;
            }
        } else {
            // Avatar not found, download the default avatar
            String defaultAvatarFilename = MD5Calculator.getStringMD5(defaultAvatarURL);
            File avatarFile = new File(Config.getCachePath(), defaultAvatarFilename);
            OnlineFileOperator.downloadFile(defaultAvatarURL, avatarFile.getAbsolutePath());

            bitmap = loadFileToBitmap(avatarFile);
            if (bitmap != null) {
                imageWidth = bitmap.getWidth();
                imageHeight = bitmap.getHeight();
            }

            if (imageWidth * imageHeight > 0) {
                //Avatar has been cached locally
                ResourceManager.getInstance().loadHighQualityFile(defaultAvatarFilename, avatarFile);
                if (ResourceManager.getInstance().getAvatarTextureIfLoaded(defaultAvatarURL) != null) {
                    return true;
                }
            }
        }

        Debug.i("Success!");
        return false;
    }

    public boolean loadProfileBannerToTextureManager(String bannerURL) {
        if (bannerURL == null || bannerURL.isEmpty()) return false;

        if (ResourceManager.getInstance().getProfileBannerTextureIfLoaded(bannerURL) != null) {
            return true;
        }

        String filename = MD5Calculator.getStringMD5(bannerURL);
        Debug.i("Loading profile banner from " + bannerURL);
        File bannerFile = new File(Config.getCachePath(), filename);
        OnlineFileOperator.downloadFile(bannerURL, bannerFile.getAbsolutePath(), true);

        var bitmap = loadFileToBitmap(bannerFile);
        int imageWidth = 0, imageHeight = 0;

        if (bitmap != null) {
            imageWidth = bitmap.getWidth();
            imageHeight = bitmap.getHeight();
        }

        if (imageWidth * imageHeight <= 0) {
            return false;
        }

        ResourceManager.getInstance().loadHighQualityFile(filename, bannerFile);
        return ResourceManager.getInstance().getProfileBannerTextureIfLoaded(bannerURL) != null;
    }

    private Bitmap loadFileToBitmap(File file) {
        if (!file.exists()) {
            return null;
        }

        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            return BitmapFactory.decodeFile(file.getPath());
        } catch (NullPointerException e) {
            return null;
        }
    }

    public String getScorePack(int userId, String hash) throws OnlineManagerException {
        PostBuilder post = new URLEncodedPostBuilder();
        post.addParam("userID", String.valueOf(userId));
        post.addParam("hash", hash);

        ArrayList<String> response = sendRequest(post, endpoint + "getScore");

        if (response == null || response.size() < 2) {
            return "";
        }

        return response.get(1);
    }

    public String getFailMessage() {
        return failMessage;
    }

    public long getRank() {
        return rank;
    }

    public long getScore() {
        return score;
    }

    public float getPP() {
        return pp;
    }

    public float getAccuracy() {
        return accuracy;
    }

    public String getAvatarURL() {
        return avatarURL;
    }

    public String getProfileBannerURL() {
        return profileBannerURL;
    }

    public String getUsername() {
        return username;
    }

    public long getUserId() {
        return userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getPassword() {
        return password;
    }

    public String getDeviceID() {
        return deviceID;
    }

    public boolean isStayOnline() {
        return stayOnline;
    }

    public void setStayOnline(boolean stayOnline) {
        this.stayOnline = stayOnline;
    }

    public static class OnlineManagerException extends Exception {
        private static final long serialVersionUID = -5703212596292949401L;

        public OnlineManagerException(final String message, final Throwable cause) {
            super(message, cause);
        }

        public OnlineManagerException(final String message) {
            super(message);
        }
    }

}