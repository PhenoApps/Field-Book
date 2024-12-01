package com.fieldbook.tracker.traits.formats

import android.content.Context
import com.fieldbook.tracker.R

open class PhotoFormat(
    override var stringNameAux: ((Context) -> String?)? = { ctx -> ctx.getString(R.string.trait_name_alt_photo_system) }
) : BasePhotoFormat()