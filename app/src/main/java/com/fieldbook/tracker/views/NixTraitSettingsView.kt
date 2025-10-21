package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.traits.SpectralTraitLayout.State.*
import com.nixsensor.universalsdk.IDeviceCompat

open class NixTraitSettingsView : ConstraintLayout {

    private val prefs by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    private val radioGroup: RadioGroup
    private val disconnectButton: Button
    private val batteryLevel: TextView
    private val rssiText: TextView
    private val typeText: TextView
    private val factoryText: TextView
    private val serialNumberText: TextView
    private val firmwareText: TextView
    private val softwareText: TextView
    private val externalPowerStateText: TextView

    private var onDisconnect: (() -> Unit)? = null

    init {
        val view = inflate(context, R.layout.view_trait_nix_settings, this)
        radioGroup = view.findViewById(R.id.view_trait_nix_settings_rg)
        disconnectButton = view.findViewById(R.id.disconnect_btn)
        batteryLevel = view.findViewById(R.id.view_trait_nix_settings_battery_level)
        rssiText = view.findViewById(R.id.view_trait_nix_settings_rssi)
        typeText = view.findViewById(R.id.view_trait_nix_settings_type)
        factoryText = view.findViewById(R.id.view_trait_nix_settings_factory_note)
        serialNumberText = view.findViewById(R.id.view_trait_nix_settings_serial_number)
        firmwareText = view.findViewById(R.id.view_trait_nix_settings_firmware_version)
        softwareText = view.findViewById(R.id.view_trait_nix_settings_software_version)
        externalPowerStateText = view.findViewById(R.id.view_trait_nix_settings_external_power_state)
    }

    constructor(ctx: Context, device: IDeviceCompat, onDisconnect: () -> Unit) : super(ctx) {
        setup()
        this.onDisconnect = onDisconnect
        radioGroup.visibility = if (!device.providesSpectral) GONE else VISIBLE

        batteryLevel.text = context.getString(R.string.view_trait_nix_settings_battery_level, device.batteryLevel.toString())
        rssiText.text = context.getString(R.string.view_trait_nix_settings_rssi, device.rssi.toString())
        typeText.text = context.getString(R.string.view_trait_nix_settings_type, device.type)
        factoryText.text = context.getString(R.string.view_trait_nix_settings_factory_note, device.note)
        serialNumberText.text = context.getString(R.string.view_trait_nix_settings_serial_number, device.serialNumber)
        firmwareText.text = context.getString(R.string.view_trait_nix_settings_firmware_version, device.firmwareVersion.string)
        softwareText.text = context.getString(R.string.view_trait_nix_settings_software_version, device.softwareVersion.string)
        externalPowerStateText.text = context.getString(R.string.view_trait_nix_settings_external_power_state, device.extPowerState.toString())
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    fun commitChanges() {

        prefs.edit {
            putInt(
                GeneralKeys.SPECTRAL_MODE,
                when (radioGroup.checkedRadioButtonId) {
                    R.id.view_trait_nix_settings_spectral_rb -> Spectral.id
                    else -> Color.id
                }
            )
        }
    }

    private fun setup() {

        setupRadioGroup()

        setupDisconnectButton()
    }

    private fun setupRadioGroup() {

        radioGroup.setOnCheckedChangeListener { group, isChecked ->

            group.checkedRadioButtonId

        }

        radioGroup.check(
            when (prefs.getInt(GeneralKeys.SPECTRAL_MODE, Spectral.id)) {
                Spectral.id -> R.id.view_trait_nix_settings_spectral_rb
                else -> R.id.view_trait_nix_settings_color_rb
            }
        )
    }

    private fun setupDisconnectButton() {

        disconnectButton.setOnClickListener {

            this.onDisconnect?.invoke()

        }
    }
}