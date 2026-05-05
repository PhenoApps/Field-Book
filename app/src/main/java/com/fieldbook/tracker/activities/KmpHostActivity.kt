package com.fieldbook.tracker.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.fieldbook.shared.AndroidAppContextHolder
import com.fieldbook.shared.AppContext
import com.fieldbook.shared.KmpApp
import com.fieldbook.shared.KmpHostScreenType
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
            MainTheme {
                KmpApp(
                    startScreen = hostScreenType,
                    onExit = { finish() },
                    onStorageResetExit = { finishAffinity() },
                    onScannerResult = { qrCode ->
                        val resultIntent = Intent().apply {
                            putExtra(ScannerActivity.EXTRA_BARCODE, qrCode)
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_SCREEN = "kmp_screen"
    }
}
