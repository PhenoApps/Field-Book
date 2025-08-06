package com.fieldbook.tracker.fragments.field_creator

import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.navigation.fragment.findNavController
import com.fieldbook.tracker.R
import com.fieldbook.tracker.views.FieldCreationStep

class FieldCreatorPatternTypeFragment : FieldCreatorBaseFragment() {

    override fun getCurrentStep(): FieldCreationStep = FieldCreationStep.WALKING_PATTERN
    override fun getLayoutResourceId(): Int = R.layout.fragment_field_creator_pattern_type
    override fun onForwardClick(): (() -> Unit)? = {
        findNavController().navigate(FieldCreatorPatternTypeFragmentDirections.actionFromPatternTypeToPreview())
    }

    private lateinit var patternRadioGroup: RadioGroup
    private lateinit var radioLinear: RadioButton
    private lateinit var radioZigzag: RadioButton
    private lateinit var linearContainer: LinearLayout
    private lateinit var zigzagContainer: LinearLayout

    override fun setupViews(view: View) {
        patternRadioGroup = view.findViewById(R.id.pattern_radio_group)
        radioLinear = view.findViewById(R.id.radio_linear)
        radioZigzag = view.findViewById(R.id.radio_zigzag)
        linearContainer = view.findViewById(R.id.linear_container)
        zigzagContainer = view.findViewById(R.id.zigzag_container)

        setupClickListeners()
    }

    override fun observeFieldCreatorViewModel() {
        fieldCreatorViewModel.fieldConfig.observe(viewLifecycleOwner) { state ->
            updateRadioButtons(state.isZigzag)

            val isForwardEnabled = state.isZigzag != null
            updateForwardButtonState(isForwardEnabled)
        }
    }

    private fun setupClickListeners() {
        patternRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                radioLinear.id -> fieldCreatorViewModel.updatePatternType(false)
                radioZigzag.id -> fieldCreatorViewModel.updatePatternType(true)
                RadioGroup.NO_ID -> {
                    // initial state
                }
            }
        }

        linearContainer.setOnClickListener {
            patternRadioGroup.check(radioLinear.id)
        }

        zigzagContainer.setOnClickListener {
            patternRadioGroup.check(radioZigzag.id)
        }
    }

    private fun updateRadioButtons(isZigzag: Boolean?) {
        when (isZigzag) {
            true -> patternRadioGroup.check(radioZigzag.id)
            false -> patternRadioGroup.check(radioLinear.id)
            null -> patternRadioGroup.clearCheck()
        }
    }
}