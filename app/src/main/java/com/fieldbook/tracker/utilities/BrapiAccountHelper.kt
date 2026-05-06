package com.fieldbook.tracker.utilities

import android.content.Context
import android.content.SharedPreferences
import com.fieldbook.tracker.preferences.PreferenceKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import org.phenoapps.brapi.account.BrapiAccountRepository
import org.phenoapps.brapi.account.BrapiPreferenceKeys
import javax.inject.Inject

class BrapiAccountHelper @Inject constructor(
    @param:ApplicationContext context: Context,
    preferences: SharedPreferences,
) : BrapiAccountRepository(
    context = context,
    preferences = preferences,
    preferenceKeys = BrapiPreferenceKeys(
        enabled = PreferenceKeys.BRAPI_ENABLED,
        baseUrl = PreferenceKeys.BRAPI_BASE_URL,
        displayName = PreferenceKeys.BRAPI_DISPLAY_NAME,
        accessToken = PreferenceKeys.BRAPI_TOKEN,
        idToken = PreferenceKeys.BRAPI_ID_TOKEN,
    ),
)
