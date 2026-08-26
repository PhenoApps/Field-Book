package com.fieldbook.tracker.printing

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.SharedPreferences
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.BluetoothChooseCallback
import com.fieldbook.tracker.utilities.BluetoothUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import org.phenoapps.labelprint.service.PrinterConnector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPrinterConnector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SharedPreferences
) : PrinterConnector {

    private val bluetoothUtil = BluetoothUtil()

    override fun isBluetoothEnabled(): Boolean {
        return BluetoothAdapter.getDefaultAdapter()?.isEnabled ?: false
    }

    override fun getConnectedPrinterName(): String? {
        return prefs.getString(GeneralKeys.LABEL_PRINT_DEVICE_NAME, null)
    }

    @SuppressLint("MissingPermission")
    override fun connectToPrinter(onResult: (Boolean) -> Unit) {
        bluetoothUtil.choose(context, object : BluetoothChooseCallback {
            override fun onDeviceChosen(deviceName: String) {
                prefs.edit().putString(GeneralKeys.LABEL_PRINT_DEVICE_NAME, deviceName).apply()
                onResult(true)
            }
        })
    }

    override fun print(zplLabels: List<String>, onResult: (Boolean, String?) -> Unit) {
        val printerName = getConnectedPrinterName()
        if (printerName == null) {
            onResult(false, "No printer connected")
            return
        }
        
        try {
            bluetoothUtil.print(context, printerName, zplLabels, "", null)
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
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                val device = adapter.bondedDevices.firstOrNull { it.name == printerName }
                if (device != null) {
                    val connection = com.zebra.sdk.comm.BluetoothConnection(device.address)
                    connection.open()
                    connection.write("~JC\n^XA\n^JUS\n^XZ\n".toByteArray())
                    connection.close()
                    onResult(true)
                } else {
                    onResult(false)
                }
            } catch (e: Exception) {
                onResult(false)
            }
        }.start()
    }
}
