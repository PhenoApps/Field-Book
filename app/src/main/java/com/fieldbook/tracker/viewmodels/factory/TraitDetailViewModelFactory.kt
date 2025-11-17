package com.fieldbook.tracker.viewmodels.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.viewmodels.TraitDetailViewModel

class TraitDetailViewModelFactory(
    private val database: DataHelper
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TraitDetailViewModel::class.java)) {
            return TraitDetailViewModel(database) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}