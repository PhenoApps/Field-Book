package com.fieldbook.shared.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.database.models.FieldObject
import com.fieldbook.shared.database.repository.StudyRepository
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_file_csv
import com.fieldbook.shared.sqldelight.DriverFactory
import com.fieldbook.shared.sqldelight.createDatabase
import com.fieldbook.shared.theme.MainTheme
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldEditorScreen(
    driverFactory: DriverFactory,
    onBack: (() -> Unit)? = null
) {
    MainTheme {
        val fieldsState = remember { mutableStateOf<List<FieldObject>?>(null) }
        val errorState = remember { mutableStateOf<String?>(null) }
        val loadingState = remember { mutableStateOf(true) }
        val db = remember(driverFactory) {
            createDatabase(driverFactory)
        }

        LaunchedEffect(Unit) {
            loadingState.value = true
            errorState.value = null
            try {
                val repo = StudyRepository(db)
                fieldsState.value = repo.getAllFields()
                println("Fields loaded: ${fieldsState.value}")
            } catch (e: Exception) {
                e.printStackTrace()
                errorState.value = e.message ?: "Unknown error"
            } finally {
                loadingState.value = false
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Fields") },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when {
                    loadingState.value -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    errorState.value != null -> {
                        Text(
                            text = "Error: ${errorState.value}",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    fieldsState.value.isNullOrEmpty() -> {
                        Text(
                            text = "No fields found.",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(fieldsState.value!!) { field ->
                                FieldListItem(field = field)
                                androidx.compose.material3.HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldListItem(field: FieldObject) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_file_csv),
                contentDescription = "Field Icon",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (field.exp_alias.isNotEmpty()) field.exp_alias else field.exp_name,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
