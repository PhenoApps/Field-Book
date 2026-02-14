package com.fieldbook.tracker.devices.multispeq

import android.content.Context
import com.fieldbook.tracker.R
import org.json.JSONArray
import org.json.JSONObject

/** A single bundled protocol definition. */
data class MultispeqProtocol(
    val id: String,
    val name: String,
    /** The raw JSON value to send as a command (string or array). */
    val commandJson: String
)

/** Loads [MultispeqProtocol] entries from `res/raw/multispeq_protocols.json`. */
object MultispeqProtocolLoader {

    fun load(context: Context): List<MultispeqProtocol> {
        return try {
            val raw = context.resources.openRawResource(R.raw.multispeq_protocols)
                .bufferedReader()
                .use { it.readText() }
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("id")
                val name = obj.optString("name")
                // "code" can be a JSONArray or a plain String (e.g. "1" for device info)
                val commandJson = when {
                    obj.has("code") && obj.get("code") is JSONArray ->
                        obj.getJSONArray("code").toString()
                    obj.has("code") ->
                        obj.getString("code")
                    else -> return@mapNotNull null
                }
                MultispeqProtocol(id = id, name = name, commandJson = commandJson)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
