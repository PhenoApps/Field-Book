package com.fieldbook.tracker.utilities

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import com.fieldbook.tracker.R


//Bluetooth Utility class for printing ZPL code and choosing bluetooth devices to print from.
class BluetoothUtil {

    private var mBtName: String = String()

    private val mBluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }

    //operation that uses the provided context to prompt the user for a paired bluetooth device
    private fun choose(ctx: Context, f: () -> Unit) {

        if (mBtName.isBlank()) {

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
                            mBtName = map[input.checkedRadioButtonId]?.name ?: ""
                        }
                        f()
                    }
                }

                builder.show()
            }

        } else f()
    }

    /**
     * This function will first ask the user to select a printer.
     * As long as the same object is used the user only needs to ask once.
     * This sends a list of label commands, and only updates the printer message when the
     * button is pressed.
     */
    fun print(ctx: Context, size: String, labelCommand: List<String>) {

        choose(ctx) {

            if (labelCommand.isNotEmpty()) {

                PrintThread(ctx, mBtName).print(size, labelCommand)

            }
        }
    }
}