package com.fieldbook.tracker.offbeat.traits.formats.contracts

import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormat
import com.fieldbook.tracker.preferences.GeneralKeys

class BrapiFormat : TraitFormat(
    format = Formats.BRAPI,
    defaultLayoutId = -1,
    layoutView = null,
    databaseName = "brapi",
    nameStringResourceId = R.string.brapi_display_name,
    iconDrawableResourceId = R.drawable.ic_adv_brapi,
    stringNameAux = { context ->
        PreferenceManager.getDefaultSharedPreferences(context)
            .getString(
                GeneralKeys.BRAPI_DISPLAY_NAME,
                context.getString(R.string.brapi_edit_display_name_default)
            )
    }
)