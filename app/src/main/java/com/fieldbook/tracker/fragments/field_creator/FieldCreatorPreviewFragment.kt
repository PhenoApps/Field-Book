package com.fieldbook.tracker.fragments.field_creator

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.compose.material3.*
import androidx.compose.ui.platform.ComposeView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.viewmodels.FieldCreationResult
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

    override fun setupViews(view: View) {
        fieldSummaryText = view.findViewById(R.id.field_summary_text)
        warningCard = view.findViewById(R.id.warning_card)
        fieldPreviewGrid = view.findViewById(R.id.field_preview_grid)
        progressContainer = view.findViewById(R.id.progress_container)
        createFieldButton = view.findViewById(R.id.create_field_button)

        setupCreateButton()
    }

    override fun observeFieldCreatorViewModel() {
        fieldCreatorViewModel.fieldConfig.observe(viewLifecycleOwner) { state ->
            fieldSummaryText.text = String.format(getString(R.string.field_creator_preview_summary), state.fieldName, state.rows, state.cols)

            warningCard.visibility = if (state.isLargeField) View.VISIBLE else View.GONE

            setupPreviewGrid(state)
        }

        fieldCreatorViewModel.creationResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is FieldCreationResult.Loading -> {
                    progressContainer.visibility = View.VISIBLE
                    createFieldButton.isEnabled = false
                }
                is FieldCreationResult.Success -> {
                    progressContainer.visibility = View.GONE
                    activity?.let { activity ->
                        val resultIntent = Intent().apply {
                            putExtra("fieldId", result.studyDbId)
                        }
                        activity.setResult(Activity.RESULT_OK, resultIntent)
                        activity.finish()
                    }
                }
                is FieldCreationResult.Error -> {
                    progressContainer.visibility = View.GONE
                    createFieldButton.isEnabled = true
                    Toast.makeText(context, "Failed to create field: ${result.message}", Toast.LENGTH_SHORT).show()
                }
                null -> {
                    // initial state - do nothing
                }
            }
        }
    }

    private fun setupCreateButton() {
        createFieldButton.setOnClickListener {
            fieldCreatorViewModel.createField(db, context)
        }
    }

    private fun setupPreviewGrid(state: com.fieldbook.tracker.viewmodels.FieldConfig) {
        fieldPreviewGrid.setContent {
            MaterialTheme {
                FieldPreviewGrid(
                    config = state,
                    showPlotNumbers = true,
                    height = 400f,
                    showHeaders = false
                )
            }
        }
    }
}