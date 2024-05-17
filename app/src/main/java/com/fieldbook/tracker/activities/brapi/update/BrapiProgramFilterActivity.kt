package com.fieldbook.tracker.activities.brapi.update

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import com.fieldbook.tracker.brapi.model.BrapiProgram

open class BrapiProgramFilterActivity: BrapiFilterActivity() {

    companion object {
        fun getIntent(activity: Activity): Intent {
            return Intent(activity, BrapiProgramFilterActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        titleFilterTextView.text = getString(R.string.brapi_program_filter_title)
    }

    override fun getChosenFilter(): BrapiFilter {

        //get ids and names of selected programs from the adapter
        val selectedPrograms = cache.filter { p -> p.checked }.map { p -> p.id }

        prefs.edit().putStringSet("programDbIds", selectedPrograms.toSet()).apply()

        return BrapiFilter(BrapiType.PROGRAM, "test")
    }

    override fun loadFilter(): BrapiFilter {
        return BrapiFilter(BrapiType.PROGRAM, "test")
    }

    override fun Intent.saveFilterData(): BrapiFilter {
        return getChosenFilter()
    }

    override fun query() {

        brapiService.getPrograms(paginationManager, { programs ->

            cacheAndSubmitItems(programs.map())

            null

        }) { _ ->

            null
        }
    }

    private fun List<BrapiProgram>.map(): List<CheckboxListAdapter.Model> = map { program ->
        CheckboxListAdapter.Model(
            checked = false,
            id = program.programDbId,
            label = program.programName
        )
    }
}
