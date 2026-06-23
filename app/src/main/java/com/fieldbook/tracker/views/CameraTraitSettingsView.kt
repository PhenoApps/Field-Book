package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.ThemedAlertDialog

/**
 * View that contains the settings for the camera trait.
 */
open class CameraTraitSettingsView : ConstraintLayout {

    private val prefs by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    private var activityContext: Context? = null
    protected var cameraSupportedResolutions: List<Size>? = null
    protected lateinit var systemCameraRg: RadioGroup
    protected lateinit var previewCb: CheckBox
    protected lateinit var resolutionGroup: RadioGroup
    protected lateinit var resolutionTitle: TextView
    protected lateinit var resolutionFrameLayout: FrameLayout
    protected lateinit var cropButton: Button

    private var lastCameraId: Int? = null
    private var lastPreview: Boolean? = null
    private var lastResolutionIndex: Int? = null

    constructor(ctx: Context, supportedResolutions: List<Size>) : super(ThemedAlertDialog.contentContext(ctx)) {
        activityContext = ctx
        this.cameraSupportedResolutions = supportedResolutions
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

    private fun collectActivity(): CollectActivity =
        (activityContext ?: context) as CollectActivity

    private fun bindLayout() {
        LayoutInflater.from(context).inflate(R.layout.view_trait_photo_settings, this, true)
        systemCameraRg = findViewById(R.id.view_trait_photo_settings_camera_rg)
        previewCb = findViewById(R.id.view_trait_photo_preview_cb)
        resolutionGroup = findViewById(R.id.view_trait_photo_settings_resolution_rg)
        resolutionTitle = findViewById(R.id.view_trait_photo_settings_resolution_tv)
        resolutionFrameLayout = findViewById(R.id.view_trait_photo_settings_resolution_fl)
        cropButton = findViewById(R.id.view_trait_photo_settings_crop_btn)
        collectActivity().currentTrait.cropImage?.let {
            cropButton.visibility = if (it) View.VISIBLE else View.GONE
        }
        ThemedAlertDialog.applyDialogTextColors(this)
    }

    fun commitChanges() {
        lastCameraId?.let {
            prefs.edit().putInt(GeneralKeys.CAMERA_SYSTEM, it).apply()
        }
        lastPreview?.let {
            prefs.edit().putBoolean(GeneralKeys.CAMERA_SYSTEM_PREVIEW, it).apply()
        }
        lastResolutionIndex?.let {
            prefs.edit().putInt(GeneralKeys.CAMERA_RESOLUTION, it).apply()
        }
    }

    private fun setSupportedResolutions(resolutions: List<Size>) {
        val savedResolutionIndex = prefs.getInt(GeneralKeys.CAMERA_RESOLUTION, 0)
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
        setupSystemCheckBox()
        setupCropButton()
    }

    private fun setupCropButton() {
        cropButton.setOnClickListener {
            collectActivity().requestAndCropImage(true, false)
        }
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
            lastCameraId = checkedId
            setupSettingsModeBasedOnPreference(systemCameraRg.checkedRadioButtonId)
        }

        systemCameraRg.check(prefs.getInt(GeneralKeys.CAMERA_SYSTEM, R.id.view_trait_photo_settings_camera_custom_rb))
        setupSettingsModeBasedOnPreference(systemCameraRg.checkedRadioButtonId)
    }

    private fun setupPreviewCheckBox() {
        previewCb.setOnCheckedChangeListener { _, isChecked ->
            lastPreview = isChecked
        }
        previewCb.isChecked = prefs.getBoolean(GeneralKeys.CAMERA_SYSTEM_PREVIEW, true)
    }

    private fun setupResolutionGroup() {
        resolutionGroup.setOnCheckedChangeListener { _, checkedId ->
            lastResolutionIndex = checkedId
        }
        cameraSupportedResolutions?.let {
            setSupportedResolutions(it)
        }
    }

    private fun setupEmbeddedCameraSettings() {
        previewCb.visibility = View.VISIBLE
        resolutionGroup.visibility = View.VISIBLE
        resolutionTitle.visibility = View.VISIBLE
        resolutionFrameLayout.visibility = View.VISIBLE
        setupPreviewCheckBox()
        setupResolutionGroup()
    }

    private fun setupSystemCameraSettings() {
        previewCb.visibility = View.GONE
        resolutionGroup.visibility = View.GONE
        resolutionTitle.visibility = View.GONE
        resolutionFrameLayout.visibility = View.GONE
    }
}
