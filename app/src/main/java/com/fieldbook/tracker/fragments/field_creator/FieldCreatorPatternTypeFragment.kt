package com.fieldbook.tracker.fragments.field_creator

import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.navigation.fragment.findNavController
import com.fieldbook.tracker.R
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.getValue
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
        patternPreviewContainer.setContent {
            MaterialTheme {
                val referenceGridDimensions by fieldCreatorViewModel.referenceGridDimensions.observeAsState()

                FieldPreviewGrid( // show directional preview until user makes a choice
                    config = config,
                    previewMode = if (config.isZigzag != null) PreviewMode.PATTERN_PREVIEW else PreviewMode.DIRECTION_PREVIEW,
                    selectedCorner = config.startCorner,
                    showPlotNumbers = true,
                    forceFullView = false,
                    highlightedCells = if (config.isZigzag != null) getPatternHighlight(config) else getDirectionHighlight(config),
                    useReferenceGridDimensions = referenceGridDimensions
                )
            }
        }
    }

    private fun getPatternHighlight(config: FieldConfig): Set<Pair<Int, Int>> {
        // highlight all cells
        return (0 until config.rows).flatMap { row ->
            (0 until config.cols).map { col ->
                row to col
            }
        }.toSet()
    }
}