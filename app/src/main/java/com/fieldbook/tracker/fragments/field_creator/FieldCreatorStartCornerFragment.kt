package com.fieldbook.tracker.fragments.field_creator

import android.view.View
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.navigation.fragment.findNavController
import com.fieldbook.tracker.R
import com.fieldbook.tracker.viewmodels.FieldConfig
import com.fieldbook.tracker.enums.GridPreviewMode
import com.fieldbook.tracker.enums.FieldCreationStep
import com.fieldbook.tracker.views.FieldPreviewGrid
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.getValue

class FieldCreatorStartCornerFragment : FieldCreatorBaseFragment() {

    override fun getCurrentStep(): FieldCreationStep = FieldCreationStep.START_CORNER
    override fun getLayoutResourceId(): Int = R.layout.fragment_field_creator_start_point
    override fun onForwardClick(): (() -> Unit)? = {
        findNavController().navigate(FieldCreatorStartCornerFragmentDirections.actionFromStartPointToDirection())
    }

    private lateinit var fieldDimensionsText: TextView
    private lateinit var startPointContainer: ComposeView

    override fun setupViews(view: View) {
        fieldDimensionsText = view.findViewById(R.id.field_dimensions_text)
        startPointContainer = view.findViewById(R.id.start_point_container)
    }

    override fun observeFieldCreatorViewModel() {
        fieldCreatorViewModel.fieldConfig.observe(viewLifecycleOwner) { state ->
            setupGridPreview(state)

            val canProceed = state.rows > 0 && state.cols > 0 && state.startCorner != null
            updateForwardButtonState(canProceed)
        }
    }


    private fun setupGridPreview(state: FieldConfig) {
        fieldDimensionsText.text = getString(R.string.field_dimensions_format, state.rows, state.cols)

        startPointContainer.setContent {
            MaterialTheme {
                val referenceGridDimensions by fieldCreatorViewModel.referenceGridDimensions.observeAsState()

                FieldPreviewGrid(
                    config = state,
                    gridPreviewMode = GridPreviewMode.CORNER_SELECTION,
                    selectedCorner = state.startCorner,
                    onCornerSelected = { corner ->
                        fieldCreatorViewModel.updateStartCorner(corner)
                    },
                    showPlotNumbers = state.startCorner != null,
                    forceFullView = false,
                    useReferenceGridDimensions = referenceGridDimensions
                )
            }
        }
    }
}