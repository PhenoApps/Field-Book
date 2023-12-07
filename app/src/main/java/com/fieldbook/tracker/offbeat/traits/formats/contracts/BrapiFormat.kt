package com.fieldbook.tracker.offbeat.traits.formats.contracts

import android.content.Context
import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormat
import com.fieldbook.tracker.preferences.GeneralKeys

class BrapiFormat : TraitFormat(
    format = Formats.BRAPI,
    defaultLayoutId = -1,
    layoutView = null,
    nameStringResourceId = R.string.brapi_display_name,
    iconDrawableResourceId = R.drawable.ic_adv_brapi,
    stringNameAux = { context ->
        context.getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE)
            .getString(
                GeneralKeys.BRAPI_DISPLAY_NAME,
                context.getString(R.string.preferences_brapi_server_test)
            )
    }
)