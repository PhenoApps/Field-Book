package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.ThemedAlertDialog

/**
 * Minimal settings view used by VideoTraitLayout: shows only resolution choices and crop button.
 */
class VideoCameraSettingsView : ConstraintLayout {

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    private var activityContext: Context? = null
    private var resolutionGroup: RadioGroup? = null
    private var resolutionTitle: View? = null
    private var resolutionFrameLayout: FrameLayout? = null
    private var cropButton: Button? = null

    private var cameraSupportedResolutions: List<Size>? = null
    private var lastResolutionIndex: Int? = null

    constructor(ctx: Context, supportedResolutions: List<Size>) :
        super(ThemedAlertDialog.contentContext(ctx)) {
        activityContext = ctx
        this.cameraSupportedResolutions = supportedResolutions
        bindLayout()
        setup()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    private fun collectActivity(): CollectActivity? =
        (activityContext ?: context) as? CollectActivity

    private fun bindLayout() {
        LayoutInflater.from(context).inflate(R.layout.view_trait_photo_settings, this, true)
        resolutionGroup = findViewById(R.id.view_trait_photo_settings_resolution_rg)
        resolutionTitle = findViewById(R.id.view_trait_photo_settings_resolution_tv)
        resolutionFrameLayout = findViewById(R.id.view_trait_photo_settings_resolution_fl)
        cropButton = findViewById(R.id.view_trait_photo_settings_crop_btn)

        findViewById<View?>(R.id.view_trait_photo_settings_camera_rg)?.visibility = GONE
        findViewById<View?>(R.id.view_trait_photo_preview_cb)?.visibility = GONE
        findViewById<TextView?>(R.id.view_trait_photo_settings_camera_choice_tv)?.visibility = GONE

        ThemedAlertDialog.applyDialogTextColors(this)
    }

    fun commitChanges() {
        lastResolutionIndex?.let { prefs.edit { putInt(GeneralKeys.CAMERA_RESOLUTION, it) } }
    }

    private fun setup() {
        setupCropButton()
        setupResolutionGroup()
    }

    private fun setupCropButton() {
        collectActivity()?.currentTrait?.cropImage?.let {
            cropButton?.visibility = if (it) View.VISIBLE else View.GONE
        }
        cropButton?.setOnClickListener {
            collectActivity()?.requestAndCropImage(false, true)
        }
    }

    private fun setupResolutionGroup() {
        resolutionGroup?.setOnCheckedChangeListener { _, checkedId -> lastResolutionIndex = checkedId }
        cameraSupportedResolutions?.let { setSupportedResolutions(it) }
    }

    private fun setSupportedResolutions(resolutions: List<Size>) {
        val savedResolutionIndex = prefs.getInt(GeneralKeys.CAMERA_RESOLUTION, 0)
        val textColor = ThemedAlertDialog.dialogTextColor(context)

        resolutionGroup?.removeAllViews()
        resolutionGroup?.orientation = RadioGroup.VERTICAL
        resolutions.forEachIndexed { index, resolution ->
            val radioButton = RadioButton(context)
            radioButton.visibility = View.VISIBLE
            radioButton.text = resolution.toString()
            radioButton.setTextColor(textColor)
            radioButton.id = index
            radioButton.isChecked = index == savedResolutionIndex
            resolutionGroup?.addView(radioButton)
        }
    }
}
