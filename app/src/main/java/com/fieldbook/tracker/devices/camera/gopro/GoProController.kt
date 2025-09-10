package com.fieldbook.tracker.devices.camera.gopro

interface GoProController {

    fun awaitGoProAp(
        ssid: String?,
        pass: String?,
        bssid: String,
        onConnected: (android.net.Network) -> Unit
    ) = Unit

    fun checkWifiEnabled(): Boolean

    fun connectToGoPro(
        device: android.bluetooth.BluetoothDevice,
        callback: android.bluetooth.BluetoothGattCallback
    )

    fun connectToGoProWifi(
        dialog: androidx.appcompat.app.AlertDialog,
        ssid: String,
        password: String,
        bssid: String?,
        onConnected: (android.net.Network) -> Unit
    )

    fun enableWifi(): Boolean

    fun isBluetoothEnabled(adapter: android.bluetooth.BluetoothAdapter): Boolean

    fun queryMedia(data: Map<String, String> = mapOf(), requestAndSaveImage: Boolean = true)

    fun registerReceivers()

    fun scanForGoPros(
        activity: android.app.Activity,
        onSelected: (android.bluetooth.BluetoothDevice?) -> Unit
    ) = Unit

    fun shutterOff()

    fun startStream()

    fun stopStream()

    fun unregisterReceivers()
}