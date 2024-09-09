package com.fieldbook.tracker.activities.brapi.update

import android.content.Context
import android.content.Intent
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import org.brapi.v2.model.core.BrAPISeason

open class BrapiSeasonsFilterActivity(override val titleResId: Int = R.string.brapi_filter_type_season) :
    BrapiListFilterActivity<BrAPISeason>() {

    companion object {

        const val FILTER_NAME = "$PREFIX.seasonDbIds"

        fun getIntent(context: Context): Intent {
            return Intent(context, BrapiSeasonsFilterActivity::class.java)
        }
    }

    override val filterName: String
        get() = FILTER_NAME

    override fun List<BrAPISeason?>.mapToUiModel() = filterNotNull().map { season ->
        CheckboxListAdapter.Model(
            checked = false,
            id = season.seasonDbId,
            label = season.seasonName,
            subLabel = "${season.year}"
        )
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menu?.findItem(R.id.action_check_all)?.isVisible = true
        menu?.findItem(R.id.action_reset_cache)?.isVisible = true
        menu?.findItem(R.id.action_brapi_filter)?.isVisible = false
        return true
    }

    //override suspend fun queryAll(): Flow<Any> = brapiService.seasonService.fetchAll(SeasonQueryParams())
}
