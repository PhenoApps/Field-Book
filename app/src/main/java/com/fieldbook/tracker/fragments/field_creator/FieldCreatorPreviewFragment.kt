package com.fieldbook.tracker.fragments.field_creator

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.material3.*
import androidx.compose.ui.platform.ComposeView
import androidx.navigation.fragment.findNavController
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.FieldCreatorActivity
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.getValue
import com.fieldbook.tracker.enums.FieldCreationStep
import com.fieldbook.tracker.views.FieldPreviewGrid
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * Show a collapsed field preview
 *
 * Updates: createField()
 * Observes: fieldConfig and creationResult
 *
 * If the field is too large, show a FAB to go to expanded preview
 *
 * Go back to activity when field is created successfully
 */
class FieldCreatorPreviewFragment : FieldCreatorBaseFragment() {

    override fun getCurrentStep(): FieldCreationStep = FieldCreationStep.FIELD_PREVIEW
    override fun getLayoutResourceId(): Int = R.layout.fragment_field_creator_preview
    override fun onForwardClick(): (() -> Unit)? = null

    private lateinit var fieldSummaryText: TextView
    private lateinit var warningCard: MaterialCardView
    private lateinit var fieldPreviewGrid: ComposeView
    private lateinit var progressContainer: LinearLayout
    private lateinit var createFieldButton: ImageButton
    private lateinit var expandViewFab: FloatingActionButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? FieldCreatorActivity)?.showStepperView() // if coming back from expanded preview
    }

    override fun setupViews(view: View) {
        fieldSummaryText = view.findViewById(R.id.field_summary_text)
        warningCard = view.findViewById(R.id.warning_card)
        fieldPreviewGrid = view.findViewById(R.id.field_preview_grid)
        progressContainer = view.findViewById(R.id.progress_container)
        expandViewFab = view.findViewById(R.id.expand_view_fab)
        createFieldButton = view.findViewById(R.id.create_field_button)

        setupExpandFab()

        setupPreviewGrid()
    }

    override fun observeFieldCreatorViewModel() {
        setupFieldSummaryInfo(fieldSummaryText)

        setupFieldCreationObserver(progressContainer, createFieldButton, expandViewFab)

        fieldCreatorViewModel.fieldConfig.observe(viewLifecycleOwner) { state ->
            warningCard.visibility = if (state.isLargeField) View.VISIBLE else View.GONE
        }
    }

    private fun setupExpandFab() {
        expandViewFab.setOnClickListener {
            findNavController().navigate(FieldCreatorPreviewFragmentDirections.actionFromPreviewToExpandedPreview())
        }
    }

    private fun setupPreviewGrid() {
        fieldPreviewGrid.setContent {
            MaterialTheme {
                val config by fieldCreatorViewModel.fieldConfig.observeAsState()

                config?.let { state ->
                    FieldPreviewGrid(
                        config = state,
                        showPlotNumbers = true,
                        forceFullView = false,
                        onCollapsingStateChanged = { needsCollapsing ->
                            // show expand button if grid can be expanded
                            expandViewFab.visibility =
                                if (needsCollapsing) View.VISIBLE else View.GONE
                        }
                    )
                }
            }
        }
    }
}