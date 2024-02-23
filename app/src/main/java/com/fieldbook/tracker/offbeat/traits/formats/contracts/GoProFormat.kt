package com.fieldbook.tracker.offbeat.traits.formats.contracts

import android.provider.ContactsContract.Contacts.Photo
import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormat
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.NameParameter

class GoProFormat : PhotoFormat(
    format = Formats.GO_PRO,
    defaultLayoutId = R.layout.trait_go_pro,
    layoutView = null,
    nameStringResourceId = R.string.traits_format_go_pro_camera,
    iconDrawableResourceId = R.drawable.ic_trait_gopro,
    stringNameAux = null
)