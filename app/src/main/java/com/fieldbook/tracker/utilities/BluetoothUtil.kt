package com.fieldbook.tracker.utilities

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import android.widget.RadioButton
import android.widget.RadioGroup
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject

interface BluetoothChooseCallback {
    fun onDeviceChosen(deviceName: String)
}
//Bluetooth Utility class for printing ZPL code and choosing bluetooth devices to print from.
class BluetoothUtil {

    private val TAG = BluetoothUtil::class.simpleName

    private val mBluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }

    //operation that uses the provided context to prompt the user for a paired bluetooth device
    fun choose(ctx: Context, callback: BluetoothChooseCallback) {

        var deviceName: String = ""

        mBluetoothAdapter?.let {

            val pairedDevices = it.bondedDevices

            val map = HashMap<Int, BluetoothDevice>()

            val input = RadioGroup(ctx)

            pairedDevices.forEach { device ->
                val deviceName = device.name
                if (!deviceName.isNullOrBlank()) {
                    val button = RadioButton(ctx)
                    button.text = deviceName
                    input.addView(button)
                    map[button.id] = device
                }
            }

            val builder = AlertDialog.Builder(ctx, R.style.AppAlertDialog).apply {

                setTitle(context.getString(R.string.bluetooth_printer_choose_device_title))

                setView(input)

                setNegativeButton(android.R.string.cancel) { _, _ ->

                }

                setPositiveButton(android.R.string.ok) { _, _ ->

                    if (input.checkedRadioButtonId == -1) return@setPositiveButton
                    else {
                        deviceName = map[input.checkedRadioButtonId]?.name ?: ""
                        Log.d(TAG, "Selected Bluetooth Device: $deviceName")
                        callback.onDeviceChosen(deviceName)
                    }
                }
            }

            builder.show()
        }
    }

    /**
     * This function will send a list of label commands to the printer, and only updates the printer message when the
     * button is pressed.
     */
    fun print(
        ctx: Context,
        printerName: String,
        labelCommand: List<String>,
        plotId: String,
        trait: TraitObject
    ) {
        Log.d(TAG, "Label zpl is: $labelCommand")
        if (labelCommand.isNotEmpty()) {
            Log.d(TAG, "Sending label zpl to $printerName")
            PrintThread(ctx, printerName).print(labelCommand, plotId, trait)
        }
    }
}
