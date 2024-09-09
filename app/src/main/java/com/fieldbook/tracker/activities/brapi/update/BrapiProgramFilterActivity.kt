package com.fieldbook.tracker.activities.brapi.update

import android.content.Context
import android.content.Intent
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.brapi.v2.model.core.BrAPIProgram

class BrapiProgramFilterActivity(override val titleResId: Int = R.string.brapi_filter_type_program) :
    BrapiListFilterActivity<BrAPIProgram>() {

    companion object {

        const val FILTER_NAME = "$PREFIX.programDbIds"

        fun getIntent(context: Context): Intent {
            return Intent(context, BrapiProgramFilterActivity::class.java)
        }
    }

    override val filterName: String
        get() = FILTER_NAME

    override suspend fun loadListData(): Flow<BrAPIProgram> = flow {
        getStoredModels().forEach { model ->
            emit(BrAPIProgram().also {
                it.programDbId = model.programDbId
                it.programName = model.programName
            })
        }
    }

    override fun List<BrAPIProgram?>.mapToUiModel() = filterNotNull().map { program ->
        CheckboxListAdapter.Model(
            checked = false,
            id = program.programDbId,
            label = program.programName,
            subLabel = program.programType ?: String()
        )
    }

//    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
//        super.onCreateOptionsMenu(menu)
//        menu?.findItem(R.id.action_check_all)?.isVisible = true
//        menu?.findItem(R.id.action_reset_cache)?.isVisible = true
//        menu?.findItem(R.id.action_brapi_filter)?.isVisible = false
//        return true
//    }
}
