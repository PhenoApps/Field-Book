package com.fieldbook.shared.screens.collect

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.fieldbook.shared.generated.resources.Res
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.sqldelight.DriverFactory
import com.fieldbook.shared.sqldelight.FieldbookDatabase
import com.fieldbook.shared.theme.MainTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import com.fieldbook.shared.generated.resources.chevron_left
import com.fieldbook.shared.generated.resources.chevron_right
import org.jetbrains.compose.resources.painterResource

/**
 * KMP version of CollectActivity main screen logic.
 * UI and business logic will be migrated here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectScreen(
    modifier: Modifier = Modifier,
    driverFactory: DriverFactory,
    onBack: (() -> Unit)? = null
) {
    MainTheme {
        val viewModel = remember { CollectViewModel(driverFactory) }
        Surface(modifier = modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(text = "Collect Data") },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = { onBack() }) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                if (viewModel.unitLoading || viewModel.traitLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (viewModel.unitError != null || viewModel.traitError != null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error: ${viewModel.unitError ?: viewModel.traitError}")
                    }
                } else if (viewModel.units.isNotEmpty() && viewModel.traits.isNotEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(24.dp))
                        TraitBox(
                            traits = viewModel.traits,
                            currentTraitIndex = viewModel.currentTraitIndex,
                            onPrevTrait = { viewModel.updateCurrentTraitIndex(viewModel.currentTraitIndex - 1) },
                            onNextTrait = { viewModel.updateCurrentTraitIndex(viewModel.currentTraitIndex + 1) },
                            traitValues = viewModel.traitValues,
                            traitValuesLoading = viewModel.traitValuesLoading,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(24.dp))
                        // Observation Unit Navigation Row
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { viewModel.updateCurrentUnitIndex(viewModel.currentUnitIndex - 1) },
                                enabled = viewModel.currentUnitIndex > 0,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.chevron_left),
                                    contentDescription = "Previous Unit",
                                    modifier = Modifier.size(56.dp)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Unit", style = MaterialTheme.typography.titleLarge)
                                val unit = viewModel.units.getOrNull(viewModel.currentUnitIndex)
                                unit?.let {
                                    Text(
                                        "ID: ${it.observation_unit_db_id}",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        "Name: ${it.observation_unit_db_id}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.updateCurrentUnitIndex(viewModel.currentUnitIndex + 1) },
                                enabled = viewModel.currentUnitIndex < viewModel.units.size - 1,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.chevron_right),
                                    contentDescription = "Next Unit",
                                    modifier = Modifier.size(56.dp)
                                )
                            }
                        }
                        // ...existing search fields and other UI if needed...
                    }
                }
            }
        }
    }
}
