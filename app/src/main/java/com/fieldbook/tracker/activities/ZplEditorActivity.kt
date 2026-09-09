package com.fieldbook.tracker.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fieldbook.tracker.R
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.zpl.TemplateRepository
import com.zebra.sdk.comm.BluetoothConnection
import com.zebra.sdk.comm.ConnectionException
import com.zebra.sdk.printer.SGD
import dagger.hilt.android.AndroidEntryPoint
import org.phenoapps.labelprint.ui.LabelSettings
import org.phenoapps.labelprint.ui.ZplEditorScreen
import java.io.IOException
import javax.inject.Inject

@AndroidEntryPoint
class ZplEditorActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ZplEditorActivity"
        const val EXTRA_INITIAL_ZPL = "initial_zpl"
        const val EXTRA_TEMPLATE_NAME = "template_name"
        const val RESULT_ZPL = "result_zpl"
        const val RESULT_TEMPLATE_NAME = "result_template_name"
    }

    @Inject
    lateinit var templateRepository: TemplateRepository

    @Inject
    lateinit var prefs: SharedPreferences

    private var pendingExportZpl: String = ""

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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        templateRepository.ensureBuiltInTemplatesExist()

        val initialZpl = intent.getStringExtra(EXTRA_INITIAL_ZPL) ?: ""
        val templateName = intent.getStringExtra(EXTRA_TEMPLATE_NAME)

        setContent {
            AppTheme {
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
                        val selectedId = templateRepository.getSelectedTemplateId()
                        val selectedName =
                            if (selectedId != null) templateRepository.getTemplateName(selectedId) else null
                        val selectedZpl =
                            if (selectedId != null) templateRepository.getTemplate(selectedId) else null

                        if (selectedName != null && selectedZpl != null) {
                            val resultIntent = Intent().apply {
                                putExtra(RESULT_ZPL, selectedZpl)
                                putExtra(RESULT_TEMPLATE_NAME, selectedName)
                            }
                            setResult(RESULT_OK, resultIntent)
                        } else {
                            setResult(RESULT_CANCELED)
                        }
                        finish()
                    },
                    onSave = { name, normalizedZpl ->
                        val id = templateRepository.saveTemplate(name, normalizedZpl)
                        templateRepository.setSelectedTemplateId(id)
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

        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
        val device = adapter.bondedDevices.firstOrNull { it.name == printerName } ?: return null
        val connection = BluetoothConnection(device.address)

        return try {
            connection.open()

            val widthStr = SGD.GET("ezpl.print_width", connection)
            val heightStr = SGD.GET("ezpl.label_length", connection)
            val mediaTypeStr = SGD.GET("ezpl.media_type", connection)
            val trackingStr = SGD.GET("media.type", connection)

            val width = widthStr?.trim()?.toIntOrNull() ?: 609
            val height = heightStr?.trim()?.toIntOrNull() ?: 406

            val mediaType = when {
                mediaTypeStr?.contains("trans", ignoreCase = true) == true -> "Thermal Transfer"
                else -> "Direct Thermal"
            }

            val mediaGap = when {
                trackingStr?.contains("continuous", ignoreCase = true) == true -> "Continuous"
                trackingStr?.contains("mark", ignoreCase = true) == true -> "Mark"
                else -> "Gap/Notch"
            }

            LabelSettings(
                labelWidth = width,
                labelLength = height,
                mediaType = mediaType,
                mediaGap = mediaGap
            )
        } catch (e: ConnectionException) {
            Log.e(TAG, "Failed to read settings from printer", e)
            null
        } finally {
            try {
                connection.close()
            } catch (_: Exception) {
            }
        }
    }
}
