package com.fieldbook.tracker.fragments.field_creator

import android.view.View
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.navigation.fragment.findNavController
import com.fieldbook.tracker.R
import com.fieldbook.tracker.viewmodels.FieldConfig
import com.fieldbook.tracker.views.FieldCreationStep
import com.fieldbook.tracker.views.FieldGrid
import com.google.android.material.button.MaterialButton

class FieldCreatorStartCornerFragment : FieldCreatorBaseFragment() {

    override fun getCurrentStep(): FieldCreationStep = FieldCreationStep.START_POINT
    override fun getLayoutResourceId(): Int = R.layout.fragment_field_creator_start_point

    private lateinit var fieldDimensionsText: TextView
    private lateinit var startPointContainer: ComposeView
    private lateinit var nextButton: MaterialButton

    override fun setupViews(view: View) {
        fieldDimensionsText = view.findViewById(R.id.field_dimensions_text)
        startPointContainer = view.findViewById(R.id.start_point_container)
        nextButton = view.findViewById(R.id.next_button)

        setupClickListeners()
    }

    override fun observeViewModel() {
        fieldCreatorViewModel.fieldConfig.observe(viewLifecycleOwner) { state ->
            setupGridPreview(state)

            nextButton.isEnabled = state.rows > 0 && state.cols > 0
        }
    }

    private fun setupClickListeners() {
        nextButton.setOnClickListener {
            findNavController().navigate(
                FieldCreatorStartCornerFragmentDirections.actionFromStartPointToPatternType()
            )
        }
    }

    private fun setupGridPreview(state: FieldConfig) {
        fieldDimensionsText.text = getString(R.string.field_dimensions_format, state.rows, state.cols)

        startPointContainer.setContent {
            MaterialTheme {
                FieldGrid(
                    rows = state.rows,
                    cols = state.cols,
                    showCornerButtons = true,
                    selectedCorner = state.startCorner,
                    onCornerSelected = { corner ->
                        fieldCreatorViewModel.updateStartCorner(corner)
                    }
                )
            }
        }
    }
}