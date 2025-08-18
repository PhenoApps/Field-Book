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
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.fieldbook.tracker.R
import com.fieldbook.tracker.viewmodels.FieldConfig
import com.fieldbook.tracker.enums.FieldCreationStep
import com.fieldbook.tracker.views.FieldPreviewGrid
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class FieldCreatorSizeFragment : FieldCreatorBaseFragment() {

    override fun getCurrentStep(): FieldCreationStep = FieldCreationStep.FIELD_SIZE
    override fun getLayoutResourceId(): Int = R.layout.fragment_field_creator_size
    override fun onForwardClick(): (() -> Unit)? = {
        if (fieldCreatorViewModel.validateBasicInfo(db)) {
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
    private lateinit var previewContainer: ComposeView

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
        previewContainer = view.findViewById(R.id.size_preview_container)

        setupTextWatchers()
    }

    override fun observeFieldCreatorViewModel() {
        updateForwardButtonState(true) // enable click on forward button to show validation errors

        fieldCreatorViewModel.fieldConfig.observe(viewLifecycleOwner) { state ->
            if (fieldNameEditText.text.toString() != state.fieldName) {
                fieldNameEditText.setText(state.fieldName)
            }
            if (rowsEditText.text.toString() != state.rows.toString() && state.rows > 0) {
                rowsEditText.setText(state.rows.toString())
            }
            if (colsEditText.text.toString() != state.cols.toString() && state.cols > 0) {
                colsEditText.setText(state.cols.toString())
            }

            updatePreview(state)
        }

        fieldCreatorViewModel.validationErrors.observe(viewLifecycleOwner) { errors ->
            fieldNameInputLayout.error = errors.fieldNameError
            rowsInputLayout.error = errors.rowsError
            colsInputLayout.error = errors.colsError
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
                fieldCreatorViewModel.updateDimensions(rows, cols)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        colsEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val rows = rowsEditText.text.toString().toIntOrNull() ?: 0
                val cols = s.toString().toIntOrNull() ?: 0
                fieldCreatorViewModel.updateDimensions(rows, cols)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun updatePreview(config: FieldConfig) {
        previewContainer.setContent {
            MaterialTheme {
                if (config.rows > 0 && config.cols > 0) {
                    FieldPreviewGrid(
                        config = config,
                        showPlotNumbers = false,
                        forceFullView = false,
                        onGridDimensionsCalculated = { displayRows, displayCols ->
                            // store the dimensions for other fragments to use
                            fieldCreatorViewModel.setReferenceGridDimensions(displayRows, displayCols)
                        },
                    )
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