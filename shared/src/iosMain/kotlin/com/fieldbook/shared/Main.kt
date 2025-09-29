package com.fieldbook.shared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.fieldbook.shared.screens.ConfigScreen
import com.fieldbook.shared.screens.FieldEditorScreen
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

        KmpHostScreenType.SCANNER -> {
            // TODO: Implement scanner screen or fallback UI
        }

        else -> {
            // Fallback UI or error
        }
    }
}
