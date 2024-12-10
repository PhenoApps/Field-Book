package com.fieldbook.tracker.utilities

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject
import com.zebra.sdk.comm.BluetoothConnection
import com.zebra.sdk.comm.ConnectionException
import com.zebra.sdk.printer.SGD
import com.zebra.sdk.printer.ZebraPrinterFactory
import com.zebra.sdk.printer.ZebraPrinterLanguageUnknownException


/**
 * A thread implementation for communicating with Zebra printers using the ZSDK_ANDROID_API.jar
 */
class PrintThread(private val ctx: Context, private val btName: String) : Thread() {

    //the list of labels to print, this is always the same label but multiple copies
    private var mLabelCommands = listOf<String>()

    //the broadcaster to send messages back to the trait layout
    private var mLocalBroadcast: LocalBroadcastManager? = null

    //the trait and plot to attach the print count to in the database
    private var plotId: String = ""
    private lateinit var trait: TraitObject

    //the command the bluetooth util class uses
    fun print(labels: List<String>, plotId: String, trait: TraitObject) {
        mLabelCommands = labels
        this.plotId = plotId
        this.trait = trait
        start()
    }

    /**
     * When the thread begins a connection is set using the Zebra api.
     * When a connection is establish a print is attempted.
     * Many errors could happen here such as no paper or the head is open.
     * An error message or a success message is sent back via the broadcast manager.
     */
    override fun run() {

        Looper.prepare()

        mLocalBroadcast = LocalBroadcastManager.getInstance(ctx)

        if (btName.isBlank()) {

            val notPaired = ctx.getString(R.string.no_device_paired)

            Toast.makeText(ctx, notPaired, Toast.LENGTH_SHORT).show()

        } else {

            val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

            val pairedDevices = mBluetoothAdapter.bondedDevices.filter {
                it.name == btName
            }

            if (pairedDevices.isNotEmpty()) {

                val bc = BluetoothConnection(pairedDevices[0].address)

                try {

                    bc.open()

                    val printer = ZebraPrinterFactory.getInstance(bc)

                    val linkOsPrinter = ZebraPrinterFactory.createLinkOsPrinter(printer)

                    linkOsPrinter?.let { it ->

                        val printerStatus = it.currentStatus

                        getPrinterStatus(bc)

                        val printerOpen = ctx.getString(R.string.printer_open)
                        val printerPaused = ctx.getString(R.string.printer_paused)
                        val noPaper = ctx.getString(R.string.printer_empty)
                        val notConnected = ctx.getString(R.string.printer_not_connected)
                        val success = ctx.getString(R.string.printer_success)

                        val intent = Intent("printer_message")

                        if (printerStatus.isReadyToPrint) {

                            mLabelCommands.forEach { label ->

                                printer.sendCommand(label)

                            }

                            intent.putExtra("message", success)
                            intent.putExtra("numLabels", mLabelCommands.size)
                            intent.putExtra("plotId", plotId)
                            intent.putExtra("traitId", trait.id)
                            intent.putExtra("traitFormat", trait.format)

                        } else if (printerStatus.isHeadOpen) {

                            intent.putExtra("message", printerOpen)

                        } else if (printerStatus.isPaused) {

                            intent.putExtra("message", printerPaused)

                        } else if (printerStatus.isPaperOut) {

                            intent.putExtra("message", noPaper)

                        } else {

                            intent.putExtra("message", notConnected)

                        }

                        mLocalBroadcast?.sendBroadcast(intent)

                    }
                } catch (e: ConnectionException) {

                    e.printStackTrace()

                } catch (e: ZebraPrinterLanguageUnknownException) {

                    e.printStackTrace()

                } finally {

                    bc.close()
                }
            }
        }
    }

    @Throws(ConnectionException::class)
    private fun getPrinterStatus(connection: BluetoothConnection) {

        SGD.SET("device.languages", "zpl", connection)

    }
}