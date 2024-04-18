package com.fieldbook.tracker.activities.brapi.hackathon.cropontology.viewmodels

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.fieldbook.tracker.activities.brapi.hackathon.cropontology.dao.StudyFtsDao
import com.fieldbook.tracker.activities.brapi.hackathon.cropontology.models.RankedStudy

class BrapiStudyViewModel(
    private val fts: StudyFtsDao
): ViewModel() {

    fun searchResult(query: String) = liveData {

        val searchResult = try {
            fts.search("$query")
        } catch (e: SQLiteException) {
            emptyList<RankedStudy>()
        }
        emit(searchResult)

    }
}
