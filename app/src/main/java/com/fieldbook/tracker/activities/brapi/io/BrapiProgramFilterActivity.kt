package com.fieldbook.tracker.activities.brapi.io

import android.content.Context
import android.content.Intent
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import org.brapi.v2.model.core.BrAPIProgram

class BrapiProgramFilterActivity(override val titleResId: Int = R.string.brapi_filter_type_program) :
    BrapiSubFilterListActivity<BrAPIProgram>() {

    companion object {

        const val FILTER_NAME = "$PREFIX.programDbIds"

        fun getIntent(context: Context): Intent {
            return Intent(context, BrapiProgramFilterActivity::class.java)
        }
    }

    override val filterName: String
        get() = FILTER_NAME

    override fun List<TrialStudyModel>.mapToUiModel() = filterNotNull().mapNotNull { model ->
        if (model.programDbId != null && model.programName != null) {
            CheckboxListAdapter.Model(
                checked = false,
                id = model.programDbId!!,
                label = model.programName!!,
                subLabel = ""
            )
        } else null
    }
}
