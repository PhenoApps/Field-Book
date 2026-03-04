package com.fieldbook.tracker.brapi.service;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.preferences.PreferenceKeys;
import com.fieldbook.tracker.utilities.BrapiAccountHelper;

public class BrAPIServiceFactory {

    public static BrAPIService getBrAPIService(Context context){

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        BrapiAccountHelper accountHelper = new BrapiAccountHelper(context, prefs);
        accountHelper.migrateFromPrefsIfNeeded();

        String version = prefs.getString(PreferenceKeys.BRAPI_VERSION, "V1");
        BrAPIService brAPIService;
        if(version.equals("V2"))
            brAPIService = new BrAPIServiceV2(context);
        else
            brAPIService = new BrAPIServiceV1(context);
        brAPIService.authorizeClient();

        return brAPIService;
    }
}
