package com.fieldbook.shared.screens.trait

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fieldbook.shared.database.models.TraitObject
import com.fieldbook.shared.traits.Formats
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private enum class TraitCreatorStep {
    ChooseFormat,
    NameDetails
}

@Composable
fun TraitCreatorDialog(
    onDismiss: () -> Unit,
    onSuccess: (TraitObject) -> Unit,
    viewModel: TraitEditorScreenViewModel = viewModel()
) {
    var currentStep by remember { mutableStateOf(TraitCreatorStep.ChooseFormat) }
    var selectedFormat by remember { mutableStateOf<Formats?>(null) }
    var name by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }

    when (currentStep) {
        TraitCreatorStep.ChooseFormat -> {
            AlertDialog(
                modifier = Modifier.fillMaxWidth(), // TODO not working?
                onDismissRequest = onDismiss,
                title = { Text("Trait Layout") },
                text = {
                    val formats = Formats.supportedFormats()

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(formats) { format ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .clickable {
                                        selectedFormat = format
                                        currentStep = TraitCreatorStep.NameDetails
                                    }
                            ) {
                                val iconRes = format.getIcon()
                                Image(
                                    painter = painterResource(iconRes),
                                    contentDescription = format.name,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(stringResource(format.getTraitFormatDefinition().nameStringResource))
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            )
        }

        TraitCreatorStep.NameDetails -> {
            val traitState = remember {
                TraitObject().apply {
                    name = name
                    details = details
                    format = selectedFormat?.databaseName
                    visible = "true"
                    traitDataSource = "local"
                }
            }

            var paramError by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    val title = selectedFormat?.let { stringResource(it.getTraitFormatDefinition().nameStringResource) }
                    Text("${title ?: ""} Parameters")
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                traitState.name = it
                            },
                            label = { Text("Name") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = details,
                            onValueChange = {
                                details = it
                                traitState.details = it
                            },
                            label = { Text("Details") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        selectedFormat?.getTraitFormatDefinition()?.ParametersEditor(traitState) { updated ->
                            paramError = updated.additionalInfo ?: ""
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.insertTrait(traitState)
                        onSuccess(traitState)
                    }, enabled = name.isNotBlank() && paramError.isBlank()) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            currentStep = TraitCreatorStep.ChooseFormat
                        }) {
                            Text("Back")
                        }

                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                    }
                }
            )
        }
    }
}
