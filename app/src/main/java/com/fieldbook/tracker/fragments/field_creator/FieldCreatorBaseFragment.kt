package com.fieldbook.tracker.fragments.field_creator

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.viewmodels.FieldCreationResult
import com.fieldbook.tracker.viewmodels.FieldCreatorViewModel
import com.fieldbook.tracker.views.FieldCreationStep
import com.google.android.material.button.MaterialButton

abstract class FieldCreatorBaseFragment : Fragment() {

    protected val db by lazy { DataHelper(requireContext()) }

    protected val fieldCreatorViewModel: FieldCreatorViewModel by activityViewModels()

    protected abstract fun getCurrentStep(): FieldCreationStep
    protected abstract fun getLayoutResourceId(): Int

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(getLayoutResourceId(), container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadInitialState()

        updateActivityStepper()
        setupViews(view)

        observeFieldCreatorViewModel()
    }

    private fun updateActivityStepper() {
        fieldCreatorViewModel.updateCurrentStep(getCurrentStep())
    }

    protected abstract fun setupViews(view: View)

    private fun loadInitialState() {
        val currentState = fieldCreatorViewModel.fieldConfig.value
        if (currentState?.fieldName?.isEmpty() == true && arguments != null) {
            fieldCreatorViewModel.loadState(
                fieldName = arguments?.getString("fieldName"),
                rows = arguments?.getInt("rows")?.takeIf { it > 0 },
                cols = arguments?.getInt("cols")?.takeIf { it > 0 },
                startCorner = arguments?.getString("startCorner"),
                isZigzag = arguments?.getBoolean("isZigzag"),
                isHorizontal = arguments?.getBoolean("isHorizontal")
            )
        }
    }

    protected open fun observeFieldCreatorViewModel() { }

    protected fun setupFieldSummaryInfo(fieldSummaryText: TextView) {
        fieldCreatorViewModel.fieldConfig.observe(viewLifecycleOwner) { state ->
            fieldSummaryText.text = String.format(
                getString(R.string.field_creator_preview_summary),
                state.fieldName,
                state.rows,
                state.cols
            )
        }
    }

    protected fun setupFieldCreationObserver(progressContainer: LinearLayout, createFieldButton: MaterialButton) {
        createFieldButton.setOnClickListener {
            fieldCreatorViewModel.createField(db, context)
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
}