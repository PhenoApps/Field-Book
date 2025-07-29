package com.fieldbook.tracker.fragments.field_creator

import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.navigation.fragment.findNavController
import com.fieldbook.tracker.R
import com.fieldbook.tracker.views.FieldCreationStep
import com.google.android.material.button.MaterialButton

class FieldCreatorDirectionFragment : FieldCreatorBaseFragment() {

    override fun getCurrentStep(): FieldCreationStep = FieldCreationStep.WALKING_DIRECTION
    override fun getLayoutResourceId(): Int = R.layout.fragment_field_creator_direction

    private lateinit var directionRadioGroup: RadioGroup
    private lateinit var radioHorizontal: RadioButton
    private lateinit var radioVertical: RadioButton
    private lateinit var horizontalContainer: LinearLayout
    private lateinit var verticalContainer: LinearLayout
    private lateinit var nextButton: MaterialButton

    override fun setupViews(view: View) {
        directionRadioGroup = view.findViewById(R.id.direction_radio_group)
        radioHorizontal = view.findViewById(R.id.radio_horizontal)
        radioVertical = view.findViewById(R.id.radio_vertical)
        horizontalContainer = view.findViewById(R.id.horizontal_container)
        verticalContainer = view.findViewById(R.id.vertical_container)
        nextButton = view.findViewById(R.id.next_button)

        setupClickListeners()
    }

    override fun observeFieldCreatorViewModel() {
        fieldCreatorViewModel.fieldConfig.observe(viewLifecycleOwner) { state ->
            updateRadioButtons(state.isHorizontal)
            nextButton.isEnabled = state.isHorizontal != null
        }
    }

    private fun setupClickListeners() {
        horizontalContainer.setOnClickListener {
            selectDirection(true)
        }

        verticalContainer.setOnClickListener {
            selectDirection(false)
        }

        enableDirectionRadioListener()

        nextButton.setOnClickListener {
            findNavController().navigate(FieldCreatorDirectionFragmentDirections.actionFromDirectionToPreview())
        }
    }

    private fun selectDirection(isHorizontal: Boolean) {
        fieldCreatorViewModel.updateDirection(isHorizontal)
    }

    private fun updateRadioButtons(isHorizontal: Boolean?) {
        directionRadioGroup.setOnCheckedChangeListener(null)

        when (isHorizontal) {
            true -> {
                radioHorizontal.isChecked = true
                radioVertical.isChecked = false
            }
            false -> {
                radioHorizontal.isChecked = false
                radioVertical.isChecked = true
            }
            null -> {
                radioHorizontal.isChecked = false
                radioVertical.isChecked = false
            }
        }

        enableDirectionRadioListener()
    }

    private fun enableDirectionRadioListener() {
        directionRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radio_horizontal -> selectDirection(true)
                R.id.radio_vertical -> selectDirection(false)
            }
        }
    }
}