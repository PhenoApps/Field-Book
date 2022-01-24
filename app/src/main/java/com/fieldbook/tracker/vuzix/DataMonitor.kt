package com.fieldbook.tracker.vuzix

import android.content.Context
import android.content.Intent
import com.fieldbook.tracker.objects.TraitObject

class DataMonitor(private val context: Context) {

    companion object {
        const val EXTRA_DATA = "com.fieldbook.tracker.vuzix.extras.data"
        const val EXTRA_TRAITS = "com.fieldbook.tracker.vuzix.extras.traits"
    }

    fun pushExtra(name: String, value: String) {
        with (Intent()) {
            action = BluetoothServer.ACTION_DATA_CHANGE
            putExtra(name, value)
            context.sendBroadcast(this)
        }
    }

    fun pushTraits(traits: Array<TraitObject>) {
        with (Intent()) {
            action = BluetoothServer.ACTION_TRAITS
            putStringArrayListExtra(EXTRA_TRAITS, ArrayList(traits.mapNotNull { it.trait }))
            context.sendBroadcast(this)
        }
    }
}