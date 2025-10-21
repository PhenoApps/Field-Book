package com.fieldbook.tracker.database.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.fieldbook.tracker.database.repository.SpectralRepository
import com.fieldbook.tracker.database.viewmodels.SpectralViewModel

class SpectralViewModelFactory(private val repository: SpectralRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SpectralViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SpectralViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
