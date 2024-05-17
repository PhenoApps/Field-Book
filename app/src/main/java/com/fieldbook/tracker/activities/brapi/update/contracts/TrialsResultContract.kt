package com.fieldbook.tracker.activities.brapi.update.contracts

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

class TrialsResultContract: ActivityResultContract<TrialsResultContract.TrialSearchParameters, List<String>>() {

    data class TrialSearchParameters(
        val programDbIds: List<String>,
        val seasonDbIds: List<String>
    )

    override fun createIntent(context: Context, input: TrialSearchParameters): Intent {
        TODO("Not yet implemented")
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<String> {
        TODO("Not yet implemented")
    }
}