package com.fieldbook.tracker.activities

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.fieldbook.tracker.R
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.printing.LabelPrintService
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.ui.theme.enums.AppTextType
import com.fieldbook.tracker.ui.theme.enums.AppThemeType
import com.fieldbook.tracker.zpl.TemplateRepository
import com.zebra.sdk.comm.BluetoothConnection
import com.zebra.sdk.comm.ConnectionException
import com.zebra.sdk.printer.SGD
import dagger.hilt.android.AndroidEntryPoint
import org.phenoapps.labelprint.ui.LabelSettings
import org.phenoapps.labelprint.ui.ZplEditorScreen
import org.phenoapps.labelprint.zpl.ZplGenerator
import java.io.IOException
import javax.inject.Inject

@AndroidEntryPoint
class ZplEditorActivity : ThemedActivity() {

    companion object {
        private const val TAG = "ZplEditorActivity"
        private const val BLUETOOTH_PERMISSION_REQUEST_CODE = 100
        const val EXTRA_INITIAL_ZPL = "initial_zpl"
        const val EXTRA_TEMPLATE_NAME = "template_name"
        const val RESULT_ZPL = "result_zpl"
        const val RESULT_TEMPLATE_NAME = "result_template_name"
        const val RESULT_TEMPLATE_ID = "result_template_id"
    }

    @Inject
    lateinit var templateRepository: TemplateRepository

    private var pendingExportZpl: String = ""

    /** Id of the template saved in this editor session, only this is returned to the caller. */
    private var savedTemplateId: String? = null

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@registerForActivityResult

        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(pendingExportZpl.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, getString(R.string.export_successful), Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Toast.makeText(this, getString(R.string.export_failed, e.message), Toast.LENGTH_SHORT)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        templateRepository.ensureBuiltInTemplatesExist()

        val initialZpl = intent.getStringExtra(EXTRA_INITIAL_ZPL) ?: ""
        val templateName = intent.getStringExtra(EXTRA_TEMPLATE_NAME)

        setContent {
            val themeIndex = prefs.getString(PreferenceKeys.THEME, "0")?.toInt() ?: 0
            val themeType = when (themeIndex) {
                1 -> AppThemeType.HighContrast
                2 -> AppThemeType.Blue
                else -> AppThemeType.Default
            }
            val textIndex = prefs.getString(PreferenceKeys.TEXT_THEME, "1")?.toInt() ?: 1
            val textType = AppTextType.entries.find { it.index == textIndex } ?: AppTextType.MEDIUM

            AppTheme(themeTypeOverride = themeType, textTypeOverride = textType) {
                var allTemplates by remember {
                    mutableStateOf(templateRepository.getAllTemplates())
                }

                ZplEditorScreen(
                    initialZpl = initialZpl,
                    initialTemplateName = templateName,
                    templates = allTemplates.values.associateWith { name ->
                        val id = allTemplates.entries.find { it.value == name }?.key
                        if (id != null) templateRepository.getTemplate(id) ?: "" else ""
                    },
                    onDismiss = {
                        val savedId = savedTemplateId
                        val savedName = savedId?.let { templateRepository.getTemplateName(it) }
                        val savedZpl = savedId?.let { templateRepository.getTemplate(it) }

                        if (savedId != null && savedName != null && savedZpl != null) {
                            val resultIntent = Intent().apply {
                                putExtra(RESULT_TEMPLATE_ID, savedId)
                                putExtra(RESULT_ZPL, savedZpl)
                                putExtra(RESULT_TEMPLATE_NAME, savedName)
                            }
                            setResult(RESULT_OK, resultIntent)
                        } else {
                            setResult(RESULT_CANCELED)
                        }
                        finish()
                    },
                    onSave = { name, normalizedZpl ->
                        savedTemplateId = templateRepository.saveTemplate(name, normalizedZpl)
                        allTemplates = templateRepository.getAllTemplates()
                    },
                    onDelete = { name ->
                        val id = allTemplates.entries.find { it.value == name }?.key
                        if (id != null) {
                            templateRepository.deleteTemplate(id)
                            allTemplates = templateRepository.getAllTemplates()
                        }
                    },
                    onExport = { suggestedName, zpl -> launchExport(suggestedName, zpl) },
                    onReadFromDevice = { readSettingsFromDevice() }
                )
            }
        }
    }

    private fun launchExport(suggestedName: String, zpl: String) {
        pendingExportZpl = zpl
        exportLauncher.launch("$suggestedName.zpl")
    }

    @SuppressLint("MissingPermission")
    private suspend fun readSettingsFromDevice(): LabelSettings? {
        val printerName = prefs.getString(GeneralKeys.LABEL_PRINT_DEVICE_NAME, null)
        if (printerName.isNullOrBlank()) {
            Log.w(TAG, "No printer device name configured")
            return null
        }

        // bondedDevices and connecting throw SecurityException without these (BLUETOOTH_CONNECT on 12+)
        val missingPermissions = LabelPrintService.getRequiredPermissions(Build.VERSION.SDK_INT)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missingPermissions.isNotEmpty()) {
            Log.w(TAG, "Missing Bluetooth permissions: $missingPermissions")
            runOnUiThread {
                ActivityCompat.requestPermissions(
                    this, missingPermissions.toTypedArray(), BLUETOOTH_PERMISSION_REQUEST_CODE
                )
            }
            return null
        }

        var connection: BluetoothConnection? = null

        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
            val device = adapter.bondedDevices.firstOrNull { it.name == printerName } ?: return null
            connection = BluetoothConnection(device.address).apply { open() }

            val widthStr = SGD.GET("ezpl.print_width", connection)
            val heightStr = SGD.GET("ezpl.label_length", connection)
            val mediaTypeStr = SGD.GET("ezpl.media_type", connection)
            val trackingStr = SGD.GET("media.type", connection)
            val dpiStr = SGD.GET("head.resolution.in_dpi", connection)

            val width = widthStr?.trim()?.toIntOrNull() ?: 609
            val height = heightStr?.trim()?.toIntOrNull() ?: 406
            val dpi = dpiStr?.trim()?.toIntOrNull()?.takeIf { it > 0 }

            val mediaType = when {
                mediaTypeStr?.contains("trans", ignoreCase = true) == true -> ZplGenerator.MEDIA_THERMAL_TRANSFER
                else -> ZplGenerator.MEDIA_THERMAL_DIRECT
            }

            val mediaGap = when {
                trackingStr?.contains("continuous", ignoreCase = true) == true -> ZplGenerator.TRACKING_CONTINUOUS
                trackingStr?.contains("mark", ignoreCase = true) == true -> ZplGenerator.TRACKING_MARK
                else -> ZplGenerator.TRACKING_GAP
            }

            LabelSettings(
                labelWidth = width,
                labelLength = height,
                mediaType = mediaType,
                mediaGap = mediaGap,
                dpi = dpi
            )
        } catch (e: ConnectionException) {
            Log.e(TAG, "Failed to read settings from printer", e)
            null
        } catch (e: SecurityException) {
            Log.e(TAG, "Bluetooth permission denied reading printer settings", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error reading printer settings", e)
            null
        } finally {
            try {
                connection?.close()
            } catch (_: Exception) {
            }
        }
    }
}
