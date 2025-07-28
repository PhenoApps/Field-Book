package com.fieldbook.tracker.fragments.field_creator

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.navigation.fragment.findNavController
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.FieldCreatorActivity
import com.fieldbook.tracker.views.FieldCreationStep
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class FieldCreatorSizeFragment : FieldCreatorBaseFragment() {

    override fun getCurrentStep(): FieldCreationStep = FieldCreationStep.FIELD_SIZE
    override fun getLayoutResourceId(): Int = R.layout.fragment_field_creator_size

    private lateinit var fieldNameEditText: TextInputEditText
    private lateinit var rowsEditText: TextInputEditText
    private lateinit var colsEditText: TextInputEditText
    private lateinit var fieldNameInputLayout: TextInputLayout
    private lateinit var rowsInputLayout: TextInputLayout
    private lateinit var colsInputLayout: TextInputLayout
    private lateinit var nextButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                activity?.finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun setupViews(view: View) {
        fieldNameEditText = view.findViewById(R.id.field_name_edit_text)
        rowsEditText = view.findViewById(R.id.rows_edit_text)
        colsEditText = view.findViewById(R.id.cols_edit_text)
        fieldNameInputLayout = view.findViewById(R.id.field_name_input_layout)
        rowsInputLayout = view.findViewById(R.id.rows_input_layout)
        colsInputLayout = view.findViewById(R.id.cols_input_layout)
        nextButton = view.findViewById(R.id.next_button)

        setupTextWatchers()
        setupButtonListener()
    }

    override fun observeViewModel() {
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

    private fun setupButtonListener() {
        nextButton.setOnClickListener {
            if (fieldCreatorViewModel.validateBasicInfo(db)) {
                val state = fieldCreatorViewModel.fieldConfig.value
                state?.let {
                    findNavController().navigate(FieldCreatorSizeFragmentDirections.actionFromSizeToStartPoint())
                }
            }
        }
    }
}