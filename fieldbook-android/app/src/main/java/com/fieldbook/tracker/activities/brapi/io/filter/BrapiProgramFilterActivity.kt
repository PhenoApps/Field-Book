package com.fieldbook.tracker.activities.brapi.io.filter

import android.content.Context
import android.content.Intent
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.brapi.io.BrapiCacheModel
import com.fieldbook.tracker.activities.brapi.io.TrialStudyModel
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import org.brapi.v2.model.core.BrAPIProgram

class BrapiProgramFilterActivity(override val titleResId: Int = R.string.brapi_filter_type_program) :
    BrapiSubFilterListActivity<BrAPIProgram>() {

    companion object {

        const val FILTER_NAME = "programDbIds"

        fun getIntent(context: Context): Intent {
            return Intent(context, BrapiProgramFilterActivity::class.java)
        }
    }

    override val filterName: String
        get() = FILTER_NAME

    override fun BrapiCacheModel.mapToUiModel() = studies.mapNotNull { model ->
        if (model.programDbId != null && model.programName != null) {
            CheckboxListAdapter.Model(
                checked = false,
                id = model.programDbId!!,
                label = model.programName!!,
                subLabel = ""
            )
        } else null
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_filter_brapi, menu)
        menu?.findItem(R.id.action_check_all)?.isVisible = true
        menu?.findItem(R.id.action_reset_cache)?.isVisible = false
        menu?.findItem(R.id.action_brapi_filter)?.isVisible = false
        return true
    }
}
