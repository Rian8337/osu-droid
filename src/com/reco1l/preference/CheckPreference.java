package com.reco1l.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.preference.CheckBoxPreference;
import androidx.preference.PreferenceViewHolder;

import com.reco1l.framework.Animation;

import com.rimu.R;

public class CheckPreference extends CheckBoxPreference {

    private LinearLayout resetButton;

    private boolean
            defaultValue,
            isResetButtonShown = false;

    //--------------------------------------------------------------------------------------------//

    public CheckPreference(Context context) {
        this(context, null);
    }

    public CheckPreference(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CheckPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public CheckPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        setLayoutResource(R.layout.custom_preference_checkbox);
    }

    //--------------------------------------------------------------------------------------------//

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        resetButton = holder.itemView.findViewById(R.id.pref_reset);

        if (resetButton != null) {
            resetButton.setOnClickListener(v -> setChecked(defaultValue));
        }
        super.onBindViewHolder(holder);
        isResetButtonShown = false;
        handleResetButton(isChecked());
    }

    //--------------------------------------------------------------------------------------------//

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        Object value = super.onGetDefaultValue(a, index);

        if (value != null) {
            defaultValue = (boolean) value;
        }
        return value;
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        if (defaultValue != null) {
            this.defaultValue = (boolean) defaultValue;
        }
        super.onSetInitialValue(defaultValue);
    }

    @Override
    public void setDefaultValue(Object defaultValue) {
        if (defaultValue != null) {
            this.defaultValue = (boolean) defaultValue;
        }
        super.setDefaultValue(defaultValue);
    }

    //--------------------------------------------------------------------------------------------//

    @Override
    public boolean callChangeListener(Object newValue) {
        handleResetButton((boolean) newValue);
        return super.callChangeListener(newValue);
    }

    @Override
    public void setChecked(boolean checked) {
        callChangeListener(checked);
        super.setChecked(checked);
    }

    //--------------------------------------------------------------------------------------------//

    private void handleResetButton(boolean newValue) {
        boolean isDefaultValue = newValue == defaultValue;

        if (!isDefaultValue && !isResetButtonShown) {
            isResetButtonShown = true;
            Animation.of(resetButton)
                    .toAlpha(1)
                    .play(200);
        }
        if (isDefaultValue && isResetButtonShown) {
            isResetButtonShown = false;
            Animation.of(resetButton)
                    .toAlpha(0)
                    .play(200);
        }
    }
}
