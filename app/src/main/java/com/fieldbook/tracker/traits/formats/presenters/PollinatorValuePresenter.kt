package com.fieldbook.tracker.traits.formats.presenters

import android.content.Context
import android.util.Log
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.PollinatorTraitLayout
import org.json.JSONObject

class PollinatorValuePresenter : ValuePresenter {

    companion object {
        private const val TAG = "PollinatorValuePresenter"
    }

    //readable per-category breakdown for export instead of the raw json value
    override fun represent(context: Context, value: Any, trait: TraitObject?): String {
        val raw = value as? String
        if (raw.isNullOrEmpty()) return ""
        return try {
            val json = JSONObject(raw)
            val seconds = json.optInt(PollinatorTraitLayout.DURATION_KEY)
            val countsJson = json.optJSONObject(PollinatorTraitLayout.COUNTS_KEY) ?: JSONObject()
            val counts = PollinatorTraitLayout.categoriesFor(context, trait)
                .joinToString(" | ") { "${it.label}: ${countsJson.optInt(PollinatorTraitLayout.keyOf(it))}" }
            val elapsed = "%d:%02d".format(seconds / 60, seconds % 60)
            "$counts | $elapsed"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to represent value: $raw", e)
            raw
        }
    }
}
