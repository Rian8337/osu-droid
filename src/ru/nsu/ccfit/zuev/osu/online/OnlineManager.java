package ru.nsu.ccfit.zuev.osu.online;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.util.Base64;

import okhttp3.OkHttpClient;

import org.anddev.andengine.util.Debug;

import java.io.File;
import java.util.ArrayList;

import ru.nsu.ccfit.zuev.osu.Config;
import ru.nsu.ccfit.zuev.osu.ResourceManager;
import ru.nsu.ccfit.zuev.osu.helper.MD5Calcuator;
import ru.nsu.ccfit.zuev.osu.online.PostBuilder.RequestException;
import ru.nsu.ccfit.zuev.osu.scoring.StatisticV2;

public class OnlineManager {
    public static final String hostname = "droidpp.osudroid.moe";
    public static final String endpoint = "https://" + hostname + "/api/droid/";
    private static final String onlineVersion = "29";

    public static final OkHttpClient client = new OkHttpClient();

    private static OnlineManager instance = null;
    private String failMessage = "";

    private boolean stayOnline = true;
    private String userId = "";
    private String sessionId = "";
    private String username = "";
    private String password = "";
    private String deviceID = "";
    private long rank = 0;
    private long score = 0;
    private float accuracy = 0;
    private String avatarURL = "";
    private int mapRank;

    public static OnlineManager getInstance() {
        if (instance == null) {
            instance = new OnlineManager();
        }
        return instance;
    }

    public static String getReplayURL(int playID) {
        return endpoint + "upload/" + playID + ".odr";
    }

    public void Init(Context context) {
        this.stayOnline = Config.isStayOnline();
        this.username = Config.getOnlineUsername();
        this.password = Config.getOnlinePassword();
        this.deviceID = Config.getOnlineDeviceID();
    }

    private ArrayList<String> sendRequest(PostBuilder post, String url) throws OnlineManagerException {
        post.addParam("sessionId", sessionId);

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

    public synchronized boolean logIn(String username, String password) throws OnlineManagerException {
        this.username = username;
        this.password = password;

        PostBuilder post = new PostBuilder();
        post.addParam("username", username);
        post.addParam("password", MD5Calcuator.getStringMD5(password + "taikotaiko"));
        post.addParam("version", onlineVersion);

        ArrayList<String> response = sendRequest(post, endpoint + "login");

        if (response == null) {
            return false;
        }
        if (response.size() < 2) {
            failMessage = "Invalid server response";
            return false;
        }

        String[] params = response.get(1).split("\\s+");
        if (params.length < 5) {
            failMessage = "Invalid server response";
            return false;
        }
        userId = params[0];
        sessionId = params[1];
        rank = Integer.parseInt(params[2]);
        score = Long.parseLong(params[3]);
        accuracy = Integer.parseInt(params[4]) / 100000f;
        this.username = params[5];
        if (params.length >= 7) {
            avatarURL = params[6];
        } else {
            avatarURL = "";
        }

        return true;
    }

    public boolean sendRecord(String data, String mapMD5) throws OnlineManagerException {
        Debug.i("Sending record...");

        PostBuilder post = new PostBuilder();
        post.addParam("userID", userId);
        post.addParam("data", data);
        post.addParam("hash", mapMD5);

        ArrayList<String> response = sendRequest(post, endpoint + "submit");

        if (response == null) {
            return false;
        }

        if (failMessage.equals("Invalid record data") || response.size() < 2)
            return false;

        String[] resp = response.get(1).split("\\s+");
        if (resp.length < 4) {
            failMessage = "Invalid server response";
            return false;
        }

        rank = Integer.parseInt(resp[0]);
        score = Long.parseLong(resp[1]);
        accuracy = Integer.parseInt(resp[2]) / 100000f;
        mapRank = Integer.parseInt(resp[3]);

        return true;
    }

    public String sendSpectatorData(byte[] data) throws OnlineManagerException {
        System.out.println("Data length: " + data.length);

        PostBuilder post = new PostBuilder();
        post.addParam("userID", userId);
        post.addParam("data", Base64.encodeToString(data, Base64.URL_SAFE));

        ArrayList<String> response = sendRequest(post, endpoint + "spectatorData");

        if (response == null || response.size() == 0) {
            return "FAILED";
        }

        return response.get(0);
    }

    public ArrayList<String> sendPlaySettings(StatisticV2 stat, final String hash) throws OnlineManagerException {
        PostBuilder post = new PostBuilder();
        post.addParam("userID", userId);
        post.addParam("modstring", stat.getModString());
        post.addParam("hash", hash);

        return sendRequest(post, endpoint + "spectatorPlayerSettings");
    }

    public ArrayList<String> getTop(final File trackFile, final String hash) throws OnlineManagerException {
        PostBuilder post = new PostBuilder();
        post.addParam("filename", trackFile.getName());
        post.addParam("hash", hash);

        return new ArrayList<>();
    }

    public boolean loadAvatarToTextureManager() {
        return loadAvatarToTextureManager(this.avatarURL, "userAvatar");
    }

    public boolean loadAvatarToTextureManager(String avatarURL, String userName) {
        if (avatarURL == null || avatarURL.length() == 0) return false;

        String filename = MD5Calcuator.getStringMD5(avatarURL + userName);
        Debug.i("Loading avatar from " + avatarURL);
        Debug.i("filename = " + filename);
        File picfile = new File(Config.getCachePath(), filename);

        if(!picfile.exists()) {
            OnlineFileOperator.downloadFile(avatarURL, picfile.getAbsolutePath());
        }else if(picfile.exists() && picfile.length() < 1) {
            picfile.delete();
            OnlineFileOperator.downloadFile(avatarURL, picfile.getAbsolutePath());
        }
        int imageWidth = 0, imageHeight = 0;
        boolean fileAvailable = true;

        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            imageWidth = BitmapFactory.decodeFile(picfile.getPath()).getWidth();
            imageHeight = BitmapFactory.decodeFile(picfile.getPath()).getHeight();
            options.inJustDecodeBounds = false;
            options = null;
        } catch (NullPointerException e) {
            fileAvailable = false;
        }
        if (fileAvailable && (imageWidth * imageHeight) > 0) {
            //头像已经缓存好在本地
            ResourceManager.getInstance().loadHighQualityFile(userName, picfile);
            if (ResourceManager.getInstance().getTextureIfLoaded(userName) != null) {
                return true;
            }
        }

        Debug.i("Success!");
        return false;
    }

    public String getScorePack(int playid) throws OnlineManagerException {
        PostBuilder post = new PostBuilder();
        post.addParam("playID", String.valueOf(playid));

        ArrayList<String> response = sendRequest(post, endpoint + "gettop.php");

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

    public float getAccuracy() {
        return accuracy;
    }

    public String getAvatarURL() {
        return avatarURL;
    }

    public String getUsername() {
        return username;
    }

    public String getUserId() {
        return userId;
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

    public int getMapRank() {
        return mapRank;
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
