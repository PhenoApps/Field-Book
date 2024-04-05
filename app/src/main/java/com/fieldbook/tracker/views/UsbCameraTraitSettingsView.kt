package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import android.util.Size
import android.view.View
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.preferences.GeneralKeys

/**
 * View that contains the settings for the usb camera trait.
 */
open class UsbCameraTraitSettingsView: ConstraintLayout {

    private val prefs by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    private var supportedResolutions: List<Size>? = null
    private val previewCb: CheckBox
    private val autoFocusCb: CheckBox
    private val autoWhiteBalance: CheckBox
    private val resolutionGroup: RadioGroup
    private val resolutionTitle: TextView

    init {

        val view = inflate(context, R.layout.view_trait_usb_photo_settings, this)
        autoFocusCb = view.findViewById(R.id.view_trait_usb_photo_auto_focus_cb)
        autoWhiteBalance = view.findViewById(R.id.view_trait_usb_photo_white_balance_cb)
        previewCb = view.findViewById(R.id.view_trait_usb_photo_preview_cb)
        resolutionGroup = view.findViewById(R.id.view_trait_usb_photo_settings_resolution_rg)
        resolutionTitle = view.findViewById(R.id.view_trait_usb_photo_settings_resolution_tv)
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

        val savedResolutionIndex = prefs.getInt(GeneralKeys.USB_CAMERA_RESOLUTION_INDEX, 0)

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

        setupAutoFocusCheckBox()
        setupAutoWhiteBalanceCheckBox()
        setupPreviewCheckBox()
        setupResolutionGroup()
    }

    private fun setupAutoFocusCheckBox() {

        autoFocusCb.setOnCheckedChangeListener { _, isChecked ->

            prefs.edit().putBoolean(GeneralKeys.USB_CAMERA_AUTO_FOCUS, isChecked).apply()
        }

        autoFocusCb.isChecked = prefs.getBoolean(GeneralKeys.USB_CAMERA_AUTO_FOCUS, true)
    }

    private fun setupAutoWhiteBalanceCheckBox() {

        autoWhiteBalance.setOnCheckedChangeListener { _, isChecked ->

            prefs.edit().putBoolean(GeneralKeys.USB_CAMERA_AUTO_WHITE_BALANCE, isChecked).apply()
        }

        autoWhiteBalance.isChecked = prefs.getBoolean(GeneralKeys.USB_CAMERA_AUTO_WHITE_BALANCE, true)
    }

    private fun setupPreviewCheckBox() {

        previewCb.setOnCheckedChangeListener { _, isChecked ->

            prefs.edit().putBoolean(GeneralKeys.USB_CAMERA_PREVIEW, isChecked).apply()
        }

        previewCb.isChecked = prefs.getBoolean(GeneralKeys.USB_CAMERA_PREVIEW, true)
    }

    private fun setupResolutionGroup() {

        resolutionGroup.setOnCheckedChangeListener { group, checkedId ->

            prefs.edit().putInt(GeneralKeys.USB_CAMERA_RESOLUTION_INDEX, checkedId).apply()

        }

        supportedResolutions?.let {
            setSupportedResolutions(it)
        }
    }
}