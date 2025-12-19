package com.fieldbook.tracker.activities.brapi.io.filter

import android.content.Context
import android.content.Intent
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.brapi.io.BrapiCacheModel
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

        fun List<TrialStudyModel>.filterByProgram(programs: List<String>, seasons: List<String>): List<TrialStudyModel> {

            return filter { if (programs.isNotEmpty()) it.programDbId in programs else true }
                .filter { if (seasons.isNotEmpty()) it.study.seasons.any { s -> s in seasons } else true }
        }
    }

    override val filterName: String
        get() = FILTER_NAME

    override fun BrapiCacheModel.filterByPreferences() = this.also {
        it.studies = it.studies.filterByProgram(
            getModels(BrapiProgramFilterActivity.FILTER_NAME).filter { it.checked }.map { it.id },
            getModels(BrapiSeasonsFilterActivity.FILTER_NAME).filter { it.checked }.map { it.id }
        )
            .distinctBy { it.trialDbId }
    }

    override fun BrapiCacheModel.mapToUiModel() = studies.mapNotNull { model ->

        try {
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
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_filter_brapi, menu)
        menu?.findItem(R.id.action_check_all)?.isVisible = true
        menu?.findItem(R.id.action_reset_cache)?.isVisible = false
        menu?.findItem(R.id.action_brapi_filter)?.isVisible = false
        return true
    }
}
