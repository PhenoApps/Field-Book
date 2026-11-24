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
 * After [connect] succeeds, call [requestDeviceInfo] to kick off a two-phase handshake
 * that mirrors the TypeScript app:
 *
 *   Phase 1 – sends "battery"  → response "battery:XX…" → parses percent
 *   Phase 2 – sends "1"        → response JSON          → parses device_id / firmware
 *
 * Both results are combined into a single [MultispeqListener.onDeviceInfo] callback.
 * All other responses go to [MultispeqListener.onMeasurementResult].
 */
class MultispeqManager(private val listener: MultispeqListener) {

    companion object {
        private const val TAG = "MultispeqManager"
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val CHECKSUM_LENGTH = 8
    }

    private enum class PendingInfo { NONE, BATTERY, DEVICE_INFO, SLEEP }

    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null

    /** Tracks which device-info response we are waiting for. Read/written on the I/O thread
     *  and set on the main thread, so marked @Volatile. */
    @Volatile private var pendingInfo = PendingInfo.NONE
    /** Battery level captured during Phase 1, held until Phase 2 completes. */
    @Volatile private var pendingBattery = -1

    /** Returns bonded Bluetooth devices whose names suggest a MultispeQ device. */
    fun getMultispeqDevices(): List<BluetoothDevice> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        return adapter.bondedDevices.filter { device ->
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
     * Kicks off the two-phase device-info handshake (battery then "1").
     * Must be called from the main thread after [MultispeqListener.onConnected].
     */
    fun requestDeviceInfo() {
        pendingBattery = -1
        pendingInfo = PendingInfo.BATTERY
        sendCommand("battery")
    }

    /**
     * Sends a command to the connected device as "<json>\r\n".
     */
    fun sendCommand(command: Any) {
        val json = when (command) {
            is String  -> command
            is JSONObject -> command.toString()
            is JSONArray  -> command.toString()
            else       -> command.toString()
        }
        connectedThread?.write("$json\r\n")
            ?: listener.onError("Not connected to a device")
    }

    /**
     * Sends the "sleep" command to power off the device.
     * Any final response from the device before it disconnects is suppressed
     * so it does not overwrite the last saved observation.
     */
    fun sendSleep() {
        pendingInfo = PendingInfo.SLEEP
        sendCommand("sleep")
    }

    /** Closes the current connection and stops all threads. */
    fun disconnect() {
        pendingInfo = PendingInfo.NONE
        connectedThread?.cancel()
        connectedThread = null
        connectThread?.cancel()
        connectThread = null
    }

    // -------------------------------------------------------------------------
    // ConnectThread
    // -------------------------------------------------------------------------

    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {

        // Socket is created lazily in run() so we can swap to a fallback if needed
        @Volatile private var activeSocket: BluetoothSocket? = null

        override fun run() {
            try { BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery() } catch (_: Exception) {}

            val sock = tryConnect() ?: run {
                listener.onError("Failed to connect to device")
                listener.onDisconnected()
                return
            }

            activeSocket = sock
            connectedThread = ConnectedThread(sock, device).apply { start() }
            listener.onConnected(device)
        }

        /**
         * Attempts connection via the standard SDP method first.
         * Falls back to a direct RFCOMM channel-1 socket via reflection — this recovers
         * the common Android "read failed, socket might closed or timeout, read ret: -1"
         * error that occurs after an unexpected disconnect (e.g. screen-off).
         */
        private fun tryConnect(): BluetoothSocket? {
            // Primary: SDP-based socket
            val primary = try {
                device.createRfcommSocketToServiceRecord(SPP_UUID)
            } catch (e: Exception) {
                Log.e(TAG, "createRfcommSocketToServiceRecord failed", e); null
            }
            if (primary != null) {
                try {
                    primary.connect()
                    return primary
                } catch (e: IOException) {
                    Log.w(TAG, "primary connect failed, trying fallback: ${e.message}")
                    try { primary.close() } catch (_: Exception) {}
                }
            }

            // Fallback: direct channel-1 socket via reflection
            return try {
                @Suppress("DiscouragedPrivateApi")
                val fallback = device.javaClass
                    .getMethod("createRfcommSocket", Int::class.java)
                    .invoke(device, 1) as BluetoothSocket
                fallback.connect()
                fallback
            } catch (e: Exception) {
                Log.e(TAG, "fallback connect failed", e)
                null
            }
        }

        fun cancel() {
            try { activeSocket?.close() } catch (e: IOException) {
                Log.e(TAG, "cancel: socket close failed", e)
            }
        }
    }

    // -------------------------------------------------------------------------
    // ConnectedThread
    // -------------------------------------------------------------------------

    private inner class ConnectedThread(
        private val socket: BluetoothSocket,
        private val device: BluetoothDevice
    ) : Thread() {

        private val inputStream: InputStream? = try {
            socket.inputStream
        } catch (e: IOException) { Log.e(TAG, "inputStream failed", e); null }

        private val outputStream: OutputStream? = try {
            socket.outputStream
        } catch (e: IOException) { Log.e(TAG, "outputStream failed", e); null }

        override fun run() {
            val buffer = StringBuilder()

            while (socket.isConnected) {
                try {
                    val byte = inputStream?.read() ?: break
                    if (byte == -1) break

                    val ch = byte.toChar()
                    if (ch == '\n') {
                        val raw = buffer.toString().trimEnd('\r')
                        buffer.clear()
                        if (raw.isNotBlank()) deliverResult(raw)
                    } else {
                        buffer.append(ch)
                    }
                } catch (e: Exception) {
                    if ("socket closed" in (e.message ?: "")) break
                    Log.e(TAG, "read error", e)
                    break
                }
            }

            listener.onDisconnected()
        }

        private fun deliverResult(raw: String) {
            when (pendingInfo) {

                PendingInfo.BATTERY -> {
                    // Response: "battery:75XXXXXXXX" — strip checksum, then remove prefix
                    val stripped = if (raw.length > CHECKSUM_LENGTH) raw.dropLast(CHECKSUM_LENGTH) else raw
                    pendingBattery = stripped.removePrefix("battery:").trim().toIntOrNull() ?: -1
                    // Phase 2: request device id + firmware
                    pendingInfo = PendingInfo.DEVICE_INFO
                    write("1\r\n")
                }

                PendingInfo.DEVICE_INFO -> {
                    pendingInfo = PendingInfo.NONE
                    var deviceId = device.address
                    var firmware = ""

                    // Try with checksum stripped, then full string (firmware-version compat)
                    val candidates = buildList {
                        if (raw.length > CHECKSUM_LENGTH) add(raw.dropLast(CHECKSUM_LENGTH))
                        add(raw)
                    }
                    for (candidate in candidates) {
                        try {
                            val obj = JSONObject(candidate)
                            deviceId = obj.optString("device_id", device.address).ifBlank { device.address }
                            firmware = obj.optString("device_firmware", "")
                            break
                        } catch (_: Exception) { /* try next */ }
                    }

                    listener.onDeviceInfo(deviceId, firmware, pendingBattery)
                }

                PendingInfo.SLEEP -> {
                    // Device is powering off; ignore any final acknowledgement response
                    pendingInfo = PendingInfo.NONE
                }

                PendingInfo.NONE -> {
                    // Normal measurement response: strip 8-char checksum
                    val json = if (raw.length > CHECKSUM_LENGTH) raw.dropLast(CHECKSUM_LENGTH) else raw
                    listener.onMeasurementResult(json)
                }
            }
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
