package com.fieldbook.tracker.activities.brapi.update

import android.content.Context
import android.content.Intent
import com.fieldbook.tracker.activities.ThemedActivity

/**
 * receive study infomation including trial
 * from trial get program
 * get obs. levels
 * get observation units, and traits
 */
class BrapiStudyImportActivity: ThemedActivity() {

    companion object {

        const val EXTRA_STUDY_NAME = "studyName"
        const val EXTRA_STUDY_LOCATION = "studyLocation"
        const val EXTRA_STUDY_IDS = "studyIds"

        fun getIntent(context: Context): Intent {
            return Intent(context, BrapiStudyImportActivity::class.java)
        }
    }
}