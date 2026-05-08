package com.fieldbook.shared.screens.brapi

import androidx.compose.runtime.Composable
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.brapi_filter_type_crop
import com.fieldbook.shared.generated.resources.brapi_filter_type_study
import com.fieldbook.shared.generated.resources.brapi_filter_type_trial
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource

data class BrapiSelectableItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: DrawableResource? = null,
)

data class BrapiFilterElement(
    val id: String,
    val label: String,
    val count: Int,
)

data class BrapiFilterChoice(
    val id: String,
    val label: String,
)

data class BrapiFilterUiState(
    val title: String = "",
    val elements: List<BrapiFilterElement> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val emptyMessage: String = "No filter values",
    val noMatchesMessage: String = "No filter values match",
)

enum class BrapiTraitFilterType {
    TRIAL,
    STUDY,
    CROP
}

@Composable
fun BrapiTraitFilterType.title(): String {
    return when (this) {
        BrapiTraitFilterType.TRIAL -> stringResource(Res.string.brapi_filter_type_trial)
        BrapiTraitFilterType.STUDY -> stringResource(Res.string.brapi_filter_type_study)
        BrapiTraitFilterType.CROP -> stringResource(Res.string.brapi_filter_type_crop)
    }
}

data class BrapiImportListUiState(
    val title: String,
    val query: String,
    val totalItemCount: Int,
    val items: List<BrapiSelectableItem>,
    val selectedIds: Set<String>,
    val loading: Boolean,
    val importing: Boolean,
    val filterChoices: List<BrapiFilterChoice>,
    val emptyMessage: String,
    val noMatchesMessage: String,
    val importButtonText: String,
)

sealed interface BrapiImportListEvent {
    data class QueryChanged(val query: String) : BrapiImportListEvent
    data class ItemSelectionChanged(val id: String, val selected: Boolean) : BrapiImportListEvent
    data object ImportClicked : BrapiImportListEvent
    data object ResetCacheConfirmed : BrapiImportListEvent
    data class FilterChoiceSelected(val id: String) : BrapiImportListEvent
}
