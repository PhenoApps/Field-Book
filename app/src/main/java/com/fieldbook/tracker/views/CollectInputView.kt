package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import android.widget.EditText
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.preferences.GeneralKeys

/**
 * View that contains the default fb edit text and the repeated values view feature.
 * This class delegates the implementation based on the settings.
 */
class CollectInputView(context: Context, attributeSet: AttributeSet) : ConstraintLayout(context, attributeSet) {

    //used to initialize the selected value with an index (mainly used for navigation)
    var forceInitialRep = -1

    var hasData: Boolean = false

    private val originalEditText: EditText

    val repeatView: RepeatedValuesView

    //get the current mode from settings
    private val repeatModeFlag = PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(GeneralKeys.REPEATED_VALUES_PREFERENCE_KEY, false)

    init {

        inflate(context, R.layout.view_collect_input, this)

        originalEditText = findViewById(R.id.view_collect_input_edit_text)
        repeatView = findViewById(R.id.view_collect_input_repeat_view)
    }

    /**
     * Called on load layout when no data exists for a given trait yet.
     * Changes the UI so only the add button is visible, instead of both edit text and add button.
     */
    fun prepareEmptyObservationsMode() {

        if (isRepeatEnabled()) {

            repeatView.prepareModeEmpty()

        } else {

            text = ""
        }
    }

    fun prepareObservationsExistMode(models: List<ObservationModel>) {

        initialize(models)

        repeatView.prepareModeNonEmpty()
    }

    fun initialize(models: List<ObservationModel>) {

        if (isRepeatEnabled()) {

            repeatView.initialize(models, forceInitialRep)

        } else {

            text = models.minByOrNull { it.rep.toInt() }?.value ?: ""
        }
    }

    fun setOnEditorActionListener(listener: TextView.OnEditorActionListener) {
        editText.setOnEditorActionListener(listener)
    }

    fun getRep(): String = repeatView.getRep()

    val editText: EditText
        get() = if (isRepeatEnabled()) {
            repeatView.getEditText() ?: originalEditText
        } else originalEditText

    var text: String
        get() = if (isRepeatEnabled()) {
            repeatView.text
        } else editText.text.toString()

        set(value) {
            if (isRepeatEnabled()) {
                repeatView.text = value
            } else editText.setText(value)
        }

    fun isRepeatEnabled() = repeatModeFlag

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
            repeatView.clear()
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
}