package com.fieldbook.tracker.activities.brapi.update

import android.content.Context
import android.content.Intent
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.CheckboxListAdapter

open class BrapiCropsFilterActivity(override val titleResId: Int = R.string.brapi_filter_type_crop) :
    BrapiSubFilterListActivity<String>() {

    companion object {

        const val FILTER_NAME = "$PREFIX.cropDbIds"

        fun getIntent(context: Context): Intent {
            return Intent(context, BrapiCropsFilterActivity::class.java)
        }
    }

    override val filterName: String
        get() = FILTER_NAME

    override fun List<TrialStudyModel>.mapToUiModel() = map { model ->
        CheckboxListAdapter.Model(
            checked = false,
            id = model.study.commonCropName,
            label = model.study.commonCropName,
            subLabel = ""
        )
    }
}
