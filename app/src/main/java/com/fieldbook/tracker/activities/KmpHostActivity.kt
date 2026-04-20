package com.fieldbook.tracker.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.fieldbook.shared.AndroidAppContextHolder
import com.fieldbook.shared.AppContext
import com.fieldbook.shared.KmpHostScreenType
import com.fieldbook.shared.screens.ConfigScreen
import com.fieldbook.shared.screens.ScannerScreen
import com.fieldbook.shared.screens.collect.CollectScreen
import com.fieldbook.shared.screens.export.ExportScreen
import com.fieldbook.shared.screens.fields.FieldEditorScreen
import com.fieldbook.shared.screens.preferences.PreferencesScreen
import com.fieldbook.shared.screens.preferences.StorageDefinerScreen
import com.fieldbook.shared.screens.preferences.StoragePreferencesScreen
import com.fieldbook.shared.screens.trait.TraitEditorScreen
import com.fieldbook.shared.sqldelight.DriverFactory
import com.fieldbook.shared.theme.MainTheme
import io.github.vinceglb.filekit.core.FileKit

class KmpHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FileKit.init(this)
        AppContext.init(DriverFactory(context = this))
        AndroidAppContextHolder.initialize(this)

        val screen = intent.getStringExtra(EXTRA_SCREEN)
        val hostScreenType = KmpHostScreenType.fromValue(screen ?: KmpHostScreenType.CONFIG.value)

        setContent {
            val currentScreen = remember { mutableStateOf(hostScreenType) }
            MainTheme {
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
                            onBack = { currentScreen.value = KmpHostScreenType.CONFIG }
                        )
                    }

                    KmpHostScreenType.TRAIT_EDITOR -> {
                        TraitEditorScreen(
                            onBack = { currentScreen.value = KmpHostScreenType.CONFIG }
                        )
                    }

                    KmpHostScreenType.COLLECT -> {
                        CollectScreen(
                            onBack = { currentScreen.value = KmpHostScreenType.CONFIG }
                        )
                    }

                    KmpHostScreenType.PREFERENCES -> {
                        PreferencesScreen(
                            onBack = { currentScreen.value = KmpHostScreenType.CONFIG },
                            onNavigate = { target -> currentScreen.value = target }
                        )
                    }

                    KmpHostScreenType.STORAGE_PREFERENCES -> {
                        StoragePreferencesScreen(
                            onNavigate = { target -> currentScreen.value = target },
                            onBack = {
                                currentScreen.value = KmpHostScreenType.PREFERENCES

                            })
                    }

                    KmpHostScreenType.STORAGE_DEFINER -> {
                        StorageDefinerScreen(
                            onBack = { currentScreen.value = KmpHostScreenType.STORAGE_PREFERENCES }
                        )
                    }

                    KmpHostScreenType.EXPORT -> {
                        ExportScreen(
                            fieldIds = listOf(), // placeholder empty selection
                            onBack = { currentScreen.value = KmpHostScreenType.CONFIG }
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_SCREEN = "kmp_screen"
    }
}
