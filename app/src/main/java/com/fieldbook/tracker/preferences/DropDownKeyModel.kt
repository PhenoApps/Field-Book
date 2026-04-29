package com.fieldbook.tracker.preferences

import android.content.Context
import androidx.preference.PreferenceManager

data class DropDownKeyModel(
    val attributeKey: String,
    val traitKey: String
) {

    companion object {
        const val DEFAULT_ATTRIBUTE_LABEL = "plot_id"
        const val DEFAULT_TRAIT_ID = "-1"
    }

    fun getValues(context: Context): Pair<String, String> {
        val attributeValue = getAttributeValue(context)
        val traitValue = getTraitValue(context)
        return Pair(attributeValue, traitValue)
    }

    private fun getAttributeValue(context: Context): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return preferences.getString(attributeKey, DEFAULT_ATTRIBUTE_LABEL)
            ?: DEFAULT_ATTRIBUTE_LABEL
    }

    private fun getTraitValue(context: Context): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return preferences.getString(traitKey, DEFAULT_TRAIT_ID)
            ?: DEFAULT_TRAIT_ID
    }
}