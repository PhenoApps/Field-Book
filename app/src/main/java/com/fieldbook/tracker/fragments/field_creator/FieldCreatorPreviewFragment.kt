package com.fieldbook.tracker.fragments.field_creator

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.material3.*
import androidx.compose.ui.platform.ComposeView
import androidx.navigation.fragment.findNavController
import com.fieldbook.tracker.R
import com.fieldbook.tracker.views.FieldCreationStep
import com.fieldbook.tracker.views.FieldPreviewGrid
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class FieldCreatorPreviewFragment : FieldCreatorBaseFragment() {

    override fun getCurrentStep(): FieldCreationStep = FieldCreationStep.FIELD_PREVIEW
    override fun getLayoutResourceId(): Int = R.layout.fragment_field_creator_preview

    private lateinit var fieldSummaryText: TextView
    private lateinit var warningCard: MaterialCardView
    private lateinit var fieldPreviewGrid: ComposeView
    private lateinit var progressContainer: LinearLayout
    private lateinit var createFieldButton: MaterialButton
    private lateinit var expandViewButton: MaterialButton

    override fun setupViews(view: View) {
        fieldSummaryText = view.findViewById(R.id.field_summary_text)
        warningCard = view.findViewById(R.id.warning_card)
        fieldPreviewGrid = view.findViewById(R.id.field_preview_grid)
        progressContainer = view.findViewById(R.id.progress_container)
        createFieldButton = view.findViewById(R.id.create_field_button)
        expandViewButton = view.findViewById(R.id.expand_view_button)

        setupExpandButton()
    }

    override fun observeFieldCreatorViewModel() {
        setupFieldSummaryInfo(fieldSummaryText)

        setupFieldCreationObserver(progressContainer, createFieldButton)

        fieldCreatorViewModel.fieldConfig.observe(viewLifecycleOwner) { state ->
            warningCard.visibility = if (state.isLargeField) View.VISIBLE else View.GONE
            setupPreviewGrid(state)
        }
    }

    private fun setupExpandButton() {
        expandViewButton.setOnClickListener {
            findNavController().navigate(FieldCreatorPreviewFragmentDirections.actionFromPreviewToExpandedPreview())
        }
    }

    private fun setupPreviewGrid(state: com.fieldbook.tracker.viewmodels.FieldConfig) {
        fieldPreviewGrid.setContent {
            MaterialTheme {
                FieldPreviewGrid(
                    config = state,
                    showPlotNumbers = true,
                    showHeaders = false,
                    forceFullView = false,
                    onCollapsingStateChanged = { needsCollapsing ->
                        // show expand button if grid can be expanded
                        expandViewButton.visibility = if (needsCollapsing) View.VISIBLE else View.GONE
                    }
                )
            }
        }
    }
}