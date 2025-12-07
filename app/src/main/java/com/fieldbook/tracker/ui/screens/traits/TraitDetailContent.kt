package com.fieldbook.tracker.ui.screens.traits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.viewmodels.ObservationData
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.parameters.BaseFormatParameter
import com.fieldbook.tracker.ui.components.graphs.PieChart
import com.fieldbook.tracker.ui.components.widgets.Chip
import com.fieldbook.tracker.ui.screens.traits.components.TraitDataSection
import com.fieldbook.tracker.ui.screens.traits.components.TraitOptionsSection
import com.fieldbook.tracker.ui.screens.traits.components.TraitOverviewSection
import com.fieldbook.tracker.ui.components.widgets.CollapsibleSection
import com.fieldbook.tracker.ui.theme.AppTheme

@Composable
fun TraitDetailContent(
    modifier: Modifier = Modifier,
    trait: TraitObject,
    observationData: ObservationData?,
    onUpdateAttributes: (TraitObject) -> Unit,
    onToggleVisibility: (Boolean) -> Unit,
    onResourceFilePickerDialog: () -> Unit,
    onUpdateAliasAndAddSynonym: (String) -> Unit,
    onValidateSynonym: (String) -> String?,
    onShowParameterEditDialog: (BaseFormatParameter, TraitObject, (TraitObject) -> Unit) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {

        // overview section
        CollapsibleSection(
            leadingIcon = R.drawable.ic_ruler,
            title = trait.alias,
            initiallyExpanded = true
        ) {
            TraitOverviewSection(
                trait = trait,
                onToggleVisibility = onToggleVisibility,
                onAddSynonym = { synonym ->
                    onUpdateAliasAndAddSynonym(synonym)
                },
                onValidateSynonym = onValidateSynonym
            )
        }

        // parameters
        CollapsibleSection(
            leadingIcon = R.drawable.ic_nav_drawer_settings,
            title = stringResource(R.string.trait_options_title),
            initiallyExpanded = true,
        ) {
            TraitOptionsSection(
                trait = trait,
                onUpdateTrait = onUpdateAttributes,
                onResourceFilePickerDialog = onResourceFilePickerDialog,
                onShowParameterEditDialog = onShowParameterEditDialog,
            )
        }

        // data collected summary
        CollapsibleSection(
            leadingIcon = R.drawable.ic_chart_bar,
            title = stringResource(R.string.trait_observation_data),
            initiallyExpanded = true,
            headerContent = {
                observationData?.let { obsData ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Chip(
                            icon = R.drawable.ic_land_fields,
                            text = obsData.fieldCount.toString()
                        )
                        Chip(
                            icon = R.drawable.ic_eye,
                            text = obsData.observationCount.toString()
                        )
                    }
                }
            },
            trailingContent = {
                observationData?.let { obsData ->
                    PieChart(completeness = obsData.completeness)
                }
            }
        ) {
            TraitDataSection(
                trait = trait,
                observationData = observationData
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TraitDetailPreview() {
    AppTheme {
        TraitDetailContent(
            trait = TraitObject().apply {
                alias = "Alias"
                format = "numeric"
                traitDataSource = "sample.csv"
            },
            observationData = ObservationData(
                fieldCount = 2,
                observationCount = 1,
                completeness = 2.0f,
                processedObservations = listOf()
            ),
            onUpdateAttributes = { },
            onToggleVisibility = { },
            onResourceFilePickerDialog = { },
            modifier = Modifier,
            onUpdateAliasAndAddSynonym = { },
            onValidateSynonym = { _ -> null },
            onShowParameterEditDialog = { _, _, _ -> null },
        )
    }
}
