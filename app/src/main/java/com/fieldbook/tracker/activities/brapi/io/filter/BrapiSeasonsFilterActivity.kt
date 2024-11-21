package com.fieldbook.tracker.activities.brapi.io.filter

import android.content.Context
import android.content.Intent
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.brapi.io.BrapiCacheModel
import com.fieldbook.tracker.activities.brapi.io.BrapiFilterTypeAdapter
import com.fieldbook.tracker.activities.brapi.io.TrialStudyModel
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import org.brapi.v2.model.core.BrAPISeason

open class BrapiSeasonsFilterActivity(override val titleResId: Int = R.string.brapi_filter_type_season) :
    BrapiSubFilterListActivity<BrAPISeason>() {

    companion object {

        const val FILTER_NAME = "seasonDbIds"

        fun getIntent(context: Context): Intent {
            return Intent(context, BrapiSeasonsFilterActivity::class.java)
        }

        fun List<TrialStudyModel>.filterByProgramAndTrial(
            programs: List<String>,
            trials: List<String>
        ): List<TrialStudyModel> {

            return filter { if (programs.isNotEmpty()) it.programDbId in programs else true }
                .filter { if (trials.isNotEmpty()) it.trialDbId in trials else true }
        }
    }

    override val filterName: String
        get() = FILTER_NAME

    override fun BrapiCacheModel.filterByPreferences(): BrapiCacheModel {

        val pids = getModels(BrapiProgramFilterActivity.FILTER_NAME)
            .filter { it.checked }
            .map { it.id }

        val tids = getModels(BrapiTrialsFilterActivity.FILTER_NAME)
            .filter { it.checked }
            .map { it.id }

        return this.also {
            it.studies = it.studies.filterByProgramAndTrial(pids, tids)
        }
    }

    override fun BrapiCacheModel.mapToUiModel(): List<CheckboxListAdapter.Model> {

        val seasonSet = hashSetOf<String>()

        studies.forEach { model ->
            model.study.seasons?.filterNotNull()?.forEach { season ->
                seasonSet.add(season)
            }
        }

        return seasonSet.toList().map {
            CheckboxListAdapter.Model(
                checked = false,
                id = it,
                label = it,
                subLabel = ""
            )
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
