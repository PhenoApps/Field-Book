package com.fieldbook.tracker.offbeat.traits.formats.contracts

import android.content.Context
import android.view.View
import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.offbeat.traits.formats.TraitFormat
import com.fieldbook.tracker.offbeat.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.offbeat.traits.formats.parameters.NameParameter

open class BasePhotoFormat(
    override var format: Formats = Formats.CAMERA,
    override var defaultLayoutId: Int = R.layout.trait_photo,
    override var layoutView: View? = null,
    override var nameStringResourceId: Int = R.string.traits_format_photo,
    override var iconDrawableResourceId: Int = R.drawable.ic_trait_camera,
    override var stringNameAux: ((Context) -> String?)? = null
) : TraitFormat(
    format = format,
    defaultLayoutId = defaultLayoutId,
    layoutView = layoutView,
    nameStringResourceId = nameStringResourceId,
    iconDrawableResourceId = iconDrawableResourceId,
    stringNameAux = stringNameAux,
    NameParameter(),
    DetailsParameter(),
)