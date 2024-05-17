package com.fieldbook.tracker.activities.brapi.update.contracts

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

class SeasonsResultContract: ActivityResultContract<SeasonsResultContract.SearchParameters, List<String>>() {

    data class SearchParameters(
        val programDbIds: List<String>
    )

    override fun createIntent(context: Context, input: SearchParameters): Intent {
        TODO("Not yet implemented")
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<String> {
        TODO("Not yet implemented")
    }
}