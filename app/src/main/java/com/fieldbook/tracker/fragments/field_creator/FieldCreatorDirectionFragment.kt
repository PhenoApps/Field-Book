package com.fieldbook.tracker.fragments.field_creator

import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.navigation.fragment.findNavController
import com.fieldbook.tracker.R
import com.fieldbook.tracker.enums.FieldCreationStep
import com.fieldbook.tracker.enums.GridPreviewMode
import com.fieldbook.tracker.views.FieldPreviewGrid

/**
 * Sets the walking direction to follow: horizontal or vertical
 *
 * Updates: fieldConfig.isHorizontal
 * Observes: fieldConfig and referenceGridDimensions
 *
 * The forward button is enabled once user makes a choice
 */
class FieldCreatorDirectionFragment : FieldCreatorBaseFragment() {

    override fun getCurrentStep(): FieldCreationStep = FieldCreationStep.WALKING_DIRECTION
    override fun getLayoutResourceId(): Int = R.layout.fragment_field_creator_direction
    override fun onForwardClick(): (() -> Unit)? = {
        findNavController().navigate(FieldCreatorDirectionFragmentDirections.actionFromDirectionToPattern())
    }

    private lateinit var directionRadioGroup: RadioGroup
    private lateinit var radioHorizontal: RadioButton
    private lateinit var radioVertical: RadioButton
    private lateinit var horizontalContainer: LinearLayout
    private lateinit var verticalContainer: LinearLayout
    private lateinit var directionPreviewContainer: ComposeView

    override fun setupViews(view: View) {
        directionRadioGroup = view.findViewById(R.id.direction_radio_group)
        radioHorizontal = view.findViewById(R.id.radio_horizontal)
        radioVertical = view.findViewById(R.id.radio_vertical)
        horizontalContainer = view.findViewById(R.id.horizontal_container)
        verticalContainer = view.findViewById(R.id.vertical_container)
        directionPreviewContainer = view.findViewById(R.id.direction_preview_container)

        setupClickListeners()

        updateDirectionPreview()
    }

    override fun observeFieldCreatorViewModel() {
        fieldCreatorViewModel.fieldConfig.observe(viewLifecycleOwner) { state ->
            updateRadioButtons(state.isHorizontal)

            val isForwardEnabled = state.isHorizontal != null
            updateForwardButtonState(isForwardEnabled)
        }
    }

    private fun setupClickListeners() {
        directionRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                radioHorizontal.id -> fieldCreatorViewModel.updateDirection(true)
                radioVertical.id -> fieldCreatorViewModel.updateDirection(false)
                RadioGroup.NO_ID -> {
                    // initial state
                }
            }
        }

        horizontalContainer.setOnClickListener {
            directionRadioGroup.check(radioHorizontal.id)
        }
        verticalContainer.setOnClickListener {
            directionRadioGroup.check(radioVertical.id)
        }
    }

    private fun updateRadioButtons(isHorizontal: Boolean?) {
        when (isHorizontal) {
            true -> directionRadioGroup.check(radioHorizontal.id)
            false -> directionRadioGroup.check(radioVertical.id)
            null -> directionRadioGroup.clearCheck()
        }
    }

    private fun updateDirectionPreview() {
        directionPreviewContainer.setContent {
            MaterialTheme {
                val config by fieldCreatorViewModel.fieldConfig.observeAsState()
                val referenceGridDimensions by fieldCreatorViewModel.referenceGridDimensions.observeAsState()

                config?.let { state ->
                    FieldPreviewGrid(
                        config = state,
                        gridPreviewMode = GridPreviewMode.DIRECTION_PREVIEW,
                        selectedCorner = state.startCorner,
                        showPlotNumbers = true,
                        forceFullView = false,
                        highlightedCells = getDirectionHighlight(state),
                        useReferenceGridDimensions = referenceGridDimensions
                    )
                }
            }
        }
    }
}