package com.fieldbook.tracker.utilities

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import com.fieldbook.tracker.R
import com.fieldbook.tracker.preferences.GeneralKeys


//Bluetooth Utility class for printing ZPL code and choosing bluetooth devices to print from.
class BluetoothUtil {

    private var ep: SharedPreferences? = null

    private var mBtName: String = String()

    private val mBluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }

    //operation that uses the provided context to prompt the user for a paired bluetooth device
    private fun choose(ctx: Context) {

        ep = ctx.getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, 0)

        mBluetoothAdapter?.let {

            val pairedDevices = it.bondedDevices

            val map = HashMap<Int, BluetoothDevice>()

            val input = RadioGroup(ctx)

            pairedDevices.forEachIndexed { _, t ->
                val button = RadioButton(ctx)
                button.text = t.name
                input.addView(button)
                map[button.id] = t
            }

            val builder = AlertDialog.Builder(ctx).apply {

                setTitle(context.getString(R.string.bluetooth_printer_choose_device_title))

                setView(input)

                setNegativeButton(android.R.string.cancel) { _, _ ->

                }

                setPositiveButton(android.R.string.ok) { _, _ ->

                    if (input.checkedRadioButtonId == -1) return@setPositiveButton
                    else {
                        Log.d("BluetoothUtil", "Setting mBtName")
                        mBtName = map[input.checkedRadioButtonId]?.name ?: ""
                        ep.edit().putString(GeneralKeys.LABEL_PRINT_DEVICE_NAME, mBtName)
                    }
                }
            }

            builder.show()
        }
    }

    /**
     * This function will first ask the user to select a printer.
     * As long as the same object is used the user only needs to ask once.
     * This sends a list of label commands, and only updates the printer message when the
     * button is pressed.
     */
    fun print(ctx: Context, printerName: String? = null, size: String, labelCommand: List<String>) {
        Log.d("BluetoothUtil", "Selected Bluetooth Device: $mBtName")
        if (printerName == null) {
            Log.d("BluetoothUtil", "Printer name is null, asking user to choose.")
            choose(ctx)
        } else {
            mBtName = printerName
        }
        Log.d("BluetoothUtil", "Label Command is: $labelCommand")
        if (labelCommand.isNotEmpty()) {
            Log.d("BluetoothUtil", "printing")
            PrintThread(ctx, mBtName).print(size, labelCommand)
        }
    }
}
