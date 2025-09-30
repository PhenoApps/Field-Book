package com.fieldbook.tracker.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.fieldbook.shared.KmpHostScreenType
import com.fieldbook.shared.screens.collect.CollectScreen
import com.fieldbook.shared.screens.ConfigScreen
import com.fieldbook.shared.screens.ScannerScreen
import com.fieldbook.shared.screens.FieldEditorScreen
import com.fieldbook.shared.screens.PreferencesScreen
import com.fieldbook.shared.sqldelight.DriverFactory

class KmpHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = intent.getStringExtra(EXTRA_SCREEN)
        val hostScreenType = KmpHostScreenType.fromValue(screen ?: KmpHostScreenType.CONFIG.value)
        setContent {
            var currentScreen = remember { mutableStateOf(hostScreenType) }
            when (currentScreen.value) {
                KmpHostScreenType.CONFIG -> ConfigScreen(
                    onBack = { finish() },
                    onNavigate = { target -> currentScreen.value = target }
                )

                KmpHostScreenType.SCANNER -> {
                    ScannerScreen(
                        onBack = { finish() },
                        onResult = { qrCode ->
                            val resultIntent = Intent().apply {
                                putExtra(ScannerActivity.EXTRA_BARCODE, qrCode)
                            }
                            setResult(RESULT_OK, resultIntent)
                            finish()
                        }
                    )
                }

                KmpHostScreenType.FIELD_EDITOR -> {
                    FieldEditorScreen(
                        driverFactory = DriverFactory(context = this),
                        onBack = { currentScreen.value = KmpHostScreenType.CONFIG }
                    )
                }

                KmpHostScreenType.COLLECT -> {
                    CollectScreen(
                        driverFactory = DriverFactory(context = this),
                        onBack = { currentScreen.value = KmpHostScreenType.CONFIG }
                    )
                }

                KmpHostScreenType.PREFERENCES -> {
                    PreferencesScreen(onBack = { currentScreen.value = KmpHostScreenType.CONFIG })
                }
            }
        }
    }

    companion object {
        const val EXTRA_SCREEN = "kmp_screen"
    }
}
