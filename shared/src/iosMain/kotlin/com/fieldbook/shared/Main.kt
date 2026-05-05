package com.fieldbook.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.fieldbook.shared.sqldelight.DriverFactory
import com.fieldbook.shared.theme.MainTheme

fun MainViewController(driverFactory: DriverFactory) = ComposeUIViewController {
    AppContext.init(driverFactory)

    MainTheme {
        KmpApp(
            onExit = {},
            onStorageResetExit = {},
        )
    }
}
