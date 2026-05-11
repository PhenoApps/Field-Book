package com.fieldbook.tracker.activities.brapi.io.filter

import android.content.Context
import android.content.Intent
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.brapi.io.BrapiCacheModel
import com.fieldbook.tracker.activities.brapi.io.TrialStudyModel
import com.fieldbook.tracker.adapters.CheckboxListAdapter

open class BrapiCropsFilterActivity(override val titleResId: Int = R.string.brapi_filter_type_crop) :
    BrapiSubFilterListActivity<String>() {

    companion object {

        const val FILTER_NAME = "cropDbIds"

        fun getIntent(context: Context): Intent {
            return Intent(context, BrapiCropsFilterActivity::class.java)
        }
    }

    override val filterName: String
        get() = FILTER_NAME

    override fun BrapiCacheModel.mapToUiModel() = studies.mapNotNull { model ->
        try {
            CheckboxListAdapter.Model(
                checked = false,
                id = model.study.commonCropName,
                label = model.study.commonCropName,
                subLabel = ""
            )
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
