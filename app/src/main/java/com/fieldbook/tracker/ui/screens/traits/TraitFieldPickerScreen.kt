package com.fieldbook.tracker.ui.screens.traits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.ui.components.appBar.ActionDisplayMode
import com.fieldbook.tracker.ui.components.appBar.AppBar
import com.fieldbook.tracker.ui.components.appBar.TopAppBarAction
import com.fieldbook.tracker.ui.theme.AppTheme

@Composable
fun TraitFieldPickerScreen(
    availableTraits: List<TraitObject>,
    onBack: () -> Unit,
    onCreateNewTrait: () -> Unit,
    onAddSelected: (Set<String>) -> Unit,
) {
    val selectedTraitIds = remember { mutableStateSetOf<String>() }
    var searchQuery by remember { mutableStateOf("") }

    val filteredTraits = remember(availableTraits, searchQuery) {
        if (searchQuery.isBlank()) availableTraits
        else availableTraits.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.alias.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(AppTheme.colors.background)) {
                AppBar(
                    title = if (selectedTraitIds.isEmpty()) {
                        stringResource(R.string.traits_viewer_create_new_trait)
                    } else {
                        stringResource(R.string.selected_count, selectedTraitIds.size)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_left),
                                contentDescription = stringResource(R.string.appbar_back),
                                tint = AppTheme.colors.text.tertiary
                            )
                        }
                    },
                    actions = if (selectedTraitIds.isNotEmpty()) {
                        listOf(
                            TopAppBarAction(
                                title = stringResource(R.string.fields_select_all),
                                icon = Icons.Default.SelectAll,
                                onClick = { 
                                    if (selectedTraitIds.size == filteredTraits.size) {
                                        selectedTraitIds.clear()
                                    } else {
                                        selectedTraitIds.addAll(filteredTraits.map { it.id })
                                    }
                                },
                                displayMode = ActionDisplayMode.ALWAYS,
                                contentDescription = stringResource(R.string.fields_select_all)
                            )
                        )
                    } else {
                        listOf(
                            TopAppBarAction(
                                title = stringResource(R.string.fields_select_all),
                                icon = Icons.Default.SelectAll,
                                onClick = { 
                                    selectedTraitIds.addAll(filteredTraits.map { it.id })
                                },
                                displayMode = ActionDisplayMode.IF_ROOM,
                                contentDescription = stringResource(R.string.fields_select_all)
                            ),
                            TopAppBarAction(
                                title = stringResource(R.string.traits_viewer_create_new_trait),
                                icon = Icons.Default.Add,
                                onClick = { onCreateNewTrait() },
                                displayMode = ActionDisplayMode.ALWAYS,
                                contentDescription = stringResource(R.string.traits_viewer_create_new_trait)
                            )
                        )
                    }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { 
                            Text(
                                text = stringResource(R.string.main_toolbar_search),
                                color = AppTheme.colors.text.secondary.copy(alpha = 0.6f)
                            ) 
                        },
                        leadingIcon = { 
                            Icon(
                                imageVector = Icons.Default.Search, 
                                contentDescription = null,
                                tint = AppTheme.colors.text.secondary
                            ) 
                        },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear, 
                                        contentDescription = null,
                                        tint = AppTheme.colors.text.secondary
                                    )
                                }
                            }
                        } else null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = AppTheme.colors.background,
                            unfocusedContainerColor = AppTheme.colors.background,
                            disabledContainerColor = AppTheme.colors.background,
                            focusedBorderColor = AppTheme.colors.primary,
                            unfocusedBorderColor = AppTheme.colors.surface.border,
                            cursorColor = AppTheme.colors.primary,
                            focusedTextColor = AppTheme.colors.text.primary,
                            unfocusedTextColor = AppTheme.colors.text.primary,
                        ),
                        shape = MaterialTheme.shapes.medium,
                        singleLine = true
                    )
                }
            }
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                color = AppTheme.colors.primary
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    OutlinedButton(
                        onClick = {
                            onAddSelected(selectedTraitIds.toSet())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AppTheme.colors.primary,
                            containerColor = AppTheme.colors.background
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.traits_viewer_add_selected),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        if (availableTraits.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_file_generic),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = AppTheme.colors.text.secondary.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.traits_viewer_no_available_traits),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppTheme.colors.text.secondary,
                    fontWeight = FontWeight.Medium
                )
            }
        } else if (filteredTraits.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.search_results_missing),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppTheme.colors.text.secondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredTraits, key = { it.id }) { trait ->
                    val isSelected = trait.id in selectedTraitIds
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isSelected) selectedTraitIds.remove(trait.id)
                                else selectedTraitIds.add(trait.id)
                            },
                        shape = MaterialTheme.shapes.medium,
                        color = if (isSelected) AppTheme.colors.interactive.selectedItemBackground else AppTheme.colors.background,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, 
                            if (isSelected) AppTheme.colors.primary else AppTheme.colors.surface.border
                        ),
                        tonalElevation = if (isSelected) 2.dp else 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (checked) selectedTraitIds.add(trait.id)
                                    else selectedTraitIds.remove(trait.id)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = AppTheme.colors.primary,
                                    uncheckedColor = AppTheme.colors.text.secondary
                                )
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = trait.alias.takeIf { it.isNotBlank() } ?: trait.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = AppTheme.colors.text.primary,
                                    fontWeight = FontWeight.SemiBold
                                )

                                val formatLabel = Formats.entries
                                    .find { it.getDatabaseName() == trait.format }
                                    ?.name
                                    ?.replace('_', ' ')
                                    ?.lowercase()
                                    ?.replaceFirstChar { it.uppercase() }
                                    ?: trait.format

                                Text(
                                    text = formatLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.text.secondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
