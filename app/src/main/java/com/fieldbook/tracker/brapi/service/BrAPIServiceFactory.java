package com.fieldbook.tracker.brapi.service;

import android.content.Context;

import com.fieldbook.tracker.preferences.GeneralKeys;

public class BrAPIServiceFactory {

    public static BrAPIService getBrAPIService(Context context){

        String version = context.getSharedPreferences("Settings", 0)
                .getString(GeneralKeys.BRAPI_VERSION, "V1");
        BrAPIService brAPIService;
        if(version.equals("V2"))
            brAPIService = new BrAPIServiceV2(context);
        else
            brAPIService = new BrAPIServiceV1(context);
        brAPIService.authorizeClient();

        return brAPIService;
    }
}
