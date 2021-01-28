package com.fieldbook.tracker.brapi;

import android.content.Context;

import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.utilities.Constants;

public class BrAPIServiceFactory {

    public static final BrAPIService getBrAPIService(Context context){

        String version = context.getSharedPreferences("Settings", 0)
                .getString(GeneralKeys.BRAPI_VERSION, "V1");
        BrAPIService brAPIService;
        if(version.equals("V2"))
            brAPIService = new BrAPIServiceV2(context);
        else
            brAPIService = new BrAPIServiceV1(context);

        return brAPIService;
    }
}
