package com.fieldbook.tracker.objects

import com.fieldbook.tracker.adapters.AttributeAdapter.AttributeModel

data class SearchDialogDataModel(
    var attribute: AttributeModel,
    var imageResourceId: Int,
    var text: String
)
