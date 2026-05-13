package com.fieldbook.shared.screens.brapi.field

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fieldbook.shared.brapi.model.v2.core.BrapiStudyDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BrapiFieldImportViewModel : ViewModel() {

    private val _selectedStudy = MutableStateFlow<BrapiStudyDetails?>(null)
    val selectedStudy: StateFlow<BrapiStudyDetails?> = _selectedStudy.asStateFlow()

    fun setSelectedStudy(study: BrapiStudyDetails) {
        _selectedStudy.value = study
    }
}

fun brapiFieldImportViewModelFactory() = viewModelFactory {
    initializer {
        BrapiFieldImportViewModel()
    }
}
