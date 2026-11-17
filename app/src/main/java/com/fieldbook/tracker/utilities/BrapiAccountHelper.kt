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
        // BrAPIService builds request URLs and BrapiListFilterActivity re-authorizes from these
        // mirrors, so they have to follow whichever account is active.
        oidcUrl = PreferenceKeys.BRAPI_OIDC_URL,
        oidcFlow = PreferenceKeys.BRAPI_OIDC_FLOW,
        oidcClientId = PreferenceKeys.BRAPI_OIDC_CLIENT_ID,
        oidcScope = PreferenceKeys.BRAPI_OIDC_SCOPE,
        brapiVersion = PreferenceKeys.BRAPI_VERSION,
    ),
)
