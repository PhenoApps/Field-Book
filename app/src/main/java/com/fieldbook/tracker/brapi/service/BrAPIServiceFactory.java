package com.fieldbook.tracker.brapi.service;

import android.content.Context;

import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.preferences.PreferenceKeys;

public class BrAPIServiceFactory {

    public static BrAPIService getBrAPIService(Context context){

        String version = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PreferenceKeys.BRAPI_VERSION, "V1");
        BrAPIService brAPIService;
        if(version.equals("V2"))
            brAPIService = new BrAPIServiceV2(context);
        else
            brAPIService = new BrAPIServiceV1(context);
        brAPIService.authorizeClient();

        return brAPIService;
    }
}
