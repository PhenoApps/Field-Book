package com.fieldbook.tracker.traits.formats.presenters

import android.content.Context
import android.util.Log
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.PollinatorTraitLayout
import com.fieldbook.tracker.utilities.JsonUtil
import org.json.JSONObject

class PollinatorValuePresenter : ValuePresenter {

    companion object {
        private const val TAG = "PollinatorValuePresenter"
    }

    //readable per-category breakdown for export instead of the raw json value
    override fun represent(context: Context, value: Any, trait: TraitObject?): String {
        val raw = value as? String
        if (raw.isNullOrEmpty()) return ""
        //missing observations are stored as NA, they are not json
        if (raw == "NA" || !JsonUtil.isJsonValid(raw)) return raw
        return try {
            val json = JSONObject(raw)
            val seconds = json.optInt(PollinatorTraitLayout.DURATION_KEY)
            val countsJson = json.optJSONObject(PollinatorTraitLayout.COUNTS_KEY) ?: JSONObject()
            //represent what was collected, categories edited after collection keep their stored key
            val labels = PollinatorTraitLayout.categoriesFor(trait)
                .associate { PollinatorTraitLayout.keyOf(it) to it.label }
            val counts = countsJson.keys().asSequence()
                .joinToString(" | ") { key -> "${labels[key] ?: key}: ${countsJson.optInt(key)}" }
            val elapsed = "%d:%02d".format(seconds / 60, seconds % 60)
            "$counts | $elapsed"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to represent value: $raw", e)
            raw
        }
    }
}
