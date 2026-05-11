package com.fieldbook.tracker.database.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.fieldbook.tracker.database.repository.SpectralRepository
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.database.viewmodels.CollectViewModel
import com.fieldbook.tracker.database.viewmodels.SpectralViewModel

class CollectViewModelFactory(private val repository: TraitRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CollectViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CollectViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
