package com.fieldbook.tracker.fragments.field_creator

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.fieldbook.tracker.R
import com.fieldbook.tracker.enums.FieldCreationStep
import com.fieldbook.tracker.viewmodels.FieldConfig
import com.fieldbook.tracker.views.FieldPreviewGrid
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Sets field name and dimensions
 *
 * Updates:
 * - fieldConfig.fieldName, fieldConfig.rows, fieldConfig.cols
 * - also updates referenceGridDimensions in viewmodel so that other fragments show the same rows/cols
 *
 * Observes: validation errors and field config
 *
 * The forward button is enabled/clickable by default to validate and show errors to the user
 */
class FieldCreatorSizeFragment : FieldCreatorBaseFragment() {

    override fun getCurrentStep(): FieldCreationStep = FieldCreationStep.FIELD_SIZE
    override fun getLayoutResourceId(): Int = R.layout.fragment_field_creator_size
    override fun onForwardClick(): (() -> Unit)? = {
        if (fieldCreatorViewModel.validateNameAndDimensions(db, context)) {
            val state = fieldCreatorViewModel.fieldConfig.value
            state?.let {
                dismissKeyboard()
                findNavController().navigate(FieldCreatorSizeFragmentDirections.actionFromSizeToStartCorner())
            }
        }
    }

    private lateinit var fieldNameEditText: TextInputEditText
    private lateinit var rowsEditText: TextInputEditText
    private lateinit var colsEditText: TextInputEditText
    private lateinit var fieldNameInputLayout: TextInputLayout
    private lateinit var rowsInputLayout: TextInputLayout
    private lateinit var colsInputLayout: TextInputLayout
    private lateinit var sizePreviewGrid: ComposeView
    private lateinit var fieldDimensionsErrorText: TextView
    private lateinit var largeFieldWarningCard: MaterialCardView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setBackButtonToolbar()
    }

    private fun setBackButtonToolbar() {
        activity?.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        activity?.finish()
                        true
                    }
                    else -> false
                }
            }

        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun setupViews(view: View) {
        fieldNameEditText = view.findViewById(R.id.field_name_edit_text)
        rowsEditText = view.findViewById(R.id.rows_edit_text)
        colsEditText = view.findViewById(R.id.cols_edit_text)
        fieldNameInputLayout = view.findViewById(R.id.field_name_input_layout)
        rowsInputLayout = view.findViewById(R.id.rows_input_layout)
        colsInputLayout = view.findViewById(R.id.cols_input_layout)
        sizePreviewGrid = view.findViewById(R.id.size_preview_container)
        fieldDimensionsErrorText = view.findViewById(R.id.field_dimensions_error_text)
        largeFieldWarningCard = view.findViewById(R.id.large_field_warning_card)

        setupTextWatchers()

        updateSizePreview()
    }

    override fun observeFieldCreatorViewModel() {
        updateForwardButtonState(true) // enable click on forward button to show validation errors

        fieldCreatorViewModel.fieldConfig.observe(viewLifecycleOwner) { state ->
            if (fieldNameEditText.text.toString() != state.fieldName) {
                fieldNameEditText.setText(state.fieldName)
            }
            if (rowsEditText.text.toString() != state.rows.toString() && state.rows > 0) {
                rowsEditText.setText(state.rows.toString())
                rowsEditText.setSelection(rowsEditText.text?.length ?: 0) // keep cursor at the end in case of errors
            }
            if (colsEditText.text.toString() != state.cols.toString() && state.cols > 0) {
                colsEditText.setText(state.cols.toString())
                colsEditText.setSelection(colsEditText.text?.length ?: 0) // keep cursor at the end in case of errors
            }

            // show large field card if total plots <= max
            val totalPlots = state.rows * state.cols
            val isValidLargeField = state.isLargeField && totalPlots <= FieldConfig.MAX_TOTAL_PLOTS
            largeFieldWarningCard.visibility = if (isValidLargeField) View.VISIBLE else View.GONE
        }

        fieldCreatorViewModel.validationErrors.observe(viewLifecycleOwner) { errors ->
            fieldNameInputLayout.error = errors.fieldNameError
            rowsInputLayout.error = errors.rowsError
            colsInputLayout.error = errors.colsError

            if (errors.dimensionsError != null) {
                fieldDimensionsErrorText.text = errors.dimensionsError
                fieldDimensionsErrorText.visibility = View.VISIBLE
            } else {
                fieldDimensionsErrorText.visibility = View.GONE
            }
        }
    }

    private fun setupTextWatchers() {
        fieldNameEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                fieldCreatorViewModel.updateFieldName(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        rowsEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val rows = s.toString().toIntOrNull() ?: 0
                val cols = colsEditText.text.toString().toIntOrNull() ?: 0
                fieldCreatorViewModel.updateDimensions(rows, cols, context)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        colsEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val rows = rowsEditText.text.toString().toIntOrNull() ?: 0
                val cols = s.toString().toIntOrNull() ?: 0
                fieldCreatorViewModel.updateDimensions(rows, cols, context)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun updateSizePreview() {
        sizePreviewGrid.setContent {
            MaterialTheme {
                val config by fieldCreatorViewModel.fieldConfig.observeAsState()

                config?.let { state ->
                    if (state.rows > 0 && state.cols > 0) {
                        FieldPreviewGrid(
                            config = state,
                            showPlotNumbers = false,
                            forceFullView = false,
                            onGridDimensionsCalculated = { displayRows, displayCols ->
                                // store the dimensions for other fragments to use
                                fieldCreatorViewModel.setReferenceGridDimensions(
                                    displayRows,
                                    displayCols
                                )
                            },
                            maxDisplayPercentage = 0.9f
                        )
                    }
                }
            }
        }
    }

    private fun dismissKeyboard() {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        view?.let { currentView ->
            imm.hideSoftInputFromWindow(currentView.windowToken, 0)
        }
    }
}