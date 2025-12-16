package com.fieldbook.tracker.ui.screens.traits.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.viewmodels.ObservationData
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.ui.components.graphs.BarChart
import com.fieldbook.tracker.ui.components.graphs.HistogramChart
import com.fieldbook.tracker.ui.theme.AppTheme

private val nonChartableFormats =
    setOf("audio", "gnss", "gopro", "location", "photo", "text", "usb camera")

@Composable
fun TraitDataSection(
    trait: TraitObject,
    observationData: ObservationData?,
) {
    if (observationData == null) return

    val filteredObservations =
        observationData.processedObservations.filter { it.isNotEmpty() && it != "NA" }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            trait.format in nonChartableFormats -> NoChartMessage(R.string.chart_incompatible_format)

            filteredObservations.isEmpty() -> NoChartMessage(R.string.chart_no_data)

            trait.format in setOf("categorical", "boolean") -> {
                BarChart(
                    observations = filteredObservations,
                    categories = trait.categories,
                )
            }

            else -> {
                val isAllNumeric = allObservationsAreNumbers(filteredObservations)

                if (isAllNumeric) {
                    HistogramChart(observations = filteredObservations)
                } else {
                    BarChart(
                        observations = filteredObservations,
                        categories = trait.categories,
                    )
                }
            }
        }
    }
}

/**
 * Only purely numeric data should be plotted on a histogram
 */
fun allObservationsAreNumbers(observations: List<String>): Boolean =
    observations.all { runCatching { it.toBigDecimal() }.isSuccess }

@Composable
private fun NoChartMessage(stringRes: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(stringRes),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TraitDataSectionPreview() {
    AppTheme {
        TraitDataSection(
            trait = TraitObject().apply {
                alias = "Alias"
                format = "categorical"
                traitDataSource = "sample.csv"
                categories = "[\"f\":\"f\"]"
            },
            observationData = ObservationData(
                fieldCount = 2,
                observationCount = 1,
                completeness = 2.0f,
                processedObservations = listOf("")
            ),
        )
    }
}