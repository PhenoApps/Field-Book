package com.fieldbook.shared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.fieldbook.shared.screens.ConfigScreen
import com.fieldbook.shared.screens.FieldEditorScreen
import com.fieldbook.shared.screens.preferences.PreferencesScreen
import com.fieldbook.shared.screens.preferences.StoragePreferencesScreen
import com.fieldbook.shared.sqldelight.DriverFactory

fun MainViewController(driverFactory: DriverFactory) = ComposeUIViewController {
    var currentScreen by remember { mutableStateOf(KmpHostScreenType.CONFIG) }

    when (currentScreen) {
        KmpHostScreenType.CONFIG -> ConfigScreen(
            onNavigate = { target -> currentScreen = target }
        )

        KmpHostScreenType.FIELD_EDITOR -> FieldEditorScreen(
            driverFactory = driverFactory,
            onBack = { currentScreen = KmpHostScreenType.CONFIG }
        )

        KmpHostScreenType.COLLECT -> com.fieldbook.shared.screens.collect.CollectScreen(
            driverFactory = driverFactory,
            onBack = { currentScreen = KmpHostScreenType.CONFIG }
        )

        KmpHostScreenType.PREFERENCES -> PreferencesScreen(
            onBack = { currentScreen = KmpHostScreenType.CONFIG },
            onNavigate = { target -> currentScreen = target }
        )

        KmpHostScreenType.STORAGE_PREFERENCES -> StoragePreferencesScreen(
            driverFactory,
            onBack = { currentScreen = KmpHostScreenType.PREFERENCES }
        )

        KmpHostScreenType.SCANNER -> {
            // TODO: Implement scanner screen or fallback UI
        }

    }
}
