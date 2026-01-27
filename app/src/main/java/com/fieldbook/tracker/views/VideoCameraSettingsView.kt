package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import android.util.Size
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.preferences.GeneralKeys
import androidx.core.content.edit

/**
 * Minimal settings view used by VideoTraitLayout: shows only resolution choices and crop button.
 * Saves only the resolution preference; crop button launches the same crop intent used elsewhere.
 */
class VideoCameraSettingsView : ConstraintLayout {

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    private var resolutionGroup: RadioGroup? = null
    private var resolutionTitle: View? = null
    private var resolutionFrameLayout: FrameLayout? = null
    private var cropButton: Button? = null

    private var cameraSupportedResolutions: List<Size>? = null
    private var lastResolutionIndex: Int? = null

    constructor(ctx: Context, supportedResolutions: List<Size>) : super(ctx) {
        this.cameraSupportedResolutions = supportedResolutions
        val view = inflate(context, R.layout.view_trait_photo_settings, this)

        val summaryView = view.findViewById<TextView>(R.id.view_trait_photo_settings_camera_choice_tv)
        resolutionGroup = view.findViewById(R.id.view_trait_photo_settings_resolution_rg)
        resolutionTitle = view.findViewById(R.id.view_trait_photo_settings_resolution_tv)
        resolutionFrameLayout = view.findViewById(R.id.view_trait_photo_settings_resolution_fl)
        cropButton = view.findViewById(R.id.view_trait_photo_settings_crop_btn)

        view.findViewById<View?>(R.id.view_trait_photo_settings_camera_rg)?.visibility = GONE
        view.findViewById<View?>(R.id.view_trait_photo_preview_cb)?.visibility = GONE
        summaryView?.visibility = GONE

        setup()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    fun commitChanges() {
        lastResolutionIndex?.let { prefs.edit { putInt(GeneralKeys.CAMERA_RESOLUTION, it) } }
    }

    private fun setup() {
        setupCropButton()
        setupResolutionGroup()
    }

    private fun setupCropButton() {
        // Set the visibility of the crop button to GONE if the trait does not support cropping
        (context as? CollectActivity)?.currentTrait?.cropImage?.let {
            cropButton?.visibility = if (it) VISIBLE else GONE
        }

        cropButton?.setOnClickListener {
            (context as? CollectActivity)?.requestAndCropImage()
        }
    }

    private fun setupResolutionGroup() {
        resolutionGroup?.setOnCheckedChangeListener { _, checkedId -> lastResolutionIndex = checkedId }
        cameraSupportedResolutions?.let { setSupportedResolutions(it) }
    }

    private fun setSupportedResolutions(resolutions: List<Size>) {
        val savedResolutionIndex = prefs.getInt(GeneralKeys.CAMERA_RESOLUTION, 0)
        resolutionGroup?.removeAllViews()
        resolutionGroup?.orientation = RadioGroup.VERTICAL
        resolutions.forEachIndexed { index, resolution ->
            val radioButton = RadioButton(context)
            radioButton.visibility = VISIBLE
            radioButton.text = resolution.toString()
            radioButton.id = index
            radioButton.isChecked = index == savedResolutionIndex
            resolutionGroup?.addView(radioButton)
        }
    }
}
