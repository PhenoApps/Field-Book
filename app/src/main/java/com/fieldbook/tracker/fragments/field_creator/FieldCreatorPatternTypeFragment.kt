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

class FieldCreatorPatternTypeFragment : FieldCreatorBaseFragment() {

    override fun getCurrentStep(): FieldCreationStep = FieldCreationStep.WALKING_PATTERN
    override fun getLayoutResourceId(): Int = R.layout.fragment_field_creator_pattern_type
    override fun onForwardClick(): (() -> Unit)? = {
        findNavController().navigate(FieldCreatorPatternTypeFragmentDirections.actionFromPatternTypeToPreview())
    }

    private lateinit var patternRadioGroup: RadioGroup
    private lateinit var radioLinear: RadioButton
    private lateinit var radioZigzag: RadioButton
    private lateinit var linearContainer: LinearLayout
    private lateinit var zigzagContainer: LinearLayout
    private lateinit var patternPreviewContainer: ComposeView

    override fun setupViews(view: View) {
        patternRadioGroup = view.findViewById(R.id.pattern_radio_group)
        radioLinear = view.findViewById(R.id.radio_linear)
        radioZigzag = view.findViewById(R.id.radio_zigzag)
        linearContainer = view.findViewById(R.id.linear_container)
        zigzagContainer = view.findViewById(R.id.zigzag_container)
        patternPreviewContainer = view.findViewById(R.id.pattern_preview_container)

        setupClickListeners()
    }

    override fun observeFieldCreatorViewModel() {
        fieldCreatorViewModel.fieldConfig.observe(viewLifecycleOwner) { state ->
            updateRadioButtons(state.isZigzag)

            val isForwardEnabled = state.isZigzag != null
            updateForwardButtonState(isForwardEnabled)

            updatePatternPreview(state)
        }
    }

    private fun setupClickListeners() {
        patternRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                radioLinear.id -> fieldCreatorViewModel.updatePatternType(false)
                radioZigzag.id -> fieldCreatorViewModel.updatePatternType(true)
                RadioGroup.NO_ID -> {
                    // initial state
                }
            }
        }

        linearContainer.setOnClickListener {
            patternRadioGroup.check(radioLinear.id)
        }

        zigzagContainer.setOnClickListener {
            patternRadioGroup.check(radioZigzag.id)
        }
    }

    private fun updateRadioButtons(isZigzag: Boolean?) {
        when (isZigzag) {
            true -> patternRadioGroup.check(radioZigzag.id)
            false -> patternRadioGroup.check(radioLinear.id)
            null -> patternRadioGroup.clearCheck()
        }
    }

    private fun updatePatternPreview(config: FieldConfig) {
        if (config.rows <= 0 || config.cols <= 0 || config.startCorner == null || config.isHorizontal == null) return

        patternPreviewContainer.setContent {
            MaterialTheme {
                if (config.rows > 0 && config.cols > 0 && config.isZigzag != null) {
                    FieldPreviewGrid(
                        config = config,
                        previewMode = PreviewMode.PATTERN_PREVIEW,
                        showPlotNumbers = true,
                        forceFullView = false,
                        highlightedCells = getPatternHighlight(config)
                    )
                }
            }
        }
    }

    private fun getPatternHighlight(config: FieldConfig): Set<Pair<Int, Int>> {
        val startCorner = config.startCorner ?: return emptySet()
        if (config.isHorizontal == null) return emptySet() // Remove the zigzag check

        return when (config.isHorizontal) {
            true -> {
                // highlight first two rows from starting corner
                when (startCorner) {
                    FieldStartCorner.TOP_LEFT, FieldStartCorner.TOP_RIGHT -> {
                        val firstRow = (0 until config.cols).map { 0 to it }
                        val secondRow = if (config.rows > 1) {
                            (0 until config.cols).map { 1 to it }
                        } else emptyList()
                        (firstRow + secondRow).toSet()
                    }
                    FieldStartCorner.BOTTOM_LEFT, FieldStartCorner.BOTTOM_RIGHT -> {
                        val firstRow = (0 until config.cols).map { (config.rows - 1) to it }
                        val secondRow = if (config.rows > 1) {
                            (0 until config.cols).map { (config.rows - 2) to it }
                        } else emptyList()
                        (firstRow + secondRow).toSet()
                    }
                }
            }
            false -> {
                // highlight first two columns from starting corner
                when (startCorner) {
                    FieldStartCorner.TOP_LEFT, FieldStartCorner.BOTTOM_LEFT -> {
                        val firstCol = (0 until config.rows).map { it to 0 }
                        val secondCol = if (config.cols > 1) {
                            (0 until config.rows).map { it to 1 }
                        } else emptyList()
                        (firstCol + secondCol).toSet()
                    }
                    FieldStartCorner.TOP_RIGHT, FieldStartCorner.BOTTOM_RIGHT -> {
                        val firstCol = (0 until config.rows).map { it to (config.cols - 1) }
                        val secondCol = if (config.cols > 1) {
                            (0 until config.rows).map { it to (config.cols - 2) }
                        } else emptyList()
                        (firstCol + secondCol).toSet()
                    }
                }
            }
        }
    }
}