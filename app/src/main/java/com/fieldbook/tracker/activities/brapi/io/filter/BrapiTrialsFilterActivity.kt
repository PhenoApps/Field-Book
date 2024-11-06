package com.fieldbook.tracker.activities.brapi.io.filter

import android.content.Context
import android.content.Intent
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.brapi.io.TrialStudyModel
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import org.brapi.v2.model.core.BrAPITrial

open class BrapiTrialsFilterActivity(override val titleResId: Int = R.string.brapi_filter_type_trial) :
    BrapiSubFilterListActivity<BrAPITrial>() {

    companion object {

        const val FILTER_NAME = "trialDbIds"

        fun getIntent(context: Context): Intent {
            return Intent(context, BrapiTrialsFilterActivity::class.java)
        }
    }

    override val filterName: String
        get() = FILTER_NAME

    override fun List<TrialStudyModel>.filterByPreferences(): List<TrialStudyModel> {

        val programDbIds = getIds(BrapiProgramFilterActivity.FILTER_NAME)

        return this
            .filter { if (programDbIds.isNotEmpty()) it.programDbId in programDbIds else true }
    }

    override fun List<TrialStudyModel>.mapToUiModel() = mapNotNull { model ->

        if (model.trialDbId != null && model.trialName != null) {
            CheckboxListAdapter.Model(
                checked = false,
                id = model.trialDbId!!,
                label = model.trialName!!,
                subLabel = model.study.locationName ?: ""
            )
        } else {
            null
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menu?.findItem(R.id.action_check_all)?.isVisible = true
        menu?.findItem(R.id.action_reset_cache)?.isVisible = false
        menu?.findItem(R.id.action_brapi_filter)?.isVisible = false
        return true
    }
}
