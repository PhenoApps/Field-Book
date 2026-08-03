package com.fieldbook.tracker.views

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.preferences.PreferenceKeys

/**
 * View that contains the default fb edit text and the repeated values view feature.
 * This class delegates the implementation based on the settings.
 */
class CollectInputView : ConstraintLayout {

    //used to initialize the selected value with an index (mainly used for navigation)
    var forceInitialRep = -1

    var hasData: Boolean = false

    /** When true, never uses Collect repeated-measures UI (tree node sidecar buffer). */
    var detachedFromCollect: Boolean = false

    private var isObservationSaved: Boolean = false

    private val originalEditText: EditText

    private val repeatViewOrNull: RepeatedValuesView?

    val repeatView: RepeatedValuesView
        get() = repeatViewOrNull
            ?: error("RepeatedValuesView is not available on detached node input buffers")

    private val timestampTextView: TextView?

    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet) {
        inflate(context, R.layout.view_collect_input, this)
        originalEditText = findViewById(R.id.view_collect_input_edit_text)
        repeatViewOrNull = findViewById(R.id.view_collect_input_repeat_view)
        timestampTextView = findViewById(R.id.view_collect_input_timestamp_tv)
        timestampTextView?.setOnClickListener {
            (context as? CollectActivity)?.showObservationMetadataDialog()
        }
    }

    private constructor(context: Context, detached: Boolean) : super(context) {
        require(detached)
        detachedFromCollect = true
        inflate(context, R.layout.view_collect_input_detached, this)
        originalEditText = findViewById(R.id.view_collect_input_edit_text)
        repeatViewOrNull = null
        timestampTextView = null
    }

    companion object {
        /** In-memory buffer for [com.fieldbook.tracker.traits.NodeTraitValueSession]. */
        @JvmStatic
        fun createDetached(context: Context): CollectInputView =
            CollectInputView(context, detached = true)
    }

    /**
     * Called on load layout when no data exists for a given trait yet.
     * Changes the UI so only the add button is visible, instead of both edit text and add button.
     */
    fun prepareEmptyObservationsMode() {

        if (isRepeatEnabled()) {

            markObservationUnsaved()

            repeatView.prepareModeEmpty()

        } else {

            text = ""
            markObservationEdited()
        }

        updateTimestampDisplay(null)
    }

    fun prepareObservationsExistMode(models: List<ObservationModel>) {

        initialize(models)

        if (isRepeatEnabled()) {
            repeatView.prepareModeNonEmpty()
        }

        markObservationSaved()

        updateTimestampDisplay(models.maxByOrNull { it.rep.toInt() }?.observation_time_stamp)
    }

    /**
     * Re-fetches the current observation from the activity and refreshes the timestamp display.
     * Pass [null] as observation value guard: only shows the timestamp if the observation has
     * a non-empty value (hides for new empty reps).
     */
    fun refreshTimestamp() {
        val obs = (context as? CollectActivity)?.getCurrentObservation()
        val timestamp = if (obs != null && obs.value.isNotEmpty()) obs.observation_time_stamp else null
        updateTimestampDisplay(timestamp)
    }

    /**
     * Shows a small timestamp label below the input when the preference is enabled.
     * Hides the label when [timestamp] is null/empty or the preference is disabled.
     */
    fun updateTimestampDisplay(timestamp: String?) {
        val tv = timestampTextView ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val show = prefs.getBoolean(PreferenceKeys.SHOW_OBSERVATION_TIMESTAMP, false)
        when {
            !show -> tv.visibility = GONE
            timestamp.isNullOrEmpty() -> tv.visibility = INVISIBLE
            else -> {
                // Trim to "yyyy-MM-dd HH:mm:ss" by taking the first 19 characters
                val display = if (timestamp.length >= 19) timestamp.substring(0, 19) else timestamp
                tv.text = display
                tv.visibility = VISIBLE
            }
        }
    }

    fun initialize(models: List<ObservationModel>) {

        if (isRepeatEnabled()) {

            repeatView.initialize(models, forceInitialRep)

        } else {

            text = models.maxByOrNull { it.rep.toInt() }?.value ?: ""
            markObservationEdited()
        }
    }

    fun setOnEditorActionListener(listener: TextView.OnEditorActionListener) {
        editText.setOnEditorActionListener(listener)
    }

    fun getRep(): String = if (detachedFromCollect) "1" else repeatView.getRep()

    val editText: EditText
        get() = if (isRepeatEnabled()) {
            repeatView.getEditText() ?: originalEditText
        } else originalEditText.apply {
            if (!detachedFromCollect) {
                setOnLongClickListener {
                    (context as CollectActivity).showObservationMetadataDialog()
                    true
                }
            }
        }

    var text: String
        get() = if (isRepeatEnabled()) {
            repeatView.text
        } else editText.text.toString()

        set(value) {
            if (isRepeatEnabled()) {
                repeatView.text = value
            } else editText.setText(value)
        }

    fun isRepeatEnabled() =
        !detachedFromCollect && (context as? CollectActivity)?.currentTrait?.repeatedMeasures == true

    /**
     * Updates visibility of views based on current trait's repeatedMeasures
     */
    fun updateInputViewVisibility(visibility: Int) {
        if (isRepeatEnabled()) {
            repeatView.visibility = visibility
            originalEditText.visibility = GONE
        } else {
            repeatViewOrNull?.visibility = GONE
            originalEditText.visibility = visibility
            originalEditText.hint = ""
        }
    }

    /**
     * Set Text Color
     */
    fun setTextColor(value: Int) {
        if (isRepeatEnabled()) {
            repeatView.setTextColor(value)
        } else editText.setTextColor(value)
    }

    /**
     * Set current hint
     */
    fun setHint(hint: String) {
        if (!isRepeatEnabled()) editText.hint = hint
    }

    /**
     * Clears the current text
     */
    fun clear() {
        if (isRepeatEnabled()) {
            repeatView.text = ""
        } else editText.text.clear()
    }

    fun navigateToRep(rep: Int) {
        forceInitialRep = rep
    }

    fun getInitialIndex(): Int {
        return forceInitialRep
    }

    fun resetInitialIndex() {
        forceInitialRep = -1
    }

    fun getIsObservationSaved(): Boolean {
        return if (isRepeatEnabled()) {
            repeatView.isSelectedSaved()
        } else isObservationSaved
    }

    fun getSavedIds(): List<Int> =
        if (detachedFromCollect) emptyList() else repeatView.getSavedIds()

    fun markObservationUnsaved() {
        isObservationSaved = false
    }

    fun markObservationEdited() {
        isObservationSaved = false
        updateCurrentValueETStyle()
    }

    /**
     * Mark current input as saved and update styling
     */
    fun markObservationSaved() {
        isObservationSaved = true
        updateCurrentValueETStyle()
    }

    /**
     * Updates the text style based on saved/edited state
     */
    private fun updateCurrentValueETStyle() {
        val style = if (isObservationSaved) Typeface.BOLD else Typeface.ITALIC
        editText.setTypeface(null, style)
    }

}
