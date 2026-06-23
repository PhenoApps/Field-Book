package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.CheckBox
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.ThemedAlertDialog

/**
 * View that contains the settings for the canon camera trait.
 */
open class CanonCameraTraitSettingsView : ConstraintLayout {

    private val prefs by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    private lateinit var previewCb: CheckBox
    private lateinit var ssidEditText: TitledEditView
    private lateinit var ipEditText: TitledEditView
    private lateinit var portEditText: TitledEditView
    private lateinit var debugCb: CheckBox

    private var lastPreview: Boolean? = null

    constructor(ctx: Context) : super(ThemedAlertDialog.contentContext(ctx)) {
        bindLayout()
        setup()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle,
    )

    private fun bindLayout() {
        LayoutInflater.from(context).inflate(R.layout.view_trait_canon_photo_settings, this, true)
        previewCb = findViewById(R.id.view_trait_canon_photo_settings_preview_cb)
        ssidEditText = findViewById(R.id.view_trait_canon_photo_settings_ssid_tev)
        ipEditText = findViewById(R.id.view_trait_canon_photo_settings_ip_tev)
        portEditText = findViewById(R.id.view_trait_canon_photo_settings_port_tev)
        debugCb = findViewById(R.id.view_trait_canon_photo_settings_debug_cb)
        ThemedAlertDialog.applyDialogTextColors(this)
    }

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
