package com.fieldbook.tracker.offbeat.traits.formats.contracts

import android.provider.ContactsContract.Contacts.Photo
import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormat
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.NameParameter

class CanonFormat : PhotoFormat(
    format = Formats.CANON,
    defaultLayoutId = R.layout.trait_canon,
    layoutView = null,
    nameStringResourceId = R.string.traits_format_canon,
    iconDrawableResourceId = R.drawable.camera_24px,
    stringNameAux = null)