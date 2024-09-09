package com.fieldbook.tracker.activities.brapi.update

import android.content.Context
import android.content.Intent
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import kotlinx.coroutines.flow.Flow

open class BrapiCropsFilterActivity(override val titleResId: Int = R.string.brapi_filter_type_crop) :
    BrapiListFilterActivity<String>() {

    companion object {

        const val FILTER_NAME = "$PREFIX.cropDbIds"

        fun getIntent(context: Context): Intent {
            return Intent(context, BrapiCropsFilterActivity::class.java)
        }
    }

    override val filterName: String
        get() = FILTER_NAME

//    override fun List<String?>.mapToUiModel() = filterNotNull().map { crop ->
//        CheckboxListAdapter.Model(
//            checked = false,
//            id = crop,
//            label = crop,
//            subLabel = ""
//        )
//    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menu?.findItem(R.id.action_check_all)?.isVisible = true
        menu?.findItem(R.id.action_reset_cache)?.isVisible = true
        menu?.findItem(R.id.action_brapi_filter)?.isVisible = false
        return true
    }

    override suspend fun loadListData(): Flow<Any> {
        TODO("Not yet implemented")
    }

    override fun List<String?>.mapToUiModel(): List<CheckboxListAdapter.Model> {
        TODO("Not yet implemented")
    }

}
