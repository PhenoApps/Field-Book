package com.fieldbook.tracker.brapi.service

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.preferences.PreferenceKeys
import java.util.UUID

object BrapiDeviceIdProvider {

    private const val TAG = "BrapiDeviceIdProvider"
    private val lock = Any()

    @JvmStatic
    fun getOrCreate(context: Context): String = synchronized(lock) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val existing = prefs.getString(PreferenceKeys.BRAPI_DEVICE_ID, null)
        if (!existing.isNullOrEmpty()) {
            return existing
        }

        val generated = UUID.randomUUID().toString()
        val committed = prefs.edit().putString(PreferenceKeys.BRAPI_DEVICE_ID, generated).commit()
        if (!committed) {
            Log.w(TAG, "Failed to persist BrAPI device id synchronously")
        }

        return prefs.getString(PreferenceKeys.BRAPI_DEVICE_ID, null) ?: generated
    }
}
