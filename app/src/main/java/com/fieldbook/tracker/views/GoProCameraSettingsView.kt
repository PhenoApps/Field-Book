package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import android.widget.CheckBox
import androidx.constraintlayout.widget.ConstraintLayout
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject

/**
 * View that contains the settings for the go pro camera trait.
 */
open class GoProCameraSettingsView: ConstraintLayout {

    private val saveImageCb: CheckBox
    private var traitObject: TraitObject? = null

    init {

        val view = inflate(context, R.layout.view_trait_go_pro_photo_settings, this)
        saveImageCb = view.findViewById(R.id.view_trait_go_pro_save_image_cb)
    }

    constructor(ctx: Context, traitObject: TraitObject) : super(ctx) {
        this.traitObject = traitObject
        setup()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    fun commitChanges() {

        traitObject?.saveAttributeValues()
    }

    private fun setup() {

        setupPreviewCheckBox()
    }

    private fun setupPreviewCheckBox() {

        saveImageCb.setOnCheckedChangeListener { _, isChecked ->

            traitObject?.saveImage = isChecked

        }

        saveImageCb.isChecked = traitObject?.saveImage != false
    }
}