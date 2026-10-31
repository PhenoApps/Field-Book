package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import android.widget.CheckBox
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys

/**
 * View that contains the settings for the go pro camera trait.
 */
open class GoProCameraSettingsView: ConstraintLayout {

    private val prefs by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    private val previewCb: CheckBox
    private val saveImageCb: CheckBox
    private var traitObject: TraitObject? = null

    private var lastPreview: Boolean? = null

    init {

        val view = inflate(context, R.layout.view_trait_go_pro_photo_settings, this)
        previewCb = view.findViewById(R.id.view_trait_go_pro_preview_cb)
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

        lastPreview?.let { flag ->

            prefs.edit().putBoolean(GeneralKeys.GOPRO_CAMERA_PREVIEW, flag).apply()

        }

        traitObject?.saveAttributeValues()
    }

    private fun setup() {

        setupPreviewCheckBox()

        setupSaveImageCheckBox()

        //videos are never copied off the camera, only their file name is saved
        saveImageCb.visibility = if (traitObject?.captureVideo == true) GONE else VISIBLE
    }

    private fun setupPreviewCheckBox() {

        previewCb.setOnCheckedChangeListener { _, isChecked ->

            lastPreview = isChecked
        }

        previewCb.isChecked = prefs.getBoolean(GeneralKeys.GOPRO_CAMERA_PREVIEW, true)
    }

    private fun setupSaveImageCheckBox() {

        saveImageCb.setOnCheckedChangeListener { _, isChecked ->

            traitObject?.saveImage = isChecked

        }

        saveImageCb.isChecked = traitObject?.saveImage != false
    }
}