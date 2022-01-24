package com.fieldbook.tracker.vuzix

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.os.IBinder
import android.util.Log
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.preferences.GeneralKeys
import java.lang.StringBuilder
import java.nio.charset.StandardCharsets

class DataTransferService : Service() {

    private var mBluetoothServer: BluetoothServer? = null

    private var mActivityReceiver: ActivityReceiver? = null

    private var mIntentFilter: IntentFilter? = null

    private var mAdapter: BluetoothAdapter? = null

    private var mDevice: BluetoothDevice? = null

    private val TAG = "DataTransferService"

    override fun onCreate() {

        Log.d(TAG, "Data transfer service created.")

        mActivityReceiver = ActivityReceiver()
        mIntentFilter = IntentFilter()
        mIntentFilter?.addAction(BluetoothServer.ACTION_DATA_CHANGE)
        mIntentFilter?.addAction(BluetoothServer.ACTION_TRAITS)
        mIntentFilter?.addAction(BluetoothServer.ACTION_STATUS)
        mIntentFilter?.addAction(BluetoothServer.ACTION_DEVICE_CHANGE)

        registerReceiver(mActivityReceiver, mIntentFilter)

        mAdapter = BluetoothAdapter.getDefaultAdapter()
        mBluetoothServer = BluetoothServer(this)

        startBluetoothConnection()
    }

    private fun startBluetoothConnection() {

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val deviceAddress = prefs.getString(GeneralKeys.VUZIX_DEVICE, "DEFAULT")

        if (!deviceAddress.equals("DEFAULT")) {
            val paired = mAdapter?.bondedDevices ?: setOf<BluetoothDevice>()
            for (device in paired) {
                if (device.address == deviceAddress) {
                    mDevice = device
                    break
                }
            }
        }

        if (mDevice != null) {
            mBluetoothServer?.init(mDevice, mAdapter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        unregisterReceiver(mActivityReceiver)
        mBluetoothServer?.cancelConnection()
        super.onDestroy()
    }

    private inner class ActivityReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            //Log.i("Receiver", "Broadcast received: $action")
            when (action) {
                BluetoothServer.ACTION_TRAITS -> {
                    intent.extras?.let { traitExtras ->
                        if (traitExtras.containsKey(DataMonitor.EXTRA_TRAITS)) {
                            traitExtras.getStringArrayList(DataMonitor.EXTRA_TRAITS)?.forEach {
                                val cmd = "TRAIT: ${it.replace(":", "")}\r\n".toByteArray(StandardCharsets.UTF_8)
                                mBluetoothServer?.write(cmd)
                            }
                        }
                    }
                }
                BluetoothServer.ACTION_DATA_CHANGE -> {
                    intent.extras?.let { extras ->
                        if (extras.containsKey(DataMonitor.EXTRA_DATA)) {
                            mBluetoothServer?.write("\r\n${extras.getString(DataMonitor.EXTRA_DATA)}\r\n"
                                .toByteArray(StandardCharsets.UTF_8))
                        }
                    }
                }
                BluetoothServer.ACTION_STATUS -> {
                    mBluetoothServer?.cancelConnection()
                    startBluetoothConnection()
                }
                BluetoothServer.ACTION_DEVICE_CHANGE -> {
                    mBluetoothServer?.cancelConnection()
                    startBluetoothConnection()
                }
            }
        }
    }
}