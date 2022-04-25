package ru.nsu.ccfit.zuev.osu.online;

import com.dgsrz.bancho.security.SecurityUtils;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;

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
