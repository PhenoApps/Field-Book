package com.fieldbook.tracker.utilities

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.util.Log
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject
import com.google.android.material.dialog.MaterialAlertDialogBuilder

interface BluetoothChooseCallback {
    fun onDeviceChosen(deviceName: String)
}

/**
 * Bluetooth Utility class for printing ZPL code and choosing bluetooth devices to print from.
 */
class BluetoothUtil {

    private val TAG = BluetoothUtil::class.simpleName

    private val mBluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }

    /**
     * Shows a Material dialog listing paired Bluetooth devices for the user to choose from.
     * Devices are displayed in a single-choice list with their names.
     */
    fun choose(ctx: Context, callback: BluetoothChooseCallback) {

        mBluetoothAdapter?.let { adapter ->

            val pairedDevices = adapter.bondedDevices
                .filter { !it.name.isNullOrBlank() }
                .toList()

            if (pairedDevices.isEmpty()) {
                MaterialAlertDialogBuilder(ctx, R.style.AppAlertDialog)
                    .setTitle(R.string.bluetooth_printer_choose_device_title)
                    .setMessage(R.string.no_device_paired)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return
            }

            val deviceNames = pairedDevices.map { it.name }.toTypedArray()
            var selectedIndex = -1

            MaterialAlertDialogBuilder(ctx, R.style.AppAlertDialog)
                .setTitle(R.string.bluetooth_printer_choose_device_title)
                .setSingleChoiceItems(deviceNames, -1) { _, which ->
                    selectedIndex = which
                }
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    if (selectedIndex >= 0) {
                        val deviceName = pairedDevices[selectedIndex].name
                        Log.d(TAG, "Selected Bluetooth Device: $deviceName")
                        callback.onDeviceChosen(deviceName)
                    }
                }
                .show()
        }
    }

    /**
     * Sends a list of label ZPL commands to the named printer via PrintThread.
     */
    fun print(
        ctx: Context,
        printerName: String,
        labelCommand: List<String>,
        plotId: String,
        trait: TraitObject?
    ) {
        Log.d(TAG, "Label zpl is: $labelCommand")
        if (labelCommand.isNotEmpty()) {
            Log.d(TAG, "Sending label zpl to $printerName")
            PrintThread(ctx, printerName).print(labelCommand, plotId, trait)
        }
    }
}
