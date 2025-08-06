package com.fieldbook.tracker.fragments.field_creator

import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.navigation.fragment.findNavController
import com.fieldbook.tracker.R
import com.fieldbook.tracker.views.FieldCreationStep

class FieldCreatorDirectionFragment : FieldCreatorBaseFragment() {

    override fun getCurrentStep(): FieldCreationStep = FieldCreationStep.WALKING_DIRECTION
    override fun getLayoutResourceId(): Int = R.layout.fragment_field_creator_direction
    override fun onForwardClick(): (() -> Unit)? = {
        findNavController().navigate(FieldCreatorDirectionFragmentDirections.actionFromDirectionToPattern())
    }

    private lateinit var directionRadioGroup: RadioGroup
    private lateinit var radioHorizontal: RadioButton
    private lateinit var radioVertical: RadioButton
    private lateinit var horizontalContainer: LinearLayout
    private lateinit var verticalContainer: LinearLayout

    override fun setupViews(view: View) {
        directionRadioGroup = view.findViewById(R.id.direction_radio_group)
        radioHorizontal = view.findViewById(R.id.radio_horizontal)
        radioVertical = view.findViewById(R.id.radio_vertical)
        horizontalContainer = view.findViewById(R.id.horizontal_container)
        verticalContainer = view.findViewById(R.id.vertical_container)

        setupClickListeners()
    }

    override fun observeFieldCreatorViewModel() {
        fieldCreatorViewModel.fieldConfig.observe(viewLifecycleOwner) { state ->
            updateRadioButtons(state.isHorizontal)

            val isForwardEnabled = state.isHorizontal != null
            updateForwardButtonState(isForwardEnabled)
        }
    }

    private fun setupClickListeners() {
        directionRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                radioHorizontal.id -> fieldCreatorViewModel.updateDirection(true)
                radioVertical.id -> fieldCreatorViewModel.updateDirection(false)
                RadioGroup.NO_ID -> {
                    // initial state
                }
            }
        }

        horizontalContainer.setOnClickListener {
            directionRadioGroup.check(radioHorizontal.id)
        }
        verticalContainer.setOnClickListener {
            directionRadioGroup.check(radioVertical.id)
        }
    }

    private fun updateRadioButtons(isHorizontal: Boolean?) {
        when (isHorizontal) {
            true -> directionRadioGroup.check(radioHorizontal.id)
            false -> directionRadioGroup.check(radioVertical.id)
            null -> directionRadioGroup.clearCheck()
        }
    }
}