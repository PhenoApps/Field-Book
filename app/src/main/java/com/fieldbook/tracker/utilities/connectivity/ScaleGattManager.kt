package com.fieldbook.tracker.utilities.connectivity

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ActivityContext
import org.phenoapps.utils.GattUtil.Companion.isIndicatable
import org.phenoapps.utils.GattUtil.Companion.isNotifiable
import javax.inject.Inject

/***
 * Crane Scale OCS-L weight custom CCCD = 0000ffb2-0000-1000-8000-00805f9b34fb
 * A&D BT (SJ-6000WP-BT) weight custom CCCD = 5699d646-0c53-11e7-93ae-92361f002671
 */
class ScaleGattManager @Inject constructor(
    @ActivityContext private val context: Context,
) {

    interface ScaleListener {
        fun onDeviceConnected()
        fun onDeviceDisconnected()
        fun onDataReceived(weight: String, unit: String, isStable: Boolean = true)
        fun logUnknownScaleBytes(serviceUuid: String, charUuid: String, value: String)
        fun onError(message: String)
    }

    companion object {
        const val TAG = "ScaleGattManager"
        const val craneScaleOCSLWeightUUID = "0000ffb2-0000-1000-8000-00805f9b34fb"
        const val andWeightUUID = "5699d646-0c53-11e7-93ae-92361f002671"
    }

    enum class DeviceState {
        CONNECTED, DISCONNECTED
    }

    private var deviceState: DeviceState = DeviceState.DISCONNECTED

    var isConnected
        get() = bluetoothGatt != null && deviceState == DeviceState.CONNECTED
        private set(_) {
            // This property is read-only, so we don't need to set it.
        }

    var device: BluetoothDevice? = null

    private var bluetoothGatt: BluetoothGatt? = null
    private var listener: ScaleListener? = null

    private val byteBuffer = arrayListOf<Byte>()

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {

            //Log.d("GATT", "onConnectionStateChange: status=$status, newState=$newState, device=${gatt.device.address}")

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("GATT", "Connection failed with status: $status")
                disconnect()
                return
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("GATT", "Disconnected from device: ${gatt.device.address}")
                disconnect()
                return
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("GATT", "Connected to device: ${gatt.device.address}")
                deviceState = DeviceState.CONNECTED
                listener?.onDeviceConnected()
                gatt.discoverServices()
            }

            if (newState == BluetoothProfile.STATE_CONNECTING) {
                Log.d("GATT", "Connecting to device: ${gatt.device.address}")
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTING) {
                Log.d("GATT", "Disconnecting from device: ${gatt.device.address}")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {

            Log.d("GATT", "onServicesDiscovered: status=$status")
            //log all services
            gatt.services.forEach { service ->
                Log.d("GATT", "Service: ${service.uuid}")
                service.characteristics.forEach { characteristic ->
                    Log.d("GATT", "Characteristic: ${characteristic.uuid}, Properties: ${characteristic.properties}")

                    characteristic.descriptors.forEach { cccd ->
                        try {
                            Log.d("GATT", "Descriptor: ${cccd.uuid}")
                            if (cccd == null) {
                                Log.e("GATT", "CCCD descriptor not found for characteristic: ${characteristic.uuid}")
                            } else {
                                if (characteristic.isIndicatable() || characteristic.isNotifiable()) {
                                    gatt.setCharacteristicNotification(characteristic, true)
                                }
                                cccd.value = if (characteristic.isIndicatable()) {
                                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                                } else {
                                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    gatt.writeDescriptor(cccd,
                                        if (characteristic.isIndicatable()) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                                        else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                                } else {
                                    gatt.writeDescriptor(cccd)
                                }

                                Log.d("GATT", "Enabled notifications for characteristic: ${characteristic.uuid}, Descriptor: ${cccd.uuid}")
                            }
                        } catch (e: Exception) {
                            Log.e("GATT", "Error accessing descriptor: ${cccd.uuid}, Error: ${e.message}")
                        }
                    }
                }
            }
        }

        private fun handleSj6000WeightCharacteristic(characteristic: BluetoothGattCharacteristic) {
            val value = characteristic.value

//          Log.d("GATT", "$value")
            byteBuffer.addAll(value.toList())
            //if byteBuffer ends with CRLF (0x0D, 0x0A), then log the buffer and clear it
            if (byteBuffer.size >= 2 && byteBuffer[byteBuffer.size - 2] == 0x0D.toByte()
                && byteBuffer[byteBuffer.size - 1] == 0x0A.toByte()) {
//              val byteHexes = byteBuffer.joinToString(", ") { String.format("0x%02X", it) }
//              Log.d("GATT", "Received complete message: ${byteHexes}")
                //convert bytes to char and log
                val charValues = byteBuffer.map { it.toChar() }
//              Log.d("GATT", "Received complete message as chars: ${charValues.joinToString("")}")
                val weightString = charValues.joinToString("").dropLast(2) // Drop the last CRLF characters
                val pattern = Regex(
                    """
                    ^(US|ST|OG),([+-][0-9.]{8})([\sA-Za-z]{3})$
                    """.trimIndent()
                )
                pattern.matchEntire(weightString)?.destructured?.let { (prefix, value, unit) ->
                    listener?.onDataReceived(value, unit, prefix == "ST")
                }

                byteBuffer.clear()
            }
        }

        private fun handleCraneScaleWeightCharacteristic(characteristic: BluetoothGattCharacteristic) {

            val stringValue = characteristic.value?.joinToString("") { it.toChar().toString() }

            stringValue?.let { weightString ->

                val pattern = Regex(
                    """
                    ^=([*A-Z]*)\s*([+-]?[0-9.]*)([A-Za-z]*)$
                    """.trimIndent()
                )
                pattern.matchEntire(weightString)?.destructured?.let { (prefix, value, unit) ->

                    //Log.d("StableWeight", "Header: $prefix, Value: $value, Unit: $unit")

                    listener?.onDataReceived(value, unit, isStable = "S" in prefix)
                }
            }
        }

        private fun handleUnknownScale(characteristic: BluetoothGattCharacteristic) {

            //Log.d("GATT", "Characteristic changed: ${characteristic.uuid}, Value: ${characteristic.value?.joinToString()}")

            val value = characteristic.value
            //log each byte
            //Log.d("GATT", "Characteristic changed: ${characteristic.uuid}, Value: ${value?.joinToString()}")
            //log as hex values
            //Log.d("GATT", "Characteristic changed: ${characteristic.uuid}, Value (Hex): ${value?.joinToString(", ") { String.format("0x%02X", it) }}")
            //log as string
            val stringValue = value?.joinToString("") { it.toChar().toString() }
            //log as integer
            //Log.d("Scale", "Characteristic changed: ${characteristic.uuid}, Value Length: ${value?.size}, Value (String): $stringValue")

            listener?.logUnknownScaleBytes(
                characteristic.service.uuid.toString(),
                characteristic.uuid.toString(),
                (value?.joinToString("") { it.toChar().toString() }
                        + " "
                        +value?.joinToString(", ") { String.format("0x%02X", it) })

            )

            stringValue?.let { weightString ->
                // Notify the listener with the weight string
                val regex = Regex("""([-+]?\d+(?:\.\d+)?(?:[eE][-+]?\d+)?)[ ]?([a-zA-Zµμ²³°%]+)""")
                regex.findAll(weightString)
                    .map { matchResult ->
                        val number = matchResult.groupValues[1]
                        val unit = matchResult.groupValues[2]
                        number to unit
                    }
                    .toList().firstOrNull()?.let { (num, unit) ->
                        listener?.onDataReceived(num, unit)

                    }
            }

            //decode byte array to float and log
//            if (value != null && value.size >= 4) {
//                val floatValue = java.nio.ByteBuffer.wrap(value).order(java.nio.ByteOrder.LITTLE_ENDIAN).float
//                Log.d("GATT", "Characteristic changed: ${characteristic.uuid}, Value (Float): $floatValue")
//                //listener?.onDataReceived(floatValue)
//            } else {
//                Log.e("GATT", "Characteristic value is null or too short to convert to float")
//            }
            //decode byte array as integer and log
//            if (value != null && value.size >= 2) {
//                val intValue = java.nio.ByteBuffer.wrap(value).order(java.nio.ByteOrder.LITTLE_ENDIAN).short.toInt()
//                Log.d("GATT", "Characteristic changed: ${characteristic.uuid}, Value (Int): $intValue")
//            } else {
//                Log.e("GATT", "Characteristic value is null or too short to convert to int")
//            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {

            if (characteristic.uuid.toString() == andWeightUUID) {

                handleSj6000WeightCharacteristic(characteristic)

            } else if (characteristic.uuid.toString() == craneScaleOCSLWeightUUID) {

                // Handle Crane Scale OCS-L weight characteristic
                handleCraneScaleWeightCharacteristic(characteristic)

            } else {

                handleUnknownScale(characteristic)
            }
        }
    }

    fun connect(device: BluetoothDevice, listener: ScaleListener) {
        this.listener = listener
        this.device = device
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
        Log.d("GATT", "Connecting to device: ${device.address}")
    }

    fun disconnect() {
        deviceState = DeviceState.DISCONNECTED
        listener?.onDeviceDisconnected()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        listener = null
        device = null
    }
}