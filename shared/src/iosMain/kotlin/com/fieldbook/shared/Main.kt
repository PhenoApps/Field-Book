package com.fieldbook.shared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.fieldbook.shared.screens.ConfigScreen
import com.fieldbook.shared.screens.collect.CollectScreen
import com.fieldbook.shared.screens.export.ExportScreen
import com.fieldbook.shared.screens.fields.FieldEditorScreen
import com.fieldbook.shared.screens.preferences.PreferencesScreen
import com.fieldbook.shared.screens.preferences.StorageDefinerScreen
import com.fieldbook.shared.screens.preferences.StoragePreferencesScreen
import com.fieldbook.shared.screens.trait.TraitEditorScreen
import com.fieldbook.shared.sqldelight.DriverFactory
import com.fieldbook.shared.theme.MainTheme

fun MainViewController(driverFactory: DriverFactory) = ComposeUIViewController {
    AppContext.init(driverFactory)

    var currentScreen by remember { mutableStateOf(KmpHostScreenType.CONFIG) }

    MainTheme {
        when (currentScreen) {
            KmpHostScreenType.CONFIG -> ConfigScreen(
                onNavigate = { target -> currentScreen = target },
                onBack = { currentScreen = KmpHostScreenType.CONFIG }
            )

            KmpHostScreenType.FIELD_EDITOR -> FieldEditorScreen(
                onBack = { currentScreen = KmpHostScreenType.CONFIG }
            )

            KmpHostScreenType.COLLECT -> CollectScreen(
                onBack = { currentScreen = KmpHostScreenType.CONFIG }
            )

            KmpHostScreenType.PREFERENCES -> PreferencesScreen(
                onBack = { currentScreen = KmpHostScreenType.CONFIG },
                onNavigate = { target -> currentScreen = target }
            )

            KmpHostScreenType.STORAGE_PREFERENCES -> StoragePreferencesScreen(
                onBack = { currentScreen = KmpHostScreenType.PREFERENCES },
                onNavigate = { target -> currentScreen = target }
            )

            KmpHostScreenType.STORAGE_DEFINER -> StorageDefinerScreen(
                onBack = { currentScreen = KmpHostScreenType.STORAGE_PREFERENCES }
            )

            KmpHostScreenType.SCANNER -> {
                // TODO: Implement scanner screen or fallback UI
            }

            KmpHostScreenType.TRAIT_EDITOR -> TraitEditorScreen(
                onBack = { currentScreen = KmpHostScreenType.CONFIG }
            )

            KmpHostScreenType.EXPORT -> {
                ExportScreen(
                    fieldIds = listOf(), // placeholder empty selection
                    onBack = { currentScreen = KmpHostScreenType.CONFIG }
                )
            }

            else -> {
                // fallback for future enum values
            }
        }
    }
}
