package com.fieldbook.tracker.fragments.field_creator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.viewmodels.FieldCreatorViewModel
import com.fieldbook.tracker.views.FieldCreationStep

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
}