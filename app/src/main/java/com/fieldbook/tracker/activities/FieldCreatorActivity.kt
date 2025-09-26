package com.fieldbook.tracker.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.navigation.NavController
import androidx.navigation.findNavController
import com.fieldbook.tracker.R
import com.fieldbook.tracker.enums.FieldCreationStep
import com.fieldbook.tracker.fragments.field_creator.FieldCreatorDirectionFragmentDirections
import com.fieldbook.tracker.fragments.field_creator.FieldCreatorPatternTypeFragmentDirections
import com.fieldbook.tracker.fragments.field_creator.FieldCreatorSizeFragmentDirections
import com.fieldbook.tracker.fragments.field_creator.FieldCreatorStartCornerFragmentDirections
import com.fieldbook.tracker.utilities.Utils
import com.fieldbook.tracker.viewmodels.FieldConfig
import com.fieldbook.tracker.viewmodels.FieldCreatorViewModel
import com.fieldbook.tracker.views.FieldCreatorStepper

/**
 * Activity consists of toolbar, stepper UI, and fragment container
 *
 * Manages:
 * - stepper UI to show current progress and allows step navigation
 * - step validation to prevent skipping incomplete steps
 * - exit confirmation when user taps on toolbar back button
 */
class FieldCreatorActivity : ThemedActivity() {

    companion object {
        fun getIntent(context: Context): Intent = Intent(context, FieldCreatorActivity::class.java)
    }

    private var isCreatingField = false // allows cancelling of field creation once task is initiated
    private lateinit var onBackPressedCallback: OnBackPressedCallback

    private val fieldCreatorViewModel: FieldCreatorViewModel by viewModels()
    private lateinit var stepperView: ComposeView

    private val navController: NavController by lazy {
        findNavController(R.id.field_creator_nav_host)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupBackCallback()

        setContentView(R.layout.activity_field_creator)

        val toolbar = findViewById<Toolbar>(R.id.field_creator_toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.apply {
            title = getString(R.string.field_creator_activity_toolbar_title)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setHomeButtonEnabled(true)
        }

        stepperView = findViewById(R.id.field_creator_stepper)
        setupStepper(getCurrentStepFromViewModel())
        observeForStepper()
    }

    fun setCreationInProgress(inProgress: Boolean) {
        isCreatingField = inProgress

        // enable interception when creation is active
        onBackPressedCallback.isEnabled = inProgress

        stepperView.isEnabled = !inProgress
        stepperView.alpha = if (inProgress) 0.5f else 1.0f
    }


    private fun setupStepper(step: FieldCreationStep) {
        stepperView.setContent {
            MaterialTheme {
                FieldCreatorStepper(
                    currentStep = step,
                    onStepClicked = { clickedStep ->
                        handleStepClick(clickedStep)
                    }
                )
            }
        }
    }

    private fun observeForStepper() {
        fieldCreatorViewModel.currentStep.observe(this) { step ->
            setupStepper(step)
        }
    }

    fun hideStepperView() {
        stepperView.visibility = View.GONE
    }

    fun showStepperView() {
        stepperView.visibility = View.VISIBLE
    }

    private fun getCurrentStepFromViewModel(): FieldCreationStep {
        return fieldCreatorViewModel.currentStep.value ?: FieldCreationStep.FIELD_SIZE
    }

    /**
     * Enables forward and backward navigation on pressing the stepper icons
     *
     * The viewmodel saves the user selected state at each step
     * Forward navigation is only possible until the furthest step UNTIL the pressed icon
     */
    private fun handleStepClick(clickedStep: FieldCreationStep) {
        if (isCreatingField) { // disabled navigation when field creation in progress
            return
        }

        val currentStep = getCurrentStepFromViewModel()
        val furthestPossibleStep = findNextAvailableStep()

        // if clicking on current step, do nothing
        if (clickedStep == currentStep) return

        // if clicking on a past step, navigate back
        if (clickedStep.position < currentStep.position) {
            navigateBackToStep(clickedStep)
            return
        } else { // going forward
            if (clickedStep.position <= furthestPossibleStep.position) {
                // allowed to go directly to the tapped step
                navigateForwardToStep(clickedStep)
            } else {
                // only go as far as we can
                navigateForwardToStep(furthestPossibleStep)
            }
        }

    }

    private fun isStepComplete(step: FieldCreationStep, config: FieldConfig?): Boolean {
        return when (step) {
            FieldCreationStep.FIELD_SIZE -> {
                config != null && config.fieldName.isNotBlank() && config.rows > 0 && config.cols > 0
            }
            FieldCreationStep.START_CORNER -> {
                config != null && config.startCorner != null
            }
            FieldCreationStep.WALKING_DIRECTION -> {
                config != null && config.isHorizontal != null
            }
            FieldCreationStep.WALKING_PATTERN -> {
                config != null && config.isZigzag != null
            }
            FieldCreationStep.FIELD_PREVIEW -> true
            else -> false
        }
    }

    private fun findNextAvailableStep(): FieldCreationStep {
        val fieldConfig = fieldCreatorViewModel.fieldConfig.value

        return when {
            !isStepComplete(FieldCreationStep.FIELD_SIZE, fieldConfig) -> FieldCreationStep.FIELD_SIZE
            !isStepComplete(FieldCreationStep.START_CORNER, fieldConfig) -> FieldCreationStep.START_CORNER
            !isStepComplete(FieldCreationStep.WALKING_DIRECTION, fieldConfig) -> FieldCreationStep.WALKING_DIRECTION
            !isStepComplete(FieldCreationStep.WALKING_PATTERN, fieldConfig) -> FieldCreationStep.WALKING_PATTERN
            else -> FieldCreationStep.FIELD_PREVIEW
        }
    }

    private fun navigateBackToStep(targetStep: FieldCreationStep) {
        val targetFragmentId = when (targetStep) {
            FieldCreationStep.FIELD_SIZE -> R.id.field_size_fragment
            FieldCreationStep.START_CORNER -> R.id.start_point_fragment
            FieldCreationStep.WALKING_DIRECTION -> R.id.direction_fragment
            FieldCreationStep.WALKING_PATTERN -> R.id.pattern_type_fragment
            FieldCreationStep.FIELD_PREVIEW -> R.id.preview_fragment
            else -> return
        }

        navController.popBackStack(targetFragmentId, false)
    }

    private fun navigateForwardToStep(targetStep: FieldCreationStep) {
        val currentStep = getCurrentStepFromViewModel()

        var step = currentStep
        while (step.position < targetStep.position) {
            // navigate to next fragment and update the step
            step = when (step) {
                FieldCreationStep.FIELD_SIZE -> {
                    navController.navigate(FieldCreatorSizeFragmentDirections.actionFromSizeToStartCorner())
                    FieldCreationStep.START_CORNER
                }
                FieldCreationStep.START_CORNER -> {
                    navController.navigate(FieldCreatorStartCornerFragmentDirections.actionFromStartPointToDirection())
                    FieldCreationStep.WALKING_DIRECTION
                }
                FieldCreationStep.WALKING_DIRECTION -> {
                    navController.navigate(FieldCreatorDirectionFragmentDirections.actionFromDirectionToPattern())
                    FieldCreationStep.WALKING_PATTERN
                }
                FieldCreationStep.WALKING_PATTERN -> {
                    navController.navigate(FieldCreatorPatternTypeFragmentDirections.actionFromPatternTypeToPreview())
                    FieldCreationStep.FIELD_PREVIEW
                }
                else -> step
            }
        }
    }


    override fun onSupportNavigateUp(): Boolean {
        if (isCreatingField) {
            showCreationMessage()
            return true
        }

        val currentStep = getCurrentStepFromViewModel()

        if (currentStep.position == FieldCreationStep.FIELD_SIZE.position) { // if on first fragment, exit
            finish()
            return true
        } else { // show warning before exiting
            showExitWarningDialog()
            return true
        }
    }

    private fun setupBackCallback() {
        onBackPressedCallback = object : OnBackPressedCallback(false) { // initially don't intercept
            override fun handleOnBackPressed() {
                showCreationMessage()
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    private fun showExitWarningDialog() {
        AlertDialog.Builder(this, R.style.AppAlertDialog)
            .setTitle(getString(R.string.field_creator_exit_dialog_title))
            .setMessage(getString(R.string.field_creator_exit_dialog_message))
            .setPositiveButton(getString(R.string.dialog_exit)) { _, _ ->
                finish()
            }
            .setNegativeButton(getString(R.string.dialog_cancel)) { d, _ ->
                d.dismiss()
            }
            .show()
    }

    private fun showCreationMessage() {
        Utils.makeToast(this, getString(R.string.field_creator_creation_in_progress))
    }
}