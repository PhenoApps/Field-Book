package com.fieldbook.shared.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fieldbook.shared.KmpHostScreenType
import com.fieldbook.shared.components.AppListItem
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_nav_drawer_collect_data
import com.fieldbook.shared.generated.resources.ic_nav_drawer_fields
import com.fieldbook.shared.generated.resources.ic_nav_drawer_settings
import com.fieldbook.shared.generated.resources.ic_nav_drawer_statistics
import com.fieldbook.shared.generated.resources.ic_nav_drawer_traits
import com.fieldbook.shared.generated.resources.ic_tb_info
import com.fieldbook.shared.generated.resources.trait_date_save
import com.fieldbook.shared.preferences.GeneralKeys
import com.fieldbook.shared.screens.onboarding.OnboardingScreen
import com.russhwolf.settings.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    viewModel: ConfigScreenViewModel = viewModel { ConfigScreenViewModel() },
    onBack: (() -> Unit)? = null,
    onNavigate: ((KmpHostScreenType) -> Unit)? = null,
) {
    val settings = remember { Settings() }
    var showOnboarding by remember {
        mutableStateOf(
            settings.getBoolean(GeneralKeys.FIRST_RUN_KMP.key, true)
        )
    }

    if (showOnboarding) {
        OnboardingScreen(
            onComplete = {
                showOnboarding = false
            }
        )
        return
    }

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
                title = {},
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
                    val isEnabled =
                        item == "Fields" || item == "Collect" || item == "Settings" || item == "Traits" || item == "Export"
                    val rowModifier = Modifier
                        .let { mod ->
                            if (isEnabled && onNavigate != null) {
                                mod.clickable {
                                    when (item) {
                                        "Fields" -> onNavigate(KmpHostScreenType.FIELD_EDITOR)
                                        "Collect" -> onNavigate(KmpHostScreenType.COLLECT)
                                        "Settings" -> onNavigate(KmpHostScreenType.PREFERENCES)
                                        "Traits" -> onNavigate(KmpHostScreenType.TRAIT_EDITOR)
                                        "Export" -> onNavigate(KmpHostScreenType.EXPORT)
                                    }
                                }
                            } else {
                                mod
                            }
                        }
                        .graphicsLayer { alpha = if (isEnabled) 1f else 0.4f }

                    AppListItem(
                        text = item,
                        icon = configIcons[index],
                        rowModifier = rowModifier
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

class ConfigScreenViewModel : ViewModel() {
    init {
        // selectFirstField()
    }
}
