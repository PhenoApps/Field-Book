package com.fieldbook.tracker.printing

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.BluetoothChooseCallback
import com.fieldbook.tracker.utilities.BluetoothUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import org.phenoapps.labelprint.service.PrinterConnector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPrinterConnector @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prefs: SharedPreferences
) : PrinterConnector {

    companion object {
        private const val TAG = "AppPrinterConnector"
    }

    private val bluetoothUtil = BluetoothUtil()

    override fun isBluetoothEnabled(): Boolean {
        return BluetoothAdapter.getDefaultAdapter()?.isEnabled ?: false
    }

    override fun getConnectedPrinterName(): String? {
        return prefs.getString(GeneralKeys.LABEL_PRINT_DEVICE_NAME, null)
    }

    @SuppressLint("MissingPermission")
    override fun connectToPrinter(context: Context, onResult: (Boolean) -> Unit) {
        bluetoothUtil.choose(context, object : BluetoothChooseCallback {
            override fun onDeviceChosen(deviceName: String) {
                prefs.edit { putString(GeneralKeys.LABEL_PRINT_DEVICE_NAME, deviceName) }
                onResult(true)
            }
        })
    }

    override fun print(zplLabels: List<String>, onResult: (Boolean, String?) -> Unit) {
        print(zplLabels, "", null, onResult)
    }

    fun print(
        zplLabels: List<String>,
        plotId: String,
        trait: TraitObject?,
        onResult: (Boolean, String?) -> Unit
    ) {
        val printerName = getConnectedPrinterName()
        if (printerName == null) {
            onResult(false, context.getString(R.string.printer_not_connected))
            return
        }

        try {
            bluetoothUtil.print(context, printerName, zplLabels, plotId, trait)
            onResult(true, null)
        } catch (e: Exception) {
            onResult(false, e.message)
        }
    }

    @SuppressLint("MissingPermission")
    override fun calibrate(onResult: (Boolean) -> Unit) {
        val printerName = getConnectedPrinterName()
        if (printerName == null) {
            onResult(false)
            return
        }

        Thread {
            var connection: com.zebra.sdk.comm.BluetoothConnection? = null
            try {
                val device = BluetoothAdapter.getDefaultAdapter()
                    ?.bondedDevices
                    ?.firstOrNull { it.name == printerName }
                if (device != null) {
                    connection = com.zebra.sdk.comm.BluetoothConnection(device.address).apply { open() }
                    connection.write("~JC\n^XA\n^JUS\n^XZ\n".toByteArray())
                    onResult(true)
                } else {
                    onResult(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Calibration failed", e)
                onResult(false)
            } finally {
                // close even when opening or writing fails, so the printer isn't left connected
                try {
                    connection?.close()
                } catch (_: Exception) {
                }
            }
        }.start()
    }
}
