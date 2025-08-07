package com.fieldbook.tracker.fragments.field_creator

import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.navigation.fragment.findNavController
import com.fieldbook.tracker.R
import com.fieldbook.tracker.utilities.FieldStartCorner
import com.fieldbook.tracker.viewmodels.FieldConfig
import com.fieldbook.tracker.viewmodels.PreviewMode
import com.fieldbook.tracker.views.FieldCreationStep
import com.fieldbook.tracker.views.FieldPreviewGrid

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
    }

    override fun observeFieldCreatorViewModel() {
        fieldCreatorViewModel.fieldConfig.observe(viewLifecycleOwner) { state ->
            updateRadioButtons(state.isHorizontal)

            val isForwardEnabled = state.isHorizontal != null
            updateForwardButtonState(isForwardEnabled)

            updateDirectionPreview(state)
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

    private fun updateDirectionPreview(config: FieldConfig) {
        if (config.rows <= 0 || config.cols <= 0) return

        directionPreviewContainer.setContent {
            MaterialTheme {
                if (config.rows > 0 && config.cols > 0) {
                    FieldPreviewGrid(
                        config = config,
                        previewMode = PreviewMode.DIRECTION_PREVIEW,
                        showPlotNumbers = config.isHorizontal != null && config.startCorner != null,
                        forceFullView = false,
                        highlightedCells = getDirectionHighlight(config),
                        maxDisplayPercentage = 0.95f
                    )
                }
            }
        }
    }

    private fun getDirectionHighlight(config: FieldConfig): Set<Pair<Int, Int>> {
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