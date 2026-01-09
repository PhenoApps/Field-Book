package com.fieldbook.shared.screens.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    fieldIds: List<Int>,
    viewModel: ExportScreenViewModel = viewModel(),
    onBack: () -> Unit
) {
    val uiState = viewModel.uiState.collectAsState().value
    val event = viewModel.events.collectAsState().value

    LaunchedEffect(Unit) {
        viewModel.loadDefaults(fieldIds.size > 1)
    }

    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Export")

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = uiState.formatDb, onCheckedChange = { viewModel.onToggleFormatDb() })
                Spacer(modifier = Modifier.width(8.dp))
                Text("Database format")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = uiState.formatTable, onCheckedChange = { viewModel.onToggleFormatTable() })
                Spacer(modifier = Modifier.width(8.dp))
                Text("Table format")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text("Columns")
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = uiState.onlyUnique, onClick = { viewModel.onSelectOnlyUnique() })
                Spacer(modifier = Modifier.width(8.dp))
                Text("Only unique id")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = uiState.allColumns, onClick = { viewModel.onSelectAllColumns() })
                Spacer(modifier = Modifier.width(8.dp))
                Text("All columns")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text("Traits")
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = uiState.activeTraits, onClick = { viewModel.onSelectActiveTraits() })
                Spacer(modifier = Modifier.width(8.dp))
                Text("Active traits")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = uiState.allTraits, onClick = { viewModel.onSelectAllTraits() })
                Spacer(modifier = Modifier.width(8.dp))
                Text("All traits")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = uiState.bundleMedia, onCheckedChange = { viewModel.onToggleBundle() })
                Spacer(modifier = Modifier.width(8.dp))
                Text("Bundle media")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(value = uiState.fileName, onValueChange = { viewModel.onFileNameChange(it) }, label = { Text("File name") }, modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onBack) { Text("Cancel") }
                Button(onClick = { viewModel.onSave(fieldIds) }) { Text("Save") }
            }

            // TODO: show progress / events
        }
    }
}

