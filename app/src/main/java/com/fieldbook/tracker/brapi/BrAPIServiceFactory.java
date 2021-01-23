package com.fieldbook.tracker.brapi;

import android.content.Context;

import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.utilities.Constants;

public class BrAPIServiceFactory {

    public static final BrAPIService getBrAPIService(Context context){

        String brapiBaseURL = context.getSharedPreferences("Settings", 0)
                .getString(GeneralKeys.BRAPI_BASE_URL, "") + Constants.BRAPI_PATH;
        BrAPIService brAPIService = new BrAPIServiceV1(brapiBaseURL, new DataHelper(context));
        return brAPIService;
    }
}
