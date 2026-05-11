package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import android.widget.CheckBox
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.preferences.GeneralKeys

/**
 * View that contains the settings for the canon camera trait.
 */
open class CanonCameraTraitSettingsView: ConstraintLayout {

    private val prefs by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    private val previewCb: CheckBox
    private val ssidEditText: TitledEditView
    private val ipEditText: TitledEditView
    private val portEditText: TitledEditView
    private val debugCb: CheckBox

    private var lastPreview: Boolean? = null

    init {

        val view = inflate(context, R.layout.view_trait_canon_photo_settings, this)
        previewCb = view.findViewById(R.id.view_trait_canon_photo_settings_preview_cb)
        ssidEditText = view.findViewById(R.id.view_trait_canon_photo_settings_ssid_tev)
        ipEditText = view.findViewById(R.id.view_trait_canon_photo_settings_ip_tev)
        portEditText = view.findViewById(R.id.view_trait_canon_photo_settings_port_tev)
        debugCb = view.findViewById(R.id.view_trait_canon_photo_settings_debug_cb)
    }

    constructor(ctx: Context) : super(ctx) {
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

            prefs.edit().putBoolean(GeneralKeys.CANON_CAMERA_PREVIEW, flag).apply()

        }
    }

    private fun setup() {

        setupPreviewCheckBox()
    }

    private fun setupPreviewCheckBox() {

        previewCb.setOnCheckedChangeListener { _, isChecked ->

            lastPreview = isChecked
        }

        previewCb.isChecked = prefs.getBoolean(GeneralKeys.CANON_CAMERA_PREVIEW, true)
    }
}