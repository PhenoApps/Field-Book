package com.fieldbook.tracker.views

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.fieldbook.tracker.R

open class GattDeviceSettingsView : ConstraintLayout {

    private val disconnectButton: Button
    private val nameTv: TextView
    private val addressTv: TextView

    private var onDisconnect: (() -> Unit)? = null

    init {
        val view = inflate(context, R.layout.view_device_settings_dialog, this)
        disconnectButton = view.findViewById(R.id.disconnect_btn)
        nameTv = view.findViewById(R.id.name_tv)
        addressTv = view.findViewById(R.id.address_tv)
    }

    constructor(ctx: Context, device: BluetoothDevice, onDisconnect: () -> Unit) : super(ctx) {
        setup()
        this.onDisconnect = onDisconnect

        nameTv.text = context.getString(R.string.device_settings_dialog_name, device.name ?: context.getString(R.string.device_settings_dialog_unknown_name))
        addressTv.text = context.getString(R.string.device_settings_dialog_address, device.address)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    private fun setup() {

        setupDisconnectButton()
    }

    private fun setupDisconnectButton() {

        disconnectButton.setOnClickListener {

            this.onDisconnect?.invoke()

        }
    }
}