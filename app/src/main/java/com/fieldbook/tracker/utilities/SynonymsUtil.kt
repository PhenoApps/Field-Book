package com.fieldbook.tracker.utilities

import android.util.Log
import com.google.common.reflect.TypeToken
import com.google.gson.Gson

object SynonymsUtil {

    private const val TAG = "SynonymsUtil"

    private val gson = Gson()

    fun deserializeSynonyms(synonymsJson: String?): List<String> {
        return if (synonymsJson.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson(synonymsJson, type) ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Could not deserialize json: $synonymsJson", e)
                emptyList()
            }
        }
    }

    fun serializeSynonyms(synonyms: List<String>): String {
        return gson.toJson(synonyms)
    }

    fun addAliasToSynonyms(alias: String, synonyms: List<String>): List<String> {
        if (alias.isEmpty()) return synonyms

        return synonyms.toMutableList().apply {
            if (none { it == alias }) add(0, alias)
        }
    }

}