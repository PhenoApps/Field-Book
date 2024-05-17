package com.fieldbook.tracker.activities.brapi.update.contracts

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

class ProgramsResultContract: ActivityResultContract<Unit, List<String>>() {
    override fun createIntent(context: Context, input: Unit): Intent {
        TODO("Not yet implemented")
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<String> {
        TODO("Not yet implemented")
    }
}