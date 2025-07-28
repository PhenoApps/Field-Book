package com.fieldbook.tracker.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.viewmodels.FieldCreatorViewModel
import com.fieldbook.tracker.views.FieldCreationStep
import com.fieldbook.tracker.views.FieldCreatorStepper

class FieldCreatorActivity : ThemedActivity() {

    companion object {
        fun getIntent(context: Context): Intent = Intent(context, FieldCreatorActivity::class.java)
    }

    private val fieldCreatorViewModel: FieldCreatorViewModel by viewModels()
    private lateinit var stepperView: ComposeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_field_creator)

        val toolbar = findViewById<Toolbar>(R.id.field_creator_toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.apply {
            title = getString(R.string.field_creator_activity_toolbar_title)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            setHomeButtonEnabled(true)
        }

        stepperView = findViewById(R.id.field_creator_stepper)
        setupStepper(getCurrentStepFromViewModel())
        observeForStepper()
    }

    private fun setupStepper(step: FieldCreationStep) {
        stepperView.setContent {
            MaterialTheme {
                FieldCreatorStepper(step)
            }
        }
    }

    private fun observeForStepper() {
        fieldCreatorViewModel.currentStep.observe(this) { step ->
            setupStepper(step)
        }
    }

    private fun getCurrentStepFromViewModel(): FieldCreationStep {
        return fieldCreatorViewModel.currentStep.value ?: FieldCreationStep.FIELD_SIZE
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}