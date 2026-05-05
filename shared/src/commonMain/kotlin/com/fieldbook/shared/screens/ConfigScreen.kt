package com.fieldbook.shared.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.fieldbook.shared.generated.resources.about_title
import com.fieldbook.shared.generated.resources.dialog_back
import com.fieldbook.shared.generated.resources.ic_nav_drawer_collect_data
import com.fieldbook.shared.generated.resources.ic_nav_drawer_fields
import com.fieldbook.shared.generated.resources.ic_nav_drawer_settings
import com.fieldbook.shared.generated.resources.ic_nav_drawer_statistics
import com.fieldbook.shared.generated.resources.ic_nav_drawer_traits
import com.fieldbook.shared.generated.resources.ic_tb_info
import com.fieldbook.shared.generated.resources.settings_advanced
import com.fieldbook.shared.generated.resources.settings_collect
import com.fieldbook.shared.generated.resources.settings_export
import com.fieldbook.shared.generated.resources.settings_fields
import com.fieldbook.shared.generated.resources.settings_statistics
import com.fieldbook.shared.generated.resources.settings_traits
import com.fieldbook.shared.generated.resources.trait_date_save
import com.fieldbook.shared.preferences.GeneralKeys
import com.fieldbook.shared.screens.onboarding.OnboardingScreen
import com.russhwolf.settings.Settings
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private data class ConfigItem(
    val title: StringResource,
    val icon: DrawableResource,
    val destination: KmpHostScreenType? = null
)

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
        ConfigItem(
            title = Res.string.settings_fields,
            icon = Res.drawable.ic_nav_drawer_fields,
            destination = KmpHostScreenType.FIELD_EDITOR
        ),
        ConfigItem(
            title = Res.string.settings_traits,
            icon = Res.drawable.ic_nav_drawer_traits,
            destination = KmpHostScreenType.TRAIT_EDITOR
        ),
        ConfigItem(
            title = Res.string.settings_collect,
            icon = Res.drawable.ic_nav_drawer_collect_data,
            destination = KmpHostScreenType.COLLECT
        ),
        ConfigItem(
            title = Res.string.settings_export,
            icon = Res.drawable.trait_date_save,
            destination = KmpHostScreenType.EXPORT
        ),
        ConfigItem(
            title = Res.string.settings_advanced,
            icon = Res.drawable.ic_nav_drawer_settings,
            destination = KmpHostScreenType.PREFERENCES
        ),
        ConfigItem(
            title = Res.string.settings_statistics,
            icon = Res.drawable.ic_nav_drawer_statistics
        ),
        ConfigItem(
            title = Res.string.about_title,
            icon = Res.drawable.ic_tb_info
        ),
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
                                contentDescription = stringResource(Res.string.dialog_back)
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
                items(configItems) { item ->
                    val isEnabled = item.destination != null
                    val rowModifier = Modifier
                        .let { mod ->
                            val destination = item.destination
                            if (destination != null && onNavigate != null) {
                                mod.clickable {
                                    onNavigate(destination)
                                }
                            } else {
                                mod
                            }
                        }
                        .graphicsLayer { alpha = if (isEnabled) 1f else 0.4f }

                    AppListItem(
                        text = stringResource(item.title),
                        icon = item.icon,
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
