package com.fieldbook.tracker.preferences.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.utilities.CallImplementedBy
import com.fieldbook.tracker.utilities.ServiceComparison
import eu.wewox.lazytable.LazyTable
import eu.wewox.lazytable.LazyTableItem
import eu.wewox.lazytable.LazyTableScrollDirection
import eu.wewox.lazytable.lazyTableDimensions
import eu.wewox.lazytable.lazyTablePinConfiguration

@Composable
fun ServerCallsTable(calls: List<ServiceComparison>) {
    val headerBackgroundColor = AppTheme.colors.primaryDark
    val cellBorderColor = AppTheme.colors.dataVisualization.dataGrid.tableBorder

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp),
    ) {
        val totalWidth = maxWidth
        val firstColumnWidth = totalWidth * 0.55f
        val remainingWidth = totalWidth * 0.45f
        val secondColumnWidth = remainingWidth / 2
        val thirdColumnWidth = remainingWidth / 2
        LazyTable(
            dimensions = lazyTableDimensions(
                columnSize = { columnIndex ->
                    when (columnIndex) {
                        0 -> firstColumnWidth
                        1 -> secondColumnWidth
                        2 -> thirdColumnWidth
                        else -> 100.dp
                    }
                },
                rowSize = { rowIndex ->
                    if (rowIndex == 0) { // header row
                        48.dp
                    } else { // dynamic height based on content
                        val callIndex = rowIndex - 1
                        if (callIndex < calls.size) {
                            val totalTextLength = calls[callIndex].service.length +
                                    calls[callIndex].methods.joinToString(", ").length
                            when {
                                totalTextLength > 150 -> 120.dp
                                totalTextLength > 100 -> 100.dp
                                totalTextLength > 50 -> 80.dp
                                else -> 60.dp
                            }
                        } else 60.dp
                    }
                }
            ),
            contentPadding = PaddingValues(0.dp),
            pinConfiguration = lazyTablePinConfiguration(columns = 0, rows = 1),
            scrollDirection = LazyTableScrollDirection.VERTICAL,
        ) {
            // total items: (header row + data rows) * 3 columns
            val totalItems = (calls.size + 1) * 3

            items(
                count = totalItems,
                layoutInfo = { index ->
                    val row = index / 3
                    val column = index % 3
                    LazyTableItem(column = column, row = row)
                }
            ) { index ->
                val row = index / 3
                val column = index % 3

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(0.5.dp, cellBorderColor)
                        .background(if (row == 0) headerBackgroundColor else Color.Transparent)
                        .padding(4.dp),
                    contentAlignment = if (column >= 1) Alignment.Center else Alignment.CenterStart
                ) {
                    if (row == 0) { // header row
                        Text(
                            text = when (column) {
                                0 -> stringResource(R.string.brapi_server_info_call)
                                1 -> stringResource(R.string.brapi_server_info_server)
                                2 -> stringResource(R.string.brapi_server_info_field_book)
                                else -> ""
                            },
                            style = AppTheme.typography.bodyStyle,
                            fontWeight = FontWeight.Bold,
                            textAlign = if (column >= 1) TextAlign.Center else TextAlign.Start
                        )
                    } else {
                        val callIndex = row - 1
                        if (callIndex < calls.size) {
                            val call = calls[callIndex]
                            when (column) {
                                0 -> Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text( // service resource
                                        text = call.service,
                                        style = AppTheme.typography.bodyStyle,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text( // service methods
                                        text = when (call.source) {
                                            CallImplementedBy.SERVER_AND_FIELD_BOOK -> {
                                                call.implementedMethods.joinToString(
                                                    ", "
                                                ).ifEmpty { "-" }
                                            }

                                            else -> call.methods.joinToString(", ")
                                                .ifEmpty { "-" }
                                        },
                                        style = AppTheme.typography.bodyStyle,
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .fillMaxWidth()
                                    )
                                }

                                1 -> { // server column
                                    val isServerImplemented =
                                        call.source != CallImplementedBy.FIELD_BOOK

                                    val icon =
                                        if (isServerImplemented) Icons.Default.Check else Icons.Default.Close
                                    val contentDescription =
                                        if (isServerImplemented) stringResource(R.string.brapi_compatibility_server_support) else stringResource(
                                            R.string.brapi_compatibility_no_server_support
                                        )
                                    val iconTint =
                                        if (isServerImplemented) AppTheme.colors.status.success else AppTheme.colors.status.error

                                    Icon(
                                        imageVector = icon,
                                        contentDescription = contentDescription,
                                        tint = iconTint,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }

                                2 -> { // field book column
                                    val isFBImplemented =
                                        call.source != CallImplementedBy.SERVER

                                    val icon =
                                        if (isFBImplemented) Icons.Default.Check else Icons.Default.Close
                                    val contentDescription =
                                        if (isFBImplemented) stringResource(R.string.brapi_compatibility_fieldbook_support) else stringResource(
                                            R.string.brapi_compatibility_no_fieldbook_support
                                        )
                                    val iconTint =
                                        if (isFBImplemented) AppTheme.colors.status.success else AppTheme.colors.status.error

                                    Icon(
                                        imageVector = icon,
                                        contentDescription = contentDescription,
                                        tint = iconTint,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ServerCallsTablePreview() {
    val calls = listOf<ServiceComparison>(
        ServiceComparison(
            service = "Service",
            methods = listOf("GET", "PUT"),
            isFbImplemented = true,
            implementedMethods = listOf("GET"),
            source = CallImplementedBy.FIELD_BOOK,
        )
    )
    AppTheme {
        ServerCallsTable(calls)
    }
}