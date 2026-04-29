package com.fieldbook.tracker.fragments.field_creator

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.FieldCreatorActivity
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.enums.FieldCreationStep
import com.fieldbook.tracker.enums.FieldStartCorner
import com.fieldbook.tracker.viewmodels.FieldConfig
import com.fieldbook.tracker.viewmodels.FieldCreationResult
import com.fieldbook.tracker.viewmodels.FieldCreatorViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * Serves as a base for all field creator fragments
 *
 * fieldCreatorViewModel: Shared activity-scoped instance
 *
 * Each fragment needs to:
 * - implement getCurrentStep() to update stepper UI
 * - observe relevant LiveData streams in observeFieldCreatorViewModel()
 * - manage navigation using the updateForwardButtonState() method to manage bottom arrow icons
 * - call onForwardClick() to define next step navigation
 */
abstract class FieldCreatorBaseFragment : Fragment() {

    protected val db by lazy { DataHelper(requireContext()) }

    protected val fieldCreatorViewModel: FieldCreatorViewModel by activityViewModels()

    // buttons for navigation between fragments
    protected var backButton: ImageButton? = null
    protected var forwardButton: ImageButton? = null

    protected abstract fun getCurrentStep(): FieldCreationStep
    protected abstract fun getLayoutResourceId(): Int
    protected abstract fun onForwardClick(): (() -> Unit)?

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(getLayoutResourceId(), container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        backButton = view.findViewById(R.id.back_button)
        forwardButton = view.findViewById(R.id.forward_button)

        updateActivityStepper()
        setupViews(view)
        setupNavigationButtons()

        observeFieldCreatorViewModel()
    }

    private fun updateActivityStepper() {
        fieldCreatorViewModel.updateCurrentStep(getCurrentStep())
    }

    protected abstract fun setupViews(view: View)

    protected open fun observeFieldCreatorViewModel() { }

    protected fun setupFieldSummaryInfo(fieldSummaryText: TextView) {
        fieldCreatorViewModel.fieldConfig.observe(viewLifecycleOwner) { state ->
            fieldSummaryText.text = String.format(
                getString(R.string.field_creator_preview_summary),
                state.fieldName,
                state.rows,
                state.cols
            )
        }
    }

    protected fun setupFieldCreationObserver(
        progressContainer: LinearLayout,
        createFieldButton: View,
        changeViewTypeFab: FloatingActionButton
    ) {
        createFieldButton.setOnClickListener {
            fieldCreatorViewModel.createField(db, context)
        }

        fieldCreatorViewModel.creationResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is FieldCreationResult.Loading -> {
                    progressContainer.visibility = View.VISIBLE
                    changeViewTypeFab.visibility = View.GONE
                    createFieldButton.let {
                        it.isEnabled = false
                        it.alpha = 0.5f
                    }

                    updateForwardButtonState(false)
                    updateBackButtonState(false)

                    (activity as FieldCreatorActivity).setCreationInProgress(true)
                }
                is FieldCreationResult.Success -> {
                    progressContainer.visibility = View.GONE
                    activity?.let { activity ->
                        val resultIntent = Intent().apply {
                            putExtra("fieldId", result.studyDbId)
                        }
                        activity.setResult(Activity.RESULT_OK, resultIntent)
                        activity.finish()
                    }
                }
                is FieldCreationResult.Error -> {
                    progressContainer.visibility = View.GONE
                    changeViewTypeFab.visibility = View.VISIBLE
                    createFieldButton.let {
                        it.isEnabled = true
                        it.alpha = 1.0f
                    }

                    updateForwardButtonState(true)
                    updateBackButtonState(true)

                    (activity as FieldCreatorActivity).setCreationInProgress(false)

                    Toast.makeText(context, "Failed to create field: ${result.message}", Toast.LENGTH_SHORT).show()
                }
                null -> {
                    // initial state - do nothing
                }
            }
        }
    }

    protected fun setupNavigationButtons() {
        backButton?.setOnClickListener {
            findNavController().popBackStack()
        }

        forwardButton?.let { button ->
            button.isEnabled = false
            button.alpha = 0.5f
            button.setOnClickListener {
                if (button.isEnabled) {
                    onForwardClick()?.invoke()
                }
            }
        }
    }

    protected fun updateForwardButtonState(enabled: Boolean) {
        forwardButton?.let { button ->
            button.isEnabled = enabled
            button.alpha = if (enabled) 1.0f else 0.5f
        }
    }

    private fun updateBackButtonState(enabled: Boolean) {
        backButton?.let { button ->
            button.isEnabled = enabled
            button.alpha = if (enabled) 1.0f else 0.5f
        }
    }

    protected fun getDirectionHighlight(config: FieldConfig): Set<Pair<Int, Int>> {
        val startCorner = config.startCorner ?: return emptySet()

        return when (config.isHorizontal) {
            true -> {
                // highlight first row from starting corner
                when (startCorner) {
                    FieldStartCorner.TOP_LEFT, FieldStartCorner.TOP_RIGHT -> {
                        (0 until config.cols).map { 0 to it }.toSet()
                    }
                    FieldStartCorner.BOTTOM_LEFT, FieldStartCorner.BOTTOM_RIGHT -> {
                        (0 until config.cols).map { (config.rows - 1) to it }.toSet()
                    }
                }
            }
            false -> {
                // highlight first column from starting corner
                when (startCorner) {
                    FieldStartCorner.TOP_LEFT, FieldStartCorner.BOTTOM_LEFT -> {
                        (0 until config.rows).map { it to 0 }.toSet()
                    }
                    FieldStartCorner.TOP_RIGHT, FieldStartCorner.BOTTOM_RIGHT -> {
                        (0 until config.rows).map { it to (config.cols - 1) }.toSet()
                    }
                }
            }
            null -> emptySet()
        }
    }
}