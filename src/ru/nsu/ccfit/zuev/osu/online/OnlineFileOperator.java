package ru.nsu.ccfit.zuev.osu.online;

import com.dgsrz.bancho.security.SecurityUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URLEncoder;
import java.util.ArrayList;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Response;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.BufferedSink;
import okio.Okio;

import org.anddev.andengine.util.Debug;
import ru.nsu.ccfit.zuev.osu.helper.FileUtils;

public class OnlineFileOperator {
    public static ArrayList<String> sendScore(String url, String data, String replayFileName, String mapMD5) {
        try {
            File file = new File(replayFileName);
            if (!file.exists()) {
                Debug.i(replayFileName + " does not exist.");
                return null;
            }

            MediaType mime = MediaType.parse("application/octet-stream");
            RequestBody fileBody = RequestBody.create(mime, file);
            RequestBody requestBody = new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("uploadedFile", file.getName(), fileBody)
                    .addFormDataPart("userID", OnlineManager.getInstance().getUserId())
                    .addFormDataPart("data", data)
                    .addFormDataPart("hash", mapMD5)
                    .addFormDataPart("sessionId", OnlineManager.getInstance().getSessionId())
                    .build();
            Request request = new Request.Builder().url(url)
                    .post(requestBody).build();
            Response response = OnlineManager.client.newCall(request).execute();

            ArrayList<String> responseList = new ArrayList<>();
            String line;
            BufferedReader reader = new BufferedReader(new StringReader(response.body().string()));
            while((line = reader.readLine()) != null) {
                Debug.i(String.format("request [%d]: %s", responseList.size(), line));
                responseList.add(line);
            }

            return responseList;
        } catch (final IOException e) {
            Debug.e("sendFile IOException " + e.getMessage(), e);
        } catch (final Exception e) {
            Debug.e("sendFile Exception " + e.getMessage(), e);
        }

        return null;
    }

    public static boolean downloadFile(String urlstr, String filename) {
        Debug.i("Starting download " + urlstr);
        File file = new File(filename);
        try {
            if(file.exists()) {
                Debug.i(file.getName() + " already exists");
                return true;
            }
            // Cheching for errors
            Debug.i("Connected to " + urlstr);

            Request request = new Request.Builder()
                .url(urlstr)
                .build();
            Response response = OnlineManager.client.newCall(request).execute();
            BufferedSink sink = Okio.buffer(Okio.sink(file));
            sink.writeAll(response.body().source());
            response.close();
            sink.close();
            return true;
        } catch (final IOException e) {
            Debug.e("downloadFile IOException " + e.getMessage(), e);
            return false;
        } catch (final Exception e) {
            Debug.e("downloadFile Exception " + e.getMessage(), e);
            return false;
        }
    }
}
