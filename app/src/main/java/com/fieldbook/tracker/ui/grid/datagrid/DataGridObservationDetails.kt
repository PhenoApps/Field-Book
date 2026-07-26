package com.fieldbook.tracker.ui.grid.datagrid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject

@Composable
fun DataGridObservationDetails(
    cell: SelectedCell?,
    viewMode: DataGridViewMode,
    gridRowHeaderNames: List<String>,
    colors: DataGridUiColors,
    loadObservedMapTraits: suspend (plotId: String) -> List<TraitObject>,
    onCollectClicked: () -> Unit
) {
    val plotLabel = stringResource(R.string.act_data_grid_details_plot)
    val emptyHint = stringResource(R.string.act_data_grid_details_empty)
    val collectText = stringResource(R.string.act_data_grid_collect_button)
    val traitsWithDataTitle = stringResource(R.string.map_view_traits_with_data)
    val noValue = stringResource(R.string.act_data_grid_details_no_value)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(colors.filledCellBgColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (cell == null) {
                    Text(
                        text = emptyHint,
                        color = Color(colors.cellTextColor).copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                } else if (viewMode == DataGridViewMode.MAP) {
                    MapDetails(
                        cell = cell,
                        plotLabel = plotLabel,
                        traitsWithDataTitle = traitsWithDataTitle,
                        noValue = noValue,
                        colors = colors,
                        loadObservedMapTraits = loadObservedMapTraits
                    )
                } else {
                    GridDetails(
                        cell = cell,
                        rowHeaderNames = gridRowHeaderNames,
                        plotLabel = plotLabel,
                        colors = colors
                    )
                }
            }

            if (cell != null) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(44.dp)
                        .background(Color(colors.activeCellBgColor))
                        .clickable(onClick = onCollectClicked),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_table_arrow_right),
                        contentDescription = collectText,
                        tint = Color(colors.activeCellTextColor),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MapDetails(
    cell: SelectedCell,
    plotLabel: String,
    traitsWithDataTitle: String,
    noValue: String,
    colors: DataGridUiColors,
    loadObservedMapTraits: suspend (plotId: String) -> List<TraitObject>
) {
    val rowHeader = cell.label ?: cell.plotId
    DetailsRow(plotLabel, rowHeader, colors)

//    Spacer(Modifier.height(8.dp))
//    Text(
//        text = traitsWithDataTitle,
//        color = Color(colors.cellTextColor),
//        fontWeight = FontWeight.Bold,
//        fontSize = 12.sp
//    )
//    Spacer(Modifier.height(4.dp))
//
//    var traitIcons by remember {
//        mutableStateOf(emptyList<TraitObject>())
//    }
//
//    LaunchedEffect(cell.plotId) {
//        traitIcons = loadObservedMapTraits(cell.plotId)
//    }
//
//    if (traitIcons.isEmpty()) {
//        Text(
//            text = noValue,
//            color = Color(colors.cellTextColor).copy(alpha = 0.7f),
//            fontSize = 12.sp
//        )
//    } else {
//        Row(
//            modifier = Modifier.fillMaxWidth(),
//            horizontalArrangement = Arrangement.spacedBy(8.dp)
//        ) {
//            traitIcons.forEach { trait ->
//                TraitFormatIcon(trait = trait)
//            }
//        }
//    }
}

@Composable
private fun GridDetails(
    cell: SelectedCell,
    rowHeaderNames: List<String>,
    plotLabel: String,
    colors: DataGridUiColors
) {
    val rowHeader = rowHeaderNames.getOrNull(cell.row) ?: cell.plotId
    DetailsRow(plotLabel, rowHeader, colors)
}

@Composable
private fun DetailsRow(label: String, value: String, colors: DataGridUiColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = Color(colors.cellTextColor).copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            color = Color(colors.cellTextColor),
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TraitFormatIcon(trait: TraitObject) {
    val iconRes = when (trait.format.lowercase()) {
        "numeric" -> R.drawable.ic_trait_numeric
        "categorical", "qualitative" -> R.drawable.ic_trait_categorical
        "text" -> R.drawable.ic_trait_text
        "date" -> R.drawable.ic_trait_date
        "time" -> R.drawable.ic_trait_counter
        "photo" -> R.drawable.ic_trait_camera
        "video" -> R.drawable.ic_trait_gopro
        "audio" -> R.drawable.ic_trait_audio
        else -> R.drawable.ic_trait_numeric
    }
    val color = when (trait.format.lowercase()) {
        "numeric" -> Color(0xFF2196F3)
        "categorical", "qualitative" -> Color(0xFF4CAF50)
        "text" -> Color(0xFF9C27B0)
        "date" -> Color(0xFFFF9800)
        "time" -> Color(0xFF00BCD4)
        "photo" -> Color(0xFFE91E63)
        "video" -> Color(0xFF673AB7)
        "audio" -> Color(0xFF795548)
        else -> Color.Gray
    }

    Box(
        modifier = Modifier.size(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = trait.alias,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
    }
}
