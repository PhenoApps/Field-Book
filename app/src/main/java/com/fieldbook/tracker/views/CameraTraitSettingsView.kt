package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import android.util.Size
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.camera.CameraCapabilities
import com.fieldbook.tracker.utilities.camera.ExposureMode
import com.fieldbook.tracker.utilities.camera.LockedCameraValuesStore
import com.fieldbook.tracker.utilities.camera.ShutterFormat

/**
 * View that contains the settings for the camera trait.
 */
open class CameraTraitSettingsView: ConstraintLayout {

    private val prefs by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    protected var cameraSupportedResolutions: List<Size>? = null
    protected val systemCameraRg: RadioGroup
    protected val previewCb: CheckBox
    protected val resolutionGroup: RadioGroup
    protected val resolutionTitle: TextView
    protected val resolutionFrameLayout: FrameLayout
    protected val cropButton: Button

    private val advancedLayout: LinearLayout
    private val exposureTitle: TextView
    private val exposureRg: RadioGroup
    private val exposureLockedRb: RadioButton
    private val exposureManualRb: RadioButton
    private val manualLayout: LinearLayout
    private val isoTitle: TextView
    private val isoSeekBar: SeekBar
    private val shutterTitle: TextView
    private val shutterSeekBar: SeekBar
    private val awbLockCb: CheckBox
    private val saveRawCb: CheckBox

    private var lastCameraId: Int? = null
    private var lastPreview: Boolean? = null
    private var lastResolutionIndex: Int? = null

    // advanced camera controls are stored per trait
    private var trait: TraitObject? = null
    private var capabilities: CameraCapabilities = CameraCapabilities.NONE
    private var allowRaw = false

    private var isoSteps: List<Int> = listOf()
    private var shutterSteps: List<Long> = listOf()

    // staged advanced values, only written to the trait in commitChanges()
    private var stagedExposureMode: ExposureMode? = null
    private var stagedIso: Int? = null
    private var stagedExposureTimeNs: Long? = null
    private var stagedAwbLock: Boolean? = null
    private var stagedSaveRaw: Boolean? = null

    init {

        val view = inflate(context, R.layout.view_trait_photo_settings, this)
        systemCameraRg = view.findViewById(R.id.view_trait_photo_settings_camera_rg)
        previewCb = view.findViewById(R.id.view_trait_photo_preview_cb)
        resolutionGroup = view.findViewById(R.id.view_trait_photo_settings_resolution_rg)
        resolutionTitle = view.findViewById(R.id.view_trait_photo_settings_resolution_tv)
        resolutionFrameLayout = view.findViewById(R.id.view_trait_photo_settings_resolution_fl)

        advancedLayout = view.findViewById(R.id.view_trait_photo_settings_advanced_ll)
        exposureTitle = view.findViewById(R.id.view_trait_photo_settings_exposure_tv)
        exposureRg = view.findViewById(R.id.view_trait_photo_settings_exposure_rg)
        exposureLockedRb = view.findViewById(R.id.view_trait_photo_settings_exposure_locked_rb)
        exposureManualRb = view.findViewById(R.id.view_trait_photo_settings_exposure_manual_rb)
        manualLayout = view.findViewById(R.id.view_trait_photo_settings_manual_ll)
        isoTitle = view.findViewById(R.id.view_trait_photo_settings_iso_tv)
        isoSeekBar = view.findViewById(R.id.view_trait_photo_settings_iso_sb)
        shutterTitle = view.findViewById(R.id.view_trait_photo_settings_shutter_tv)
        shutterSeekBar = view.findViewById(R.id.view_trait_photo_settings_shutter_sb)
        awbLockCb = view.findViewById(R.id.view_trait_photo_settings_awb_lock_cb)
        saveRawCb = view.findViewById(R.id.view_trait_photo_settings_save_raw_cb)

        cropButton = view.findViewById(R.id.view_trait_photo_settings_crop_btn)
        // Set the visibility of the crop button to GONE if the trait does not support cropping
        (context as CollectActivity).currentTrait.cropImage?.let {
            cropButton.visibility = if (it) View.VISIBLE else View.GONE
        }
    }

    constructor(ctx: Context, supportedResolutions: List<Size>) : super(ctx) {
        this.cameraSupportedResolutions = supportedResolutions
        setup()
    }

    constructor(
        ctx: Context,
        supportedResolutions: List<Size>,
        trait: TraitObject?,
        capabilities: CameraCapabilities,
        allowRaw: Boolean
    ) : super(ctx) {
        this.cameraSupportedResolutions = supportedResolutions
        this.trait = trait
        this.capabilities = capabilities
        this.allowRaw = allowRaw
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

        commitAdvancedChanges()
    }

    private fun commitAdvancedChanges() {

        val t = trait ?: return

        var changed = false

        stagedExposureMode?.let { t.cameraExposureMode = it.value; changed = true }
        stagedIso?.let { t.cameraIso = it.toString(); changed = true }
        stagedExposureTimeNs?.let { t.cameraExposureTimeNs = it.toString(); changed = true }
        stagedAwbLock?.let { t.cameraAwbLock = it; changed = true }
        stagedSaveRaw?.let { t.cameraSaveRaw = it; changed = true }

        if (changed) t.saveAttributeValues()

        // a new exposure or white balance choice should be metered fresh rather than restored
        if (stagedExposureMode != null || stagedAwbLock != null) {
            LockedCameraValuesStore.clear(t.id)
        }
    }

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

        setupAdvancedControls()
        setupSystemCheckBox()
        setupCropButton()
    }

    private fun setupCropButton() {

        cropButton.setOnClickListener {

            (context as CollectActivity).requestAndCropImage(true, false)

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

        resolutionGroup.setOnCheckedChangeListener { group, checkedId ->

            lastResolutionIndex = checkedId
        }

        cameraSupportedResolutions?.let {
            setSupportedResolutions(it)
        }
    }

    private fun isAdvancedAvailable() = trait != null &&
            (capabilities.aeLockAvailable || capabilities.manualSensor ||
                    capabilities.awbLockAvailable || (allowRaw && capabilities.raw))

    /**
     * Populates the advanced controls from the trait, showing only what this device supports.
     * Values that the device can't use are left untouched in the trait unless the user changes them.
     */
    private fun setupAdvancedControls() {

        val t = trait ?: return

        if (!isAdvancedAvailable()) return

        val savedMode = ExposureMode.parse(t.cameraExposureMode)

        // exposure mode
        val lockSupported = capabilities.aeLockAvailable
        val manualSupported = capabilities.manualSensor

        exposureLockedRb.visibility = if (lockSupported) View.VISIBLE else View.GONE
        exposureManualRb.visibility = if (manualSupported) View.VISIBLE else View.GONE

        val exposureVisible = if (lockSupported || manualSupported) View.VISIBLE else View.GONE
        exposureTitle.visibility = exposureVisible
        exposureRg.visibility = exposureVisible

        val displayedMode = when {
            savedMode == ExposureMode.LOCKED && lockSupported -> ExposureMode.LOCKED
            savedMode == ExposureMode.MANUAL && manualSupported -> ExposureMode.MANUAL
            else -> ExposureMode.AUTO
        }

        exposureRg.check(exposureRadioId(displayedMode))

        manualLayout.visibility = if (displayedMode == ExposureMode.MANUAL) View.VISIBLE else View.GONE

        exposureRg.setOnCheckedChangeListener { _, checkedId ->

            val mode = when (checkedId) {
                R.id.view_trait_photo_settings_exposure_locked_rb -> ExposureMode.LOCKED
                R.id.view_trait_photo_settings_exposure_manual_rb -> ExposureMode.MANUAL
                else -> ExposureMode.AUTO
            }

            stagedExposureMode = mode

            manualLayout.visibility = if (mode == ExposureMode.MANUAL) View.VISIBLE else View.GONE

            // make sure the values shown on the sliders are saved when manual is first chosen
            if (mode == ExposureMode.MANUAL) {
                if (stagedIso == null && isoSteps.isNotEmpty()) stagedIso = isoSteps[isoSeekBar.progress]
                if (stagedExposureTimeNs == null && shutterSteps.isNotEmpty()) stagedExposureTimeNs = shutterSteps[shutterSeekBar.progress]
            }
        }

        if (manualSupported) setupManualControls(t)

        // white balance lock
        awbLockCb.visibility = if (capabilities.awbLockAvailable) View.VISIBLE else View.GONE
        awbLockCb.isChecked = t.cameraAwbLock
        awbLockCb.setOnCheckedChangeListener { _, isChecked -> stagedAwbLock = isChecked }

        // raw
        saveRawCb.visibility = if (allowRaw && capabilities.raw) View.VISIBLE else View.GONE
        saveRawCb.isChecked = t.cameraSaveRaw
        saveRawCb.setOnCheckedChangeListener { _, isChecked -> stagedSaveRaw = isChecked }
    }

    private fun setupManualControls(t: TraitObject) {

        isoSteps = capabilities.isoSteps()
        shutterSteps = capabilities.shutterStepsNs()

        setupStepSeekBar(
            isoSeekBar,
            isoTitle,
            isoSteps,
            capabilities.clampIso(t.cameraIso.toIntOrNull()),
            { context.getString(R.string.view_trait_photo_settings_iso, it) },
            { stagedIso = it }
        )

        setupStepSeekBar(
            shutterSeekBar,
            shutterTitle,
            shutterSteps,
            capabilities.clampExposure(t.cameraExposureTimeNs.toLongOrNull()),
            { context.getString(R.string.view_trait_photo_settings_shutter, ShutterFormat.label(it)) },
            { stagedExposureTimeNs = it }
        )
    }

    /**
     * Configures a SeekBar that steps through [steps], labelling the current value in [title].
     */
    private fun <T : Number> setupStepSeekBar(
        seekBar: SeekBar,
        title: TextView,
        steps: List<T>,
        initial: T,
        label: (T) -> String,
        onChanged: (T) -> Unit
    ) {

        if (steps.isEmpty()) {
            seekBar.visibility = View.GONE
            title.visibility = View.GONE
            return
        }

        val index = CameraCapabilities.nearestIndex(steps, initial)

        title.text = label(steps[index])

        // a single step can't be adjusted, so only show its label
        if (steps.size < 2) {
            seekBar.visibility = View.GONE
            return
        }

        seekBar.max = steps.lastIndex
        seekBar.progress = index

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = steps[progress.coerceIn(0, steps.lastIndex)]
                title.text = label(value)
                if (fromUser) onChanged(value)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
    }

    private fun exposureRadioId(mode: ExposureMode) = when (mode) {
        ExposureMode.AUTO -> R.id.view_trait_photo_settings_exposure_auto_rb
        ExposureMode.LOCKED -> R.id.view_trait_photo_settings_exposure_locked_rb
        ExposureMode.MANUAL -> R.id.view_trait_photo_settings_exposure_manual_rb
    }

    private fun setupEmbeddedCameraSettings() {

        previewCb.visibility = View.VISIBLE
        resolutionGroup.visibility = View.VISIBLE
        resolutionTitle.visibility = View.VISIBLE
        resolutionFrameLayout.visibility = View.VISIBLE
        advancedLayout.visibility = if (isAdvancedAvailable()) View.VISIBLE else View.GONE

        setupPreviewCheckBox()
        setupResolutionGroup()
    }

    private fun setupSystemCameraSettings() {

        previewCb.visibility = View.GONE
        resolutionGroup.visibility = View.GONE
        resolutionTitle.visibility = View.GONE
        resolutionFrameLayout.visibility = View.GONE
        advancedLayout.visibility = View.GONE
    }
}
