package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.ThemedAlertDialog

/**
 * View that contains the settings for the usb camera trait.
 */
open class UsbCameraTraitSettingsView : ConstraintLayout {

    private val prefs by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    private var supportedResolutions: List<Size>? = null
    private var initialMaxIndexSelection: Int = 0

    private lateinit var previewCb: CheckBox
    private lateinit var autoFocusCb: CheckBox
    private lateinit var autoWhiteBalance: CheckBox
    private lateinit var resolutionGroup: RadioGroup
    private lateinit var resolutionTitle: TextView

    private var lastAutoFocus: Boolean? = null
    private var lastWhiteBalance: Boolean? = null
    private var lastPreview: Boolean? = null
    private var lastResolutionIndex: Int? = null

    constructor(ctx: Context, supportedResolutions: List<Size>, initialMaxIndexSelection: Int) :
        super(ThemedAlertDialog.contentContext(ctx)) {
        this.supportedResolutions = supportedResolutions
        this.initialMaxIndexSelection = initialMaxIndexSelection
        bindLayout()
        setup()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle,
    )

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int, defStyleRes: Int) : super(
        context,
        attrs,
        defStyle,
        defStyleRes,
    )

    private fun bindLayout() {
        LayoutInflater.from(context).inflate(R.layout.view_trait_usb_photo_settings, this, true)
        autoFocusCb = findViewById(R.id.view_trait_usb_photo_auto_focus_cb)
        autoWhiteBalance = findViewById(R.id.view_trait_usb_photo_white_balance_cb)
        previewCb = findViewById(R.id.view_trait_usb_photo_preview_cb)
        resolutionGroup = findViewById(R.id.view_trait_usb_photo_settings_resolution_rg)
        resolutionTitle = findViewById(R.id.view_trait_usb_photo_settings_resolution_tv)
        ThemedAlertDialog.applyDialogTextColors(this)
    }

    fun commitChanges() {
        lastAutoFocus?.let { flag ->
            prefs.edit().putBoolean(GeneralKeys.USB_CAMERA_AUTO_FOCUS, flag).apply()
        }
        lastWhiteBalance?.let { flag ->
            prefs.edit().putBoolean(GeneralKeys.USB_CAMERA_AUTO_WHITE_BALANCE, flag).apply()
        }
        lastPreview?.let { flag ->
            prefs.edit().putBoolean(GeneralKeys.USB_CAMERA_PREVIEW, flag).apply()
        }
        lastResolutionIndex?.let { index ->
            prefs.edit().putInt(GeneralKeys.USB_CAMERA_RESOLUTION_INDEX, index).apply()
        }
    }

    private fun setSupportedResolutions(resolutions: List<Size>) {
        val savedResolutionIndex = prefs.getInt(GeneralKeys.USB_CAMERA_RESOLUTION_INDEX, initialMaxIndexSelection)
        val textColor = ThemedAlertDialog.dialogTextColor(context)

        resolutionGroup.removeAllViews()
        resolutionGroup.orientation = RadioGroup.VERTICAL

        resolutions.forEachIndexed { index, resolution ->
            val radioButton = RadioButton(context)
            radioButton.visibility = View.VISIBLE
            radioButton.text = resolution.toString()
            radioButton.setTextColor(textColor)
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
            lastAutoFocus = isChecked
        }
        autoFocusCb.isChecked = prefs.getBoolean(GeneralKeys.USB_CAMERA_AUTO_FOCUS, true)
    }

    private fun setupAutoWhiteBalanceCheckBox() {
        autoWhiteBalance.setOnCheckedChangeListener { _, isChecked ->
            lastWhiteBalance = isChecked
        }
        autoWhiteBalance.isChecked = prefs.getBoolean(GeneralKeys.USB_CAMERA_AUTO_WHITE_BALANCE, true)
    }

    private fun setupPreviewCheckBox() {
        previewCb.setOnCheckedChangeListener { _, isChecked ->
            lastPreview = isChecked
        }
        previewCb.isChecked = prefs.getBoolean(GeneralKeys.USB_CAMERA_PREVIEW, true)
    }

    private fun setupResolutionGroup() {
        resolutionGroup.setOnCheckedChangeListener { _, checkedId ->
            lastResolutionIndex = checkedId
        }
        supportedResolutions?.let {
            setSupportedResolutions(it)
        }
    }
}
