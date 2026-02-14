package com.fieldbook.tracker.devices.multispeq

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Manages Bluetooth Classic communication with a MultispeQ device.
 *
 * MultispeQ devices use the Serial Port Profile (SPP) over Bluetooth Classic.
 * Commands are sent as JSON strings followed by CRLF.
 * Responses arrive as JSON followed by an 8-character checksum and a newline:
 *   <json_data><8-char-checksum>\n
 *
 * Device name filter: name must contain "multi" or "photo" (case-insensitive).
 */
class MultispeqManager(private val listener: MultispeqListener) {

    companion object {
        private const val TAG = "MultispeqManager"
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val CHECKSUM_LENGTH = 8
    }

    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null

    /** Returns bonded Bluetooth devices whose names suggest a MultispeQ device. */
    fun getMultispeqDevices(): List<BluetoothDevice> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        return adapter.bondedDevices
            .filter { device ->
                val name = device.name?.lowercase() ?: ""
                name.contains("multi") || name.contains("photo")
            }
    }

    /** Begins connecting to the given device. Result delivered via [MultispeqListener]. */
    fun connect(device: BluetoothDevice) {
        disconnect()
        connectThread = ConnectThread(device).apply { start() }
    }

    /**
     * Sends a command to the connected device.
     * The command is serialized to JSON and sent as "<json>\r\n".
     */
    fun sendCommand(command: Any) {
        val json = when (command) {
            is String -> command
            is JSONObject -> command.toString()
            is JSONArray -> command.toString()
            else -> command.toString()
        }
        connectedThread?.write("$json\r\n")
            ?: listener.onError("Not connected to a device")
    }

    /** Closes the current connection and stops all threads. */
    fun disconnect() {
        connectedThread?.cancel()
        connectedThread = null
        connectThread?.cancel()
        connectThread = null
    }

    // -------------------------------------------------------------------------
    // ConnectThread — establishes the RFCOMM socket
    // -------------------------------------------------------------------------

    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {

        private val socket: BluetoothSocket? = try {
            device.createRfcommSocketToServiceRecord(SPP_UUID)
        } catch (e: Exception) {
            Log.e(TAG, "createRfcommSocketToServiceRecord failed", e)
            null
        }

        override fun run() {
            // Cancel discovery to speed up connection
            try {
                BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
            } catch (_: Exception) {}

            try {
                socket?.connect()
            } catch (e: IOException) {
                Log.e(TAG, "connect() failed", e)
                try { socket?.close() } catch (_: Exception) {}
                listener.onError("Failed to connect: ${e.message}")
                return
            }

            socket?.let { sock ->
                connectedThread = ConnectedThread(sock, device).apply { start() }
                listener.onConnected(device)
            }
        }

        fun cancel() {
            try { socket?.close() } catch (e: IOException) {
                Log.e(TAG, "cancel: socket close failed", e)
            }
        }
    }

    // -------------------------------------------------------------------------
    // ConnectedThread — reads responses from the device
    // -------------------------------------------------------------------------

    private inner class ConnectedThread(
        private val socket: BluetoothSocket,
        private val device: BluetoothDevice
    ) : Thread() {

        private val inputStream: InputStream? = try {
            socket.inputStream
        } catch (e: IOException) {
            Log.e(TAG, "inputStream failed", e)
            null
        }

        private val outputStream: OutputStream? = try {
            socket.outputStream
        } catch (e: IOException) {
            Log.e(TAG, "outputStream failed", e)
            null
        }

        override fun run() {
            val buffer = StringBuilder()

            while (socket.isConnected) {
                try {
                    val byte = inputStream?.read() ?: break
                    if (byte == -1) break

                    val ch = byte.toChar()

                    if (ch == '\n') {
                        // We have a complete response — strip trailing \r if present
                        val raw = buffer.toString().trimEnd('\r')
                        buffer.clear()

                        if (raw.length > CHECKSUM_LENGTH) {
                            val jsonPart = raw.dropLast(CHECKSUM_LENGTH)
                            deliverResult(jsonPart)
                        } else if (raw.isNotBlank()) {
                            // Short response (e.g. device info "1" command returns plain text)
                            deliverResult(raw)
                        }
                    } else {
                        buffer.append(ch)
                    }
                } catch (e: Exception) {
                    if ("socket closed" in (e.message ?: "")) {
                        break
                    }
                    Log.e(TAG, "read error", e)
                    break
                }
            }

            listener.onDisconnected()
        }

        private fun deliverResult(json: String) {
            listener.onMeasurementResult(json)
        }

        fun write(data: String) {
            try {
                outputStream?.write(data.toByteArray())
                outputStream?.flush()
            } catch (e: IOException) {
                Log.e(TAG, "write failed", e)
                listener.onError("Failed to send command: ${e.message}")
            }
        }

        fun cancel() {
            try {
                inputStream?.close()
                socket.close()
            } catch (e: IOException) {
                Log.e(TAG, "cancel: socket close failed", e)
            }
        }
    }
}
