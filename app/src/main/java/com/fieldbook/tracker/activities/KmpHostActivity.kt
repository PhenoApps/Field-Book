package com.fieldbook.tracker.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.fieldbook.shared.ConfigScreen
import com.fieldbook.shared.ScannerScreen
import com.fieldbook.shared.activities.FieldEditorScreen
import com.fieldbook.shared.sqldelight.DriverFactory

class KmpHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = intent.getStringExtra(EXTRA_SCREEN)
        val hostScreenType = KmpHostScreenType.fromValue(screen ?: KmpHostScreenType.CONFIG.value)
        setContent {
            when (hostScreenType) {
                KmpHostScreenType.CONFIG -> ConfigScreen(onBack = { finish() })
                KmpHostScreenType.SCANNER -> {
                    ScannerScreen(
                        onBack = { finish() },
                        onResult = { qrCode ->
                            val resultIntent = android.content.Intent().apply {
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
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_SCREEN = "kmp_screen"
    }
}
