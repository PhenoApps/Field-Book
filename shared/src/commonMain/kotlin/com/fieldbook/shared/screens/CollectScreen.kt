package com.fieldbook.shared.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.fieldbook.shared.generated.resources.Res
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.database.models.ObservationUnitModel
import com.fieldbook.shared.database.models.TraitObject
import com.fieldbook.shared.database.repository.ObservationUnitRepository
import com.fieldbook.shared.database.repository.TraitRepository
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
        val db = remember(driverFactory) {
            FieldbookDatabase(driverFactory.createDriver())
        }
        val observationUnitRepository = ObservationUnitRepository(db)
        val traitRepository = TraitRepository(db)

        // Observation units
        var units by remember { mutableStateOf<List<ObservationUnitModel>>(emptyList()) }
        var unitLoading by remember { mutableStateOf(true) }
        var unitError by remember { mutableStateOf<String?>(null) }
        var currentUnitIndex by remember { mutableStateOf(0) }

        // Traits
        var traits by remember { mutableStateOf<List<TraitObject>>(emptyList()) }
        var traitLoading by remember { mutableStateOf(true) }
        var traitError by remember { mutableStateOf<String?>(null) }
        var currentTraitIndex by remember { mutableStateOf(0) }

        // Load observation units
        LaunchedEffect(Unit) {
            try {
                units = observationUnitRepository.getAllObservationUnits()
                unitLoading = false
            } catch (e: Exception) {
                e.printStackTrace()
                unitError = e.message
                unitLoading = false
            }
        }
        // Load traits
        LaunchedEffect(Unit) {
            try {
                traits = traitRepository.getAllTraits()
                traitLoading = false
            } catch (e: Exception) {
                e.printStackTrace()
                traitError = e.message
                traitLoading = false
            }
        }

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
                if (unitLoading || traitLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (unitError != null || traitError != null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error: ${unitError ?: traitError}")
                    }
                } else if (units.isNotEmpty() && traits.isNotEmpty()) {
                    // Compact, grouped layout with chevron navigation
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Observation Unit Navigation Row
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { if (currentUnitIndex > 0) currentUnitIndex-- },
                                enabled = currentUnitIndex > 0,
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
                                val unit = units.getOrNull(currentUnitIndex)
                                unit?.let {
                                    Text("ID: ${it.observation_unit_db_id}", style = MaterialTheme.typography.bodyLarge)
                                    Text("Name: ${it.observation_unit_db_id}", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            IconButton(
                                onClick = { if (currentUnitIndex < units.size - 1) currentUnitIndex++ },
                                enabled = currentUnitIndex < units.size - 1,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.chevron_right),
                                    contentDescription = "Next Unit",
                                    modifier = Modifier.size(56.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        // Trait Navigation Row
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { if (currentTraitIndex > 0) currentTraitIndex-- },
                                enabled = currentTraitIndex > 0,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.chevron_left),
                                    contentDescription = "Previous Trait",
                                    modifier = Modifier.size(56.dp)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Trait", style = MaterialTheme.typography.titleLarge)
                                val trait = traits.getOrNull(currentTraitIndex)
                                trait?.let {
                                    Text("ID: ${it.id}", style = MaterialTheme.typography.bodyLarge)
                                    Text("Name: ${it.name}", style = MaterialTheme.typography.bodyMedium)
                                    Text("Format: ${it.format}", style = MaterialTheme.typography.bodySmall)
                                    it.minimum?.let { min -> Text("Min: $min", style = MaterialTheme.typography.bodySmall) }
                                    it.maximum?.let { max -> Text("Max: $max", style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                            IconButton(
                                onClick = { if (currentTraitIndex < traits.size - 1) currentTraitIndex++ },
                                enabled = currentTraitIndex < traits.size - 1,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.chevron_right),
                                    contentDescription = "Next Trait",
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
