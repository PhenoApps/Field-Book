package com.fieldbook.tracker.utilities.connectivity

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ActivityContext
import java.util.UUID
import javax.inject.Inject

/***
 * GreenSeeker Gatt protocol:
 * Adding GreenSeeker device: GreenSeekerH BLE
 *
 * specific client connection:
 *
    connect() - device: XX:XX:XX:XX:7C:A2, auto: false
    registerApp()
    registerApp() - UUID=4fe6af05-ae5e-43b0-b7d0-f8e188ea7d8d
    onClientRegistered() - status=0 clientIf=166
    onClientConnectionState() - status=0 clientIf=166 connected=true device=XX:XX:XX:XX:7C:A2
    discoverServices() - device: XX:XX:XX:XX:7C:A2
    onClientConnectionState() - status=0 clientIf=165 connected=true device=XX:XX:XX:XX:7C:A2
    discoverServices() - device: XX:XX:XX:XX:7C:A2
    onConnectionUpdated() - Device=XX:XX:XX:XX:7C:A2 interval=6 latency=0 timeout=500 status=0
    onConnectionUpdated() - Device=XX:XX:XX:XX:7C:A2 interval=6 latency=0 timeout=500 status=0
    onSearchComplete() = address=XX:XX:XX:XX:7C:A2 status=0

*   Service search:

    Generic Access Service:
    Service: 00001800-0000-1000-8000-00805f9b34fb
        Characteristic: 00002a28-0000-1000-8000-00805f9b34fb
        Characteristic: 00002a00-0000-1000-8000-00805f9b34fb
        Characteristic: 00002a01-0000-1000-8000-00805f9b34fb
        Characteristic: 00002a04-0000-1000-8000-00805f9b34fb
        Characteristic: 00002a29-0000-1000-8000-00805f9b34fb

    Generic Attribute Service:
    Service: 00001801-0000-1000-8000-00805f9b34fb

    Device Information Service:
    Service: 0000180a-0000-1000-8000-00805f9b34fb
        Characteristic: 00002a23-0000-1000-8000-00805f9b34fb
        Characteristic: 00002a24-0000-1000-8000-00805f9b34fb
        Characteristic: 00002a25-0000-1000-8000-00805f9b34fb
        Characteristic: 00002a26-0000-1000-8000-00805f9b34fb
        Characteristic: 00002a27-0000-1000-8000-00805f9b34fb
    Service: 0000180a-0000-1000-8000-00805f9b34fb
        Characteristic: 00002a2a-0000-1000-8000-00805f9b34fb
        Characteristic: 00002a23-0000-1000-8000-00805f9b34fb
        Characteristic: 00002a24-0000-1000-8000-00805f9b34fb
        Characteristic: 00002a50-0000-1000-8000-00805f9b34fb
        Characteristic: 00002a25-0000-1000-8000-00805f9b34fb
        Characteristic: 00002a26-0000-1000-8000-00805f9b34fb

    Device Battery Service:
    Service: 0000180f-0000-1000-8000-00805f9b34fb
        Characteristic: 00002a27-0000-1000-8000-00805f9b34fb
        Characteristic: 00002a28-0000-1000-8000-00805f9b34fb
        Characteristic: 00002a19-0000-1000-8000-00805f9b34fb
        Characteristic: 00002a29-0000-1000-8000-00805f9b34fb
        Characteristic: 00002a2a-0000-1000-8000-00805f9b34fb
            Descriptor: 00002902-0000-1000-8000-00805f9b34fb
        Characteristic: 00002a50-0000-1000-8000-00805f9b34fb
            Descriptor: 00002908-0000-1000-8000-00805f9b34fb
    Service: 0000180f-0000-1000-8000-00805f9b34fb
        Descriptor: 00002904-0000-1000-8000-00805f9b34fb
        Characteristic: 00002a19-0000-1000-8000-00805f9b34fb

    Custom Texas Instrument Movement Sensor Service:
    Service: f000aa90-0451-4000-b000-000000000000
        Descriptor: 00002902-0000-1000-8000-00805f9b34fb
        Characteristic: f000aa91-0451-4000-b000-000000000000
            Descriptor: 00002908-0000-1000-8000-00805f9b34fb
            Descriptor: 00002902-0000-1000-8000-00805f9b34fb
            Descriptor: 00002904-0000-1000-8000-00805f9b34fb
            Descriptor: 00002901-0000-1000-8000-00805f9b34fb
    Service: f000aa90-0451-4000-b000-000000000000
        Characteristic: f000aa92-0451-4000-b000-000000000000 --listens to on char write here
        Characteristic: f000aa91-0451-4000-b000-000000000000 --sets notification here
            Descriptor: 00002901-0000-1000-8000-00805f9b34fb
            Descriptor: 00002902-0000-1000-8000-00805f9b34fb --enables notification with write descriptor: byteArray(0x0, 0x0)
            Descriptor: 00002901-0000-1000-8000-00805f9b34fb
        Characteristic: f000aa92-0451-4000-b000-000000000000
            Descriptor: 00002901-0000-1000-8000-00805f9b34fb
 */
class GreenSeekerGattManager @Inject constructor(
    @ActivityContext private val context: Context,
) {

    interface GreenSeekerListener {
        fun onTriggerDown()
        fun onTriggerUp()
        fun onDeviceConnected()
        fun onDeviceDisconnected()
        fun onDataReceived(ndviValue: Int)
        fun onAverageReceived(ndviValue: Int)
        fun onError(message: String)
    }

    var device: BluetoothDevice? = null

    var isConnected: Boolean
        get() = bluetoothGatt != null
        set(_) {
            // This property is read-only, no setter needed
        }

    private var bluetoothGatt: BluetoothGatt? = null
    private var listener: GreenSeekerListener? = null

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {

            Log.d("GATT", "onConnectionStateChange: status=$status, newState=$newState, device=${gatt.device.address}")

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("GATT", "Connection failed with status: $status")
                return
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("GATT", "Disconnected from device: ${gatt.device.address}")
                gatt.close()
                bluetoothGatt = null
                listener?.onDeviceDisconnected()
                return
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("GATT", "Connected to device: ${gatt.device.address}")
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

            gatt.getService(UUID.fromString("f000aa90-0451-4000-b000-000000000000"))?.let { service ->
                Log.d("GATT", "Service: ${service.uuid}")

                service.getCharacteristic(UUID.fromString("f000aa92-0451-4000-b000-000000000000"))?.let { characteristic ->
                    Log.d("GATT", "Characteristic for writing: ${characteristic.uuid}")
                     characteristic.value = byteArrayOf(0x01) // Example value
                     gatt.writeCharacteristic(characteristic)
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = characteristic.value
            //Log.d("GATT", "Characteristic changed: ${characteristic.uuid}, Value: ${value?.map { it.toInt() }?.joinToString(", ")}")
            val trigger = (value?.getOrNull(0) ?: 0).toInt().toChar().toString()
            val ndviValue = (value?.getOrNull(1) ?: 0).toInt()
            when (trigger) {
                "d" -> {
                    //Log.d("GreenSeeker", "Device Trigger Down")
                    listener?.onTriggerDown()
                }
                "s" -> {
                    // Handle data read Normalized Difference Vegetation Index (NDVI) fom 0.00 to 0.99
                    //https://ascommunications.co.uk/wp-content/uploads/2021/11/Greenseeker-Handheld-Quick-Reference-Guide-VEW.pdf
                    //Log.d("GATT", "NDVI Value: $ndviValue")
                    listener?.onDataReceived(ndviValue)
                }
                "u" -> {
                    //Log.d("GreenSeeker", "Device Trigger Up")
                    listener?.onTriggerUp()
                }
                "a" -> {
                    // Log.d("GreenSeeker", "Device Trigger Average")
                    //Log.d("GATT", "NDVI Avg Value: $ndviValue")
                    listener?.onAverageReceived(ndviValue)
                }
                "e" -> {
                    //error to close or far
                    //Log.e("GATT", "Error: Device too close or too far")
                    val distance = (value?.getOrNull(1) ?: 0).toInt()
                    listener?.onError("Distance error: $distance")
                    /**
                     * these distance readings seem to be a bit random but also based on scan distance
                     * E_F error "too far from subject" is shows low values
                     * E_C error "too close to subject" is shows high values
                     * But not reliable, it seems readings stop at a certain number of "e" readings
                     * and at the end it spits out two low-value readings
                     */
                }
            }
            Log.d("GreenSeeker", "Trigger: $trigger $ndviValue ${value.joinToString(", ")}")
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("GATT", "Characteristic written successfully: ${characteristic?.uuid}")
                if (characteristic?.uuid?.toString() == "f000aa92-0451-4000-b000-000000000000") {
                    gatt?.getService(UUID.fromString("f000aa90-0451-4000-b000-000000000000"))?.let { service ->
                        service.getCharacteristic(UUID.fromString("f000aa91-0451-4000-b000-000000000000"))
                            ?.let { characteristic ->
                                gatt.setCharacteristicNotification(characteristic, true)
                                characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                                    ?.let { descriptor ->
                                        descriptor.value =
                                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                        gatt.writeDescriptor(descriptor)
                                        Log.d(
                                            "GATT",
                                            "Enabled notifications for characteristic: ${characteristic.uuid}"
                                        )
                                    }
                            }
                    }
                }
            } else {
                Log.e("GATT", "Failed to write characteristic: ${characteristic?.uuid}, status: $status")
            }
        }
    }

    fun connect(device: BluetoothDevice, listener: GreenSeekerListener) {
        this.device = device
        this.listener = listener
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        listener = null
        device = null
    }
}