package com.fieldbook.tracker.preferences;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;

import androidx.preference.EditTextPreference;

public class BetterEditTextPreference extends EditTextPreference {

    public BetterEditTextPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public BetterEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BetterEditTextPreference(Context context) {
        super(context);
    }

    // According to ListPreference implementation
    @Override
    public CharSequence getSummary() {
        String text = getText();
        if (TextUtils.isEmpty(text)) {
            return super.getSummary();
        } else {
            CharSequence summary = super.getSummary();
            if (!TextUtils.isEmpty(summary)) {
                return String.format(summary.toString(), text);
            } else {
                return summary;
            }
        }
    }
}
