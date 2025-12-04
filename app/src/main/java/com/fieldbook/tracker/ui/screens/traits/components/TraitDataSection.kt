package com.fieldbook.tracker.ui.screens.traits.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.database.viewmodels.ObservationData
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.ui.components.widgets.CardView

@Composable
fun TraitDataSection(
    trait: TraitObject,
    observationData: ObservationData?
) {
    if (observationData == null) {
        Text("No observation data available.")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Text("Fields: ${observationData.fieldCount}")
        Text("Observations: ${observationData.observationCount}")

        val percent = (observationData.completeness * 100).toInt()
        Text("Completeness: $percent%")

        // chart
        if (observationData.processedObservations.isEmpty()) {
            Text("No chart data")
        } else {
            if (trait.format == "categorical" || trait.format == "boolean") {
                BarChartView(
                    observations = observationData.processedObservations
                )
            } else {
                HistogramChartView(
                    observations = observationData.processedObservations
                )
            }
        }
    }
}

@Composable
fun BarChartView(observations: List<String>) {
    CardView {
        Text("Bar Chart Placeholder (${observations.size} values)")
    }
}

@Composable
fun HistogramChartView(observations: List<String>) {
    CardView {
        Text("Histogram Placeholder (${observations.size} values)")
    }
}

