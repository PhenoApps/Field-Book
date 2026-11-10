package com.fieldbook.tracker.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.fieldbook.tracker.database.viewmodels.FieldInsightsViewModel
import com.fieldbook.tracker.ui.screens.fields.insights.FieldInsightsScreen
import com.fieldbook.tracker.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Explores one field's data across several views: progress over time, trait distributions, a
 * map of the field, group comparisons and relationships between traits.
 *
 * Opened from the field detail page; read only.
 */
@AndroidEntryPoint
class FieldInsightsActivity : ThemedActivity() {

    companion object {
        private const val TAG = "FieldInsightsActivity"

        const val FIELD_ID = "com.fieldbook.tracker.activities.FieldInsightsActivity.FIELD_ID"

        fun getIntent(context: Context, fieldId: Int): Intent =
            Intent(context, FieldInsightsActivity::class.java).putExtra(FIELD_ID, fieldId)
    }

    private val viewModel: FieldInsightsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val fieldId = intent.getIntExtra(FIELD_ID, -1)
        if (fieldId < 0) {
            Log.e(TAG, "Opened without a field id")
            finish()
            return
        }

        setContent {
            AppTheme {
                FieldInsightsScreen(
                    fieldId = fieldId,
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }
}
