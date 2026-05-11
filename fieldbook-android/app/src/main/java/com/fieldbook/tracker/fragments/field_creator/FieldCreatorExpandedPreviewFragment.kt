package com.fieldbook.tracker.fragments.field_creator

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.material3.*
import androidx.compose.ui.platform.ComposeView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.FieldCreatorActivity
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.MutableLiveData
import com.fieldbook.tracker.enums.FieldCreationStep
import com.fieldbook.tracker.views.FieldPreviewGrid
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * Show an expanded field preview with an option to collapse the field
 *
 * Updates: createField()
 * Observes: fieldConfig and creationResult
 *
 * Go back to activity when field is created successfully
 */
class FieldCreatorExpandedPreviewFragment : FieldCreatorBaseFragment() {

    override fun getCurrentStep(): FieldCreationStep = FieldCreationStep.FIELD_PREVIEW
    override fun getLayoutResourceId(): Int = R.layout.fragment_field_creator_expanded_preview
    override fun onForwardClick(): (() -> Unit)? = null

    private lateinit var titleText: TextView
    private lateinit var fieldSummaryTv: TextView
    private lateinit var warningCard: MaterialCardView
    private lateinit var fieldGrid: ComposeView
    private lateinit var progressContainer: LinearLayout
    private lateinit var createFieldButton: ImageButton
    private lateinit var collapseViewFab: FloatingActionButton

    private val isExpanded = MutableLiveData(true)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? FieldCreatorActivity)?.hideStepperView()
    }

    override fun setupViews(view: View) {
        titleText = view.findViewById(R.id.expanded_title_text)
        fieldSummaryTv = view.findViewById(R.id.expanded_field_summary_text)
        warningCard = view.findViewById(R.id.expanded_warning_card)
        fieldGrid = view.findViewById(R.id.expanded_field_preview_grid)
        progressContainer = view.findViewById(R.id.expanded_progress_container)
        createFieldButton = view.findViewById(R.id.create_field_button)
        collapseViewFab = view.findViewById(R.id.collapse_view_fab)

        titleText.visibility = View.GONE
        fieldSummaryTv.visibility = View.GONE
        warningCard.visibility = View.GONE

        collapseViewFab.setOnClickListener {
            toggleExpandedView()
        }

        setupExpandedPreviewGrid()
    }

    override fun observeFieldCreatorViewModel() {
        setupFieldSummaryInfo(fieldSummaryTv)

        setupFieldCreationObserver(progressContainer, createFieldButton, collapseViewFab)

        fieldCreatorViewModel.fieldConfig.observe(viewLifecycleOwner) { state ->
            val currentExpanded = isExpanded.value != false
            if (!currentExpanded) {
                warningCard.visibility = if (state.isLargeField) View.VISIBLE else View.GONE
            }
        }
    }

    private fun setupExpandedPreviewGrid() {
        fieldGrid.setContent {
            MaterialTheme {
                val config by fieldCreatorViewModel.fieldConfig.observeAsState()
                val expanded by isExpanded.observeAsState(true)

                config?.let { state ->
                    FieldPreviewGrid(
                        config = state,
                        showPlotNumbers = true,
                        forceFullView = expanded,
                        onCollapsingStateChanged = null
                    )
                }
            }
        }
    }

    private fun toggleExpandedView() {
        val expandedState = isExpanded.value != false
        val newExpandedState = !expandedState
        isExpanded.value = newExpandedState

        if (newExpandedState) { // expanded view
            titleText.visibility = View.GONE
            fieldSummaryTv.visibility = View.GONE
            warningCard.visibility = View.GONE
            collapseViewFab.setImageResource(R.drawable.ic_arrow_collapse_all)
        } else { // collapsed view
            titleText.visibility = View.VISIBLE
            fieldSummaryTv.visibility = View.VISIBLE
            val state = fieldCreatorViewModel.fieldConfig.value
            warningCard.visibility = if (state?.isLargeField == true) View.VISIBLE else View.GONE
            collapseViewFab.setImageResource(R.drawable.ic_arrow_expand_all)
        }
    }
}