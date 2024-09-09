package com.fieldbook.tracker.activities.brapi.update

import android.content.Context
import android.content.Intent
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.brapi.v2.model.core.BrAPITrial

open class BrapiTrialsFilterActivity(override val titleResId: Int = R.string.brapi_filter_type_trial) :
    BrapiListFilterActivity<BrAPITrial>() {

    companion object {

        const val FILTER_NAME = "$PREFIX.trialDbIds"

        fun getIntent(context: Context): Intent {
            return Intent(context, BrapiTrialsFilterActivity::class.java)
        }
    }

    override val filterName: String
        get() = FILTER_NAME

//    override fun List<BrAPITrial?>.mapToUiModel() = filterNotNull().map { trial ->
//        CheckboxListAdapter.Model(
//            checked = trial.trialDbId in cache.map { it.id },
//            id = trial.trialDbId,
//            label = trial.trialName,
//            subLabel = trial.trialDescription
//        )
//    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menu?.findItem(R.id.action_check_all)?.isVisible = true
        menu?.findItem(R.id.action_reset_cache)?.isVisible = true
        menu?.findItem(R.id.action_brapi_filter)?.isVisible = false
        return true
    }

    override suspend fun loadListData(): Flow<Any> = channelFlow {
        getStoredModels().forEach { model ->


        }
    }

    override fun List<BrAPITrial?>.mapToUiModel() = filterNotNull().map { trial ->
        CheckboxListAdapter.Model(
            checked = false,
            id = trial.trialDbId,
            label = trial.trialName,
            subLabel = trial.startDate.toString()
        )
    }

    //override suspend fun queryAll(): Flow<Any> = (brapiService as BrAPIServiceV2).trialService.fetchAll(TrialQueryParams())
}
