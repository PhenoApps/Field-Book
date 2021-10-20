package com.fieldbook.tracker.brapi.service;

import android.content.Context;
import android.util.Log;

import com.fieldbook.tracker.preferences.GeneralKeys;

public abstract class AbstractBrAPIService implements BrAPIService {

    protected Integer getTimeoutValue(Context context) {
        String timeoutString = context.getSharedPreferences("Settings", 0)
                .getString(GeneralKeys.BRAPI_TIMEOUT, "120");

        int timeout = 120;

        try {
            if (timeoutString != null) {
                timeout = Integer.parseInt(timeoutString);
            }
        } catch (NumberFormatException nfe) {
            String message = nfe.getLocalizedMessage();
            if (message != null) {
                Log.d("FieldBookError", nfe.getLocalizedMessage());
            } else {
                Log.d("FieldBookError", "Timeout Preference number format error.");
            }
            nfe.printStackTrace();
        }

        return timeout;
    }
}
