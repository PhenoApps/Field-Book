package com.fieldbook.tracker.cropontology.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import com.fieldbook.tracker.cropontology.dao.VariableFtsDao
import com.fieldbook.tracker.cropontology.models.RankedVariable

class VariableViewModel(
    private val fts: VariableFtsDao
): ViewModel() {

    fun searchResult(query: String) = liveData<List<RankedVariable>> {

        emit(fts.search("*$query*"))

    }
}
