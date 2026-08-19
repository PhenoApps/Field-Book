package com.fieldbook.tracker.brapi.service;

import android.accounts.Account;
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

        // Read BrAPI version from the active account's config (fallback to prefs)
        String version = getActiveAccountVersion(context, accountHelper, prefs);

        BrAPIService brAPIService;
        if (version.equals("V2"))
            brAPIService = new BrAPIServiceV2(context);
        else
            brAPIService = new BrAPIServiceV1(context);
        brAPIService.authorizeClient();

        return brAPIService;
    }

    /**
     * Returns the BrAPI version for the active account.
     * Reads the account's config first, falls back to SharedPreferences.
     */
    public static String getActiveAccountVersion(
            Context context,
            BrapiAccountHelper accountHelper,
            SharedPreferences prefs
    ) {
        Account active = accountHelper.findAccount();
        if (active != null) {
            String version = accountHelper.accountInfoOrEmpty(active).getBrapiVersion();
            if (!version.isEmpty()) return version;
        }
        return prefs.getString(PreferenceKeys.BRAPI_VERSION, "V1");
    }
}
