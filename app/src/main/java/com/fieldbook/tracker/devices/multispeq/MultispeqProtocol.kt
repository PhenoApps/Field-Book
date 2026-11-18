package com.fieldbook.tracker.devices.multispeq

import android.content.Context
import com.fieldbook.tracker.R
import org.json.JSONArray

/**
 * Protocols Field Book can send, in order from fastest to slowest.
 *
 * PAR, SPAD and Phi2 are the upstream JII-MultispeQ-Protocols definitions in
 * `res/raw/multispeq_protocols.json`; RIDES is `res/raw/photosynthesis_rides_2_1.json`.
 *
 * @param jsonId id of the entry in `multispeq_protocols.json`, or null for RIDES
 * @param provides parameters [MultispeqCalculator] can produce from this protocol's response
 */
enum class MultispeqProtocolType(
    val jsonId: String?,
    val provides: Set<MultispeqParameter>
) {
    PAR("par", setOf(MultispeqParameter.PAR)),
    SPAD("spad", setOf(MultispeqParameter.SPAD_VALUE)),
    PHI2("phi2", setOf(MultispeqParameter.PHI2, MultispeqParameter.LEF, MultispeqParameter.PAR)),
    RIDES(null, MultispeqParameter.values().toSet());

    companion object {

        /** The fastest protocol whose outputs cover every selected parameter. */
        fun select(params: Set<MultispeqParameter>): MultispeqProtocolType {
            if (params.isEmpty()) return RIDES
            return values().first { it.provides.containsAll(params) }
        }
    }
}

/** Loads the device command for a [MultispeqProtocolType] from raw resources. */
object MultispeqProtocolLoader {

    /**
     * RIDES is always sent whole: the macro reads ambient conditions from PAM and leaf angle
     * from DIRK_P700, and PAM's actinic light follows the earlier sub-protocols.
     */
    fun commandFor(context: Context, type: MultispeqProtocolType): JSONArray {
        val resources = context.resources
        val id = type.jsonId
            ?: return JSONArray(resources.openRawResource(R.raw.photosynthesis_rides_2_1)
                .bufferedReader().use { it.readText() })

        val protocols = JSONArray(resources.openRawResource(R.raw.multispeq_protocols)
            .bufferedReader().use { it.readText() })
        for (i in 0 until protocols.length()) {
            val obj = protocols.optJSONObject(i) ?: continue
            if (obj.optString("id") == id) return obj.getJSONArray("code")
        }
        throw IllegalStateException("Protocol '$id' missing from multispeq_protocols.json")
    }
}
