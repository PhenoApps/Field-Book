package com.fieldbook.shared

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.database.models.ObservationUnitModel
import com.fieldbook.shared.database.models.TraitObject
import com.fieldbook.shared.database.repository.ObservationUnitRepository
import com.fieldbook.shared.database.repository.TraitRepository
import com.fieldbook.shared.sqldelight.DriverFactory
import com.fieldbook.shared.sqldelight.FieldbookDatabase

/**
 * KMP version of CollectActivity main screen logic.
 * UI and business logic will be migrated here.
 */
@Composable
fun CollectScreen(
    modifier: Modifier = Modifier,
    driverFactory: DriverFactory,
    onBack: (() -> Unit)? = null
) {
    val db = remember(driverFactory) {
        FieldbookDatabase(driverFactory.createDriver())
    }
    val observationUnitRepository = ObservationUnitRepository(db)
    val traitRepository = TraitRepository(db)

    // Observation units
    var units by remember { mutableStateOf<List<ObservationUnitModel>>(emptyList()) }
    var unitNavigator by remember { mutableStateOf<ObservationUnitNavigator?>(null) }
    var unitLoading by remember { mutableStateOf(true) }
    var unitError by remember { mutableStateOf<String?>(null) }

    // Traits
    var traits by remember { mutableStateOf<List<TraitObject>>(emptyList()) }
    var traitNavigator by remember { mutableStateOf<TraitNavigator?>(null) }
    var traitLoading by remember { mutableStateOf(true) }
    var traitError by remember { mutableStateOf<String?>(null) }

    // Load observation units
    LaunchedEffect(Unit) {
        try {
            units = observationUnitRepository.getAllObservationUnits()
            unitNavigator = ObservationUnitNavigator(units)
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
            traitNavigator = TraitNavigator(traits)
            println("Traits loaded: $traits")
            println("TraitNavigator initialized: $traitNavigator")
            traitLoading = false
        } catch (e: Exception) {
            e.printStackTrace()
            traitError = e.message
            traitLoading = false
        }
    }


    Surface(modifier = modifier.fillMaxSize()) {
        if (unitLoading || traitLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (unitError != null || traitError != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: ${unitError ?: traitError}")
            }
        } else if (unitNavigator != null && traitNavigator != null) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("Observation Unit Navigation", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                unitNavigator?.currentUnit?.let { unit ->
                    Text("ID: ${unit.observation_unit_db_id}", style = MaterialTheme.typography.bodyLarge)
                    Text("Name: ${unit.observation_unit_db_id}")
                }
                Spacer(Modifier.height(24.dp))
                Row {
                    Button(onClick = { unitNavigator?.previous() }, enabled = (unitNavigator?.currentIndex ?: 0) > 0) {
                        Text("Previous Unit")
                    }
                    Spacer(Modifier.width(16.dp))
                    Button(onClick = { unitNavigator?.next() }, enabled = (unitNavigator?.currentIndex ?: 0) < (units.size - 1)) {
                        Text("Next Unit")
                    }
                }
                Spacer(Modifier.height(16.dp))
                var searchUnitId by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = searchUnitId,
                        onValueChange = { searchUnitId = it },
                        label = { Text("Search Unit by ID") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        unitNavigator?.searchById(searchUnitId)
                    }, enabled = searchUnitId.isNotBlank()) {
                        Text("Go")
                    }
                }
                Divider(Modifier.padding(vertical = 24.dp))
                Text("Trait Navigation", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                traitNavigator?.currentTrait?.let { trait ->
                    Text("ID: ${trait.id}", style = MaterialTheme.typography.bodyLarge)
                    Text("Name: ${trait.name}", style = MaterialTheme.typography.bodyLarge)
                    Text("Format: ${trait.format}", style = MaterialTheme.typography.bodyMedium)
                    trait.minimum?.let { Text("Min: $it") }
                    trait.maximum?.let { Text("Max: $it") }
                }
                Spacer(Modifier.height(24.dp))
                Row {
                    Button(onClick = { traitNavigator?.previous() }, enabled = (traitNavigator?.currentIndex ?: 0) > 0) {
                        Text("Previous Trait")
                    }
                    Spacer(Modifier.width(16.dp))
                    Button(onClick = { traitNavigator?.next() }, enabled = (traitNavigator?.currentIndex ?: 0) < (traits.size - 1)) {
                        Text("Next Trait")
                    }
                }
                Spacer(Modifier.height(16.dp))
                var searchTraitId by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = searchTraitId,
                        onValueChange = { searchTraitId = it },
                        label = { Text("Search Trait by ID") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        traitNavigator?.searchById(searchTraitId)
                    }, enabled = searchTraitId.isNotBlank()) {
                        Text("Go")
                    }
                }
                Spacer(Modifier.height(8.dp))
                var searchTraitName by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = searchTraitName,
                        onValueChange = { searchTraitName = it },
                        label = { Text("Search Trait by Name") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        traitNavigator?.searchByName(searchTraitName)
                    }, enabled = searchTraitName.isNotBlank()) {
                        Text("Go")
                    }
                }
            }
        }
    }
}

// Navigation state and logic for Observation Units
class ObservationUnitNavigator(
    private val units: List<ObservationUnitModel>,
    initialIndex: Int = 0
) {
    var currentIndex: Int = initialIndex
        private set
    val currentUnit: ObservationUnitModel?
        get() = units.getOrNull(currentIndex)

    fun next() {
        if (currentIndex < units.size - 1) currentIndex++
    }
    fun previous() {
        if (currentIndex > 0) currentIndex--
    }
    fun searchById(id: String): Boolean {
        val idx = units.indexOfFirst { it.observation_unit_db_id == id }
        return if (idx != -1) {
            currentIndex = idx
            true
        } else false
    }
}

// Navigation state and logic for Traits
class TraitNavigator(
    private val traits: List<TraitObject>,
    initialIndex: Int = 0
) {
    var currentIndex: Int = initialIndex
        private set
    val currentTrait: TraitObject?
        get() = traits.getOrNull(currentIndex)

    fun next() {
        if (currentIndex < traits.size - 1) currentIndex++
    }
    fun previous() {
        if (currentIndex > 0) currentIndex--
    }
    fun searchById(id: String): Boolean {
        val idx = traits.indexOfFirst { it.id == id }
        return if (idx != -1) {
            currentIndex = idx
            true
        } else false
    }
    fun searchByName(name: String): Boolean {
        val idx = traits.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        return if (idx != -1) {
            currentIndex = idx
            true
        } else false
    }
}

