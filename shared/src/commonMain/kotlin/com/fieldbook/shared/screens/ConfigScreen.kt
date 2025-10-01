package com.fieldbook.shared.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fieldbook.shared.KmpHostScreenType
import com.fieldbook.shared.database.repository.StudiesRepository
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_nav_drawer_collect_data
import com.fieldbook.shared.generated.resources.ic_nav_drawer_fields
import com.fieldbook.shared.generated.resources.ic_nav_drawer_settings
import com.fieldbook.shared.generated.resources.ic_nav_drawer_statistics
import com.fieldbook.shared.generated.resources.ic_nav_drawer_traits
import com.fieldbook.shared.generated.resources.ic_tb_info
import com.fieldbook.shared.generated.resources.trait_date_save
import com.fieldbook.shared.sqldelight.DriverFactory
import com.fieldbook.shared.sqldelight.FieldbookDatabase
import com.fieldbook.shared.theme.MainTheme
import com.fieldbook.shared.utilities.FieldSwitchImpl
import com.fieldbook.shared.utilities.selectFirstField
import org.jetbrains.compose.resources.painterResource


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    driverFactory: DriverFactory,
    viewModel: ConfigScreenViewModel = viewModel { ConfigScreenViewModel(driverFactory) },
    onBack: (() -> Unit)? = null,
    onNavigate: ((KmpHostScreenType) -> Unit)? = null
) {
    MainTheme {
        val configItems = listOf(
            "Fields",
            "Traits",
            "Collect",
            "Export",
            "Settings",
            "Statistics",
            "About",
        )
        val configIcons = listOf(
            Res.drawable.ic_nav_drawer_fields,
            Res.drawable.ic_nav_drawer_traits,
            Res.drawable.ic_nav_drawer_collect_data,
            Res.drawable.trait_date_save,
            Res.drawable.ic_nav_drawer_settings,
            Res.drawable.ic_nav_drawer_statistics,
            Res.drawable.ic_tb_info,
        )
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(text = "KMP Module") },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(configItems) { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .let { mod ->
                                    when {
                                        item == "Fields" && onNavigate != null -> mod.clickable {
                                            onNavigate(
                                                KmpHostScreenType.FIELD_EDITOR
                                            )
                                        }

                                        item == "Collect" && onNavigate != null -> mod.clickable {
                                            onNavigate(
                                                KmpHostScreenType.COLLECT
                                            )
                                        }

                                        item == "Settings" && onNavigate != null -> mod.clickable {
                                            onNavigate(
                                                KmpHostScreenType.PREFERENCES
                                            )
                                        }

                                        else -> mod
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(configIcons[index]),
                                contentDescription = item,
                                modifier = Modifier.padding(end = 16.dp).size(24.dp)
                            )
                            Text(
                                text = item,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Divider()
                    }
                }
            }
        }
    }
}

class ConfigScreenViewModel(
    driverFactory: DriverFactory
) : ViewModel() {
    private val db = FieldbookDatabase(driverFactory.createDriver())
    private val studiesRepository: StudiesRepository = StudiesRepository(db)

    init {
        selectFirstField(driverFactory)
    }
}
