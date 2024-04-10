package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import android.util.Size
import android.view.View
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.preferences.GeneralKeys

/**
 * View that contains the settings for the camera trait.
 */
open class CameraTraitSettingsView: ConstraintLayout {

    private val prefs by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    private var supportedResolutions: List<Size>? = null
    private val systemCameraRg: RadioGroup
    private val previewCb: CheckBox
    private val resolutionGroup: RadioGroup
    private val resolutionTitle: TextView

    init {

        val view = inflate(context, R.layout.view_trait_photo_settings, this)
        systemCameraRg = view.findViewById(R.id.view_trait_photo_settings_camera_rg)
        previewCb = view.findViewById(R.id.view_trait_photo_preview_cb)
        resolutionGroup = view.findViewById(R.id.view_trait_photo_settings_resolution_rg)
        resolutionTitle = view.findViewById(R.id.view_trait_photo_settings_resolution_tv)
    }

    constructor(ctx: Context, supportedResolutions: List<Size>) : super(ctx) {
        this.supportedResolutions = supportedResolutions
        setup()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int, defStyleRes: Int) : super(
        context,
        attrs,
        defStyle,
        defStyleRes
    )

    private fun setSupportedResolutions(resolutions: List<Size>) {

        val savedResolutionIndex = prefs.getInt(GeneralKeys.CAMERA_RESOLUTION, 0)

        resolutionGroup.removeAllViews()

        resolutionGroup.orientation = RadioGroup.VERTICAL

        resolutions.forEachIndexed { index, resolution ->

            val radioButton = RadioButton(context)
            radioButton.visibility = View.VISIBLE
            radioButton.text = resolution.toString()
            radioButton.id = index
            radioButton.isChecked = index == savedResolutionIndex
            resolutionGroup.addView(radioButton)
        }
    }

    private fun setup() {

        setupSystemCheckBox()

    }

    private fun setupSettingsModeBasedOnPreference(checkedRadioButtonId: Int) {

        if (checkedRadioButtonId == R.id.view_trait_photo_settings_camera_system_rb) {

            setupSystemCameraSettings()

        } else {

            setupEmbeddedCameraSettings()

        }
    }

    private fun setupSystemCheckBox() {

        systemCameraRg.setOnCheckedChangeListener { _, checkedId ->

            prefs.edit().putInt(GeneralKeys.CAMERA_SYSTEM, checkedId).apply()

            setupSettingsModeBasedOnPreference(systemCameraRg.checkedRadioButtonId)
        }

        systemCameraRg.check(prefs.getInt(GeneralKeys.CAMERA_SYSTEM, R.id.view_trait_photo_settings_camera_system_rb))

        setupSettingsModeBasedOnPreference(systemCameraRg.checkedRadioButtonId)
    }

    private fun setupPreviewCheckBox() {

        previewCb.setOnCheckedChangeListener { _, isChecked ->

            prefs.edit().putBoolean(GeneralKeys.CAMERA_SYSTEM_PREVIEW, isChecked).apply()
        }

        previewCb.isChecked = prefs.getBoolean(GeneralKeys.CAMERA_SYSTEM_PREVIEW, false)
    }

    private fun setupResolutionGroup() {

        resolutionGroup.setOnCheckedChangeListener { group, checkedId ->

            println(checkedId)
            prefs.edit().putInt(GeneralKeys.CAMERA_RESOLUTION, checkedId).apply()

        }

        supportedResolutions?.let {
            setSupportedResolutions(it)
        }
    }

    private fun setupEmbeddedCameraSettings() {

        previewCb.visibility = View.VISIBLE
        resolutionGroup.visibility = View.VISIBLE
        resolutionTitle.visibility = View.VISIBLE

        setupPreviewCheckBox()
        setupResolutionGroup()
    }

    private fun setupSystemCameraSettings() {

        previewCb.visibility = View.GONE
        resolutionGroup.visibility = View.GONE
        resolutionTitle.visibility = View.GONE
    }
}