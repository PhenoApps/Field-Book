package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.fieldbook.tracker.R
import com.ISCSDK.ISCNIRScanSDK.NanoDevice

open class InnoSpectraNanoSettingsView : ConstraintLayout {

    private val disconnectButton: Button
    private val deviceNameText: TextView
    private val macText: TextView

    private var onDisconnect: (() -> Unit)? = null

    init {
        // inflate the new InnoSpectra Nano settings layout
        val view = inflate(context, R.layout.view_trait_innospectra_nano_settings, this)
        disconnectButton = view.findViewById(R.id.view_trait_innospectra_disconnect_btn)
        deviceNameText = view.findViewById(R.id.view_trait_innospectra_device_name)
        macText = view.findViewById(R.id.view_trait_innospectra_mac)
    }

    constructor(ctx: Context, device: NanoDevice, onDisconnect: () -> Unit) : super(ctx) {
        setup()
        this.onDisconnect = onDisconnect
        deviceNameText.text = device.nanoName
        macText.text = device.nanoMac
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
