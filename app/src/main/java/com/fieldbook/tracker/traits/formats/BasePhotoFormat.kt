package com.fieldbook.tracker.traits.formats

import android.content.Context
import android.view.View
import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.formats.parameters.CropImageParameter
import com.fieldbook.tracker.traits.formats.parameters.DetailsParameter
import com.fieldbook.tracker.traits.formats.parameters.NameParameter
import com.fieldbook.tracker.traits.formats.presenters.UriPresenter
import com.fieldbook.tracker.traits.formats.presenters.ValuePresenter

open class BasePhotoFormat(
    override var format: Formats = Formats.CAMERA,
    override var defaultLayoutId: Int = R.layout.trait_photo,
    override var layoutView: View? = null,
    override var databaseName: String = "photo",
    override var nameStringResourceId: Int = R.string.traits_format_photo,
    override var iconDrawableResourceId: Int = R.drawable.ic_trait_camera,
    override var stringNameAux: ((Context) -> String?)? = null
) : TraitFormat(
    format = format,
    defaultLayoutId = defaultLayoutId,
    layoutView = layoutView,
    databaseName = databaseName,
    nameStringResourceId = nameStringResourceId,
    iconDrawableResourceId = iconDrawableResourceId,
    stringNameAux = stringNameAux,
    NameParameter(),
    DetailsParameter(),
    CropImageParameter()
), ValuePresenter by UriPresenter()