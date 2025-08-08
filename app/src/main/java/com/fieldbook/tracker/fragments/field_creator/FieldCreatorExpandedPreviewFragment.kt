package com.fieldbook.tracker.fragments.field_creator

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.compose.material3.*
import androidx.compose.ui.platform.ComposeView
import androidx.navigation.fragment.findNavController
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.FieldCreatorActivity
import com.fieldbook.tracker.views.FieldCreationStep
import com.fieldbook.tracker.views.FieldPreviewGrid
import com.google.android.material.floatingactionbutton.FloatingActionButton

class FieldCreatorExpandedPreviewFragment : FieldCreatorBaseFragment() {

    override fun getCurrentStep(): FieldCreationStep = FieldCreationStep.FIELD_PREVIEW
    override fun getLayoutResourceId(): Int = R.layout.fragment_field_creator_expanded_preview
    override fun onForwardClick(): (() -> Unit)? = null

    private lateinit var fieldGrid: ComposeView
    private lateinit var progressContainer: LinearLayout
    private lateinit var createFieldButton: ImageButton
    private lateinit var collapseViewFab: FloatingActionButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? FieldCreatorActivity)?.hideStepperView()
    }

    override fun setupViews(view: View) {
        fieldGrid = view.findViewById(R.id.expanded_field_preview_grid)
        progressContainer = view.findViewById(R.id.expanded_progress_container)
        createFieldButton = view.findViewById(R.id.create_field_button)
        collapseViewFab = view.findViewById(R.id.collapse_view_fab)

        collapseViewFab.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    override fun observeFieldCreatorViewModel() {
        setupFieldCreationObserver(progressContainer, createFieldButton)

        fieldCreatorViewModel.fieldConfig.observe(viewLifecycleOwner) { state ->
            setupExpandedPreviewGrid(state)
        }
    }

    private fun setupExpandedPreviewGrid(state: com.fieldbook.tracker.viewmodels.FieldConfig) {
        fieldGrid.setContent {
            MaterialTheme {
                FieldPreviewGrid(
                    config = state,
                    showPlotNumbers = true,
                    forceFullView = true,
                    onCollapsingStateChanged = null
                )
            }
        }
    }
}