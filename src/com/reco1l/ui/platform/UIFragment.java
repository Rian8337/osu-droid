package com.reco1l.ui.platform;

import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.reco1l.utils.Animation;
import com.reco1l.utils.ClickListener;
import com.reco1l.utils.Res;
import com.reco1l.utils.interfaces.UI;
import com.reco1l.utils.interfaces.IMainClasses;

import ru.nsu.ccfit.zuev.osu.Config;
import ru.nsu.ccfit.zuev.osuplus.R;

// Created by Reco1l on 22/6/22 02:26
// Based on the EdrowsLuo BaseFragment class :)

public abstract class UIFragment extends Fragment implements IMainClasses, UI {

    protected View rootView, rootBackground;
    protected boolean
            isDismissOnBackgroundPress = false,
            isDismissOnBackPress = true;

    public boolean isShowing = false;

    protected int screenWidth = Config.getRES_WIDTH();
    protected int screenHeight = Config.getRES_HEIGHT();

    //--------------------------------------------------------------------------------------------//
    /**
     * Runs once the layout XML is inflated.
     */
    protected abstract void onLoad();

    /**
     * Simplifies the way views are got with the method {@link #find(String)}, every layout XML file have an
     * undefined prefix (you have to define it on every view ID declaration).
     */
    protected abstract String getPrefix();

    protected abstract @LayoutRes int getLayout();
    //--------------------------------------------------------------------------------------------//

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle bundle) {

        rootView = inflater.inflate(getLayout(), container, false);

        // Don't forget to create a View matching root bounds and set its ID to "background" for this feature.
        // You can also set the root view ID as "background".
        rootBackground = find(R.id.background);
        onLoad();
        if (isDismissOnBackgroundPress && rootBackground != null) {
            rootBackground.setClickable(true);

            if (!rootBackground.hasOnClickListeners())
                new ClickListener(rootBackground).onlyOnce(true).touchEffect(false).simple(this::close);
        }
        return rootView;
    }

    //---------------------------------------Management-------------------------------------------//

    /**
     * Dismiss the layout.
     * <p>
     * If you override this method always compare if {@linkplain #isShowing} is <code>true</code> at
     * the start of the method, otherwise any action with a View that is not showing will throw a
     * {@link NullPointerException}.
     * <p>
     * Also don't forget to call <code>super.close()</code> otherwise the layout will not dismiss, if you add
     * animations call it at the end of the animation, otherwise the animation will broke up.
     */
    public void close() {
        if (!isShowing)
            return;
        FragmentPlatform.getInstance().removeFragment(this);
        isShowing = false;
        System.gc();
    }

    public void show() {
        if (isShowing)
            return;
        String tag = this.getClass().getName() + "@" + this.hashCode();
        FragmentPlatform.getInstance().addFragment(this, tag);
        isShowing = true;
        System.gc();
    }

    /**
     * If the layout is showing then dismiss it, otherwise shows it.
     */
    public void altShow() {
        if (isShowing) close();
        else show();
    }

    /**
     * @param onBackgroundPress allows the user dismiss the fragment when the background is pressed.
     *                          <p> default value is: <code>false</code>.
     * <p>
     * @param onBackPress allows the user dismiss the fragment when the back button is pressed.
     *                    <p> default value is: <code>true</code>.
     */
    protected void setDismissMode(boolean onBackgroundPress, boolean onBackPress) {
        isDismissOnBackgroundPress = onBackgroundPress;
        isDismissOnBackPress = onBackPress;
    }

    //--------------------------------------------------------------------------------------------//
    /**
     * Finds a child View of the parent layout from its resource ID.
     * @return the view itself if it exists as child in the layout, otherwise null.
     */
    @SuppressWarnings("unchecked")
    protected <T extends View> T find(@IdRes int id) {
        if (rootView == null || id == 0)
            return null;
        Object object = rootView.findViewById(id);

        return object != null ? (T) object : null;
    }

    /**
     * Finds a child View of the parent layout from its ID name in String format.
     * <p>
     *     Note: if you previously defined the layout prefix with the method {@link #getPrefix()}
     *     you don't need to add the prefix to the ID name.
     * @return the view itself if it exists as child in the layout, otherwise null.
     */
    @SuppressWarnings("unchecked")
    protected <T extends View> T find(String id) {
        if (rootView == null || id == null)
            return null;

        int Id;

        if (getPrefix() == null || id.startsWith(getPrefix())) {
            Id = res().getIdentifier(id, "id", mActivity.getPackageName());
        } else {
            Id = res().getIdentifier(getPrefix() + "_" + id, "id", mActivity.getPackageName());
        }

        Object view = rootView.findViewById(Id);
        return (T) view;
    }

    //--------------------------------------------------------------------------------------------//

    /**
     * Simple method to check nullability of multiples views at once.
     */
    protected boolean isNull(View... views) {
        for (View view: views) {
            if (view == null)
                return true;
        }
        return false;
    }

    /**
     * Set text with a fade animation.
     *
     * <p>
     *     Will be removed.
     * </p>
     */
    @Deprecated
    public void setText(TextView view, String text) {
        if (view == null)
            return;

        final int color = view.getCurrentTextColor();
        final int alpha = Color.alpha(color);
        final int[] rgb = {
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        };

        new Animation(view)
                .ofArgb(color, Color.argb(0, rgb[0], rgb[1], rgb[2]))
                .runOnUpdate(val -> view.setTextColor((int) val.getAnimatedValue()))
                .play(400);

        new Animation(view)
                .ofArgb(view.getCurrentTextColor(), Color.argb(alpha, rgb[0], rgb[1], rgb[2]))
                .runOnUpdate(val -> view.setTextColor((int) val.getAnimatedValue()))
                .runOnStart(() -> view.setText(text))
                .delay(400)
                .play(400);
    }

    //--------------------------------------------------------------------------------------------//
    @Deprecated // Use Res class instead.
    protected Resources res(){
        return mActivity.getResources();
    }
}
