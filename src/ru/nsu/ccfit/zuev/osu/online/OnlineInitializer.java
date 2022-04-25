package ru.nsu.ccfit.zuev.osu.online;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import ru.nsu.ccfit.zuev.osu.Config;
import ru.nsu.ccfit.zuev.osu.ToastLogger;
import ru.nsu.ccfit.zuev.osu.async.AsyncTaskLoader;
import ru.nsu.ccfit.zuev.osu.async.OsuAsyncCallback;
import ru.nsu.ccfit.zuev.osu.helper.StringTable;
import ru.nsu.ccfit.zuev.osu.online.OnlineManager.OnlineManagerException;
import ru.nsu.ccfit.zuev.osuplus.R;

public class OnlineInitializer implements View.OnClickListener {
    private Activity activity;
    private Dialog registerDialog;

    public OnlineInitializer(Activity context) {
        this.activity = context;
    }

    public void createInitDialog() {
        registerDialog = new Dialog(activity);
        registerDialog.setContentView(R.layout.register_dialog);
        registerDialog.setTitle(StringTable.get(R.string.online_registration));

        Button btn = (Button) registerDialog.findViewById(R.id.register_btn);
        if (btn != null) btn.setOnClickListener(this);
        btn = (Button) registerDialog.findViewById(R.id.cancel_btn);
        if (btn != null) btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                registerDialog.dismiss();
            }
        });

        registerDialog.show();
    }


    public void onClick(View v) {
        ToastLogger.showText("Registration is only supported in the official client", true);
    }
}
