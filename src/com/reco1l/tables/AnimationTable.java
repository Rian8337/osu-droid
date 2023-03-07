package com.reco1l.tables;

import android.view.View;
import android.widget.TextView;

import com.reco1l.framework.Animation;

@Deprecated
public class AnimationTable {

    public static void textChange(TextView view, String newText) {
        Animation.of(view)
                .toAlpha(0)
                .runOnEnd(() -> {
                    view.setText(newText);

                    Animation.of(view)
                            .toAlpha(1)
                            .play(150);
                })
                .play(150);
    }

    public static void fadeOutIn(View view, Runnable onStart) {
        Animation.of(view)
                .toAlpha(0)
                .runOnEnd(() -> Animation.of(view)
                        .toAlpha(1)
                        .runOnStart(onStart)
                        .play(150))
                .play(150);
    }

    public static void fadeOutScaleOut(View view) {
        Animation.of(view)
                .toAlpha(0)
                .toScale(0.8f)
                .play(200);
    }
}
