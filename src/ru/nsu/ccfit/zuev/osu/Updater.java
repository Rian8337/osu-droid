package ru.nsu.ccfit.zuev.osu;

import android.os.Build;

import com.edlplan.ui.fragment.LoadingFragment;
import com.edlplan.ui.fragment.UpdateDialogFragment;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;

import org.anddev.andengine.util.Debug;

import java.io.IOException;

import okhttp3.Request;
import okhttp3.ResponseBody;
import ru.nsu.ccfit.zuev.osu.async.AsyncTask;
import ru.nsu.ccfit.zuev.osu.helper.StringTable;
import ru.nsu.ccfit.zuev.osu.model.vo.UpdateVO;
import ru.nsu.ccfit.zuev.osu.online.OnlineManager;
import ru.nsu.ccfit.zuev.osuplus.R;

/**
 * @author kairusds
 */

public class Updater {

    private boolean newUpdate = false;
    private String changelogMsg, downloadUrl;
    private LoadingFragment loadingFragment;
    private final MainActivity mActivity;

    private static final Updater instance = new Updater();

    private Updater() {
        mActivity = GlobalManager.getInstance().getMainActivity();
    }

    public static Updater getInstance() {
        return instance;
    }

    private ResponseBody httpGet(String url) throws IOException {
        Request request = new Request.Builder()
            .url(url)
            .build();
        return OnlineManager.client.newCall(request).execute().body();
    }

    public void checkForUpdates(boolean showLoading, boolean showSnackbar) {
        new AsyncTask() {
            @Override
            public void run() {
                mActivity.runOnUiThread(() -> {
                    if (showSnackbar) {
                        Snackbar.make(mActivity.findViewById(android.R.id.content),
                                StringTable.get(R.string.update_info_checking), 1500).show();
                    } else {
                        GlobalManager.getInstance().setInfo(StringTable.get(R.string.update_info_checking));
                    }

                    if (showLoading && loadingFragment == null) {
                        loadingFragment = new LoadingFragment();
                        loadingFragment.show();
                    }
                });

                String lang;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    lang = mActivity.getResources().getConfiguration().getLocales().get(0).getLanguage();
                } else {
                    lang = mActivity.getResources().getConfiguration().locale.getLanguage();
                }

                try (ResponseBody response = httpGet(OnlineManager.endpoint + "update.php?lang=" + lang)) {
                    UpdateVO updateInfo = new Gson().fromJson(response.string(), UpdateVO.class);
                    if (!newUpdate && updateInfo != null && updateInfo.getVersionCode() > mActivity.getVersionCode()) {
                        changelogMsg = updateInfo.getChangelog();
                        downloadUrl = updateInfo.getLink();
                        newUpdate = true;
                    }
                } catch (Exception e) {
                    Debug.e("Updater onRun: " + e.getMessage(), e);
                }
            }

            @Override
            public void onComplete() {
                mActivity.runOnUiThread(() -> {
                    if (showLoading && loadingFragment != null) {
                        loadingFragment.dismiss();
                        loadingFragment = null;
                    }

                    if (newUpdate) {
                        new UpdateDialogFragment()
                                .setChangelogMessage(changelogMsg)
                                .setDownloadUrl(downloadUrl)
                                .show();
                     } else {
                        if (showSnackbar) {
                            Snackbar.make(mActivity.findViewById(android.R.id.content),
                                    StringTable.get(R.string.update_info_latest), 1500).show();
                        } else {
                            GlobalManager.getInstance().setInfo(StringTable.get(R.string.update_info_latest));
                        }
                    }
                });
            }
        }.execute();
    }
}
