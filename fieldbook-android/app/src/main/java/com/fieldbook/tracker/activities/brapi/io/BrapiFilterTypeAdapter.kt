package com.fieldbook.tracker.activities.brapi.io

import android.content.SharedPreferences
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class BrapiFilterTypeAdapter {

    companion object {

        fun toModelList(prefs: SharedPreferences, key: String): List<CheckboxListAdapter.Model> {

            val jsonString = prefs.getString(key, "")

            return try {
                Gson().fromJson<List<CheckboxListAdapter.Model>>(jsonString,
                    object : TypeToken<List<CheckboxListAdapter.Model>>() {}.type) ?: listOf()
            } catch (e: Exception) {
                e.printStackTrace()
                listOf()
            }
        }

        fun saveFilter(prefs: SharedPreferences, key: String, list: List<CheckboxListAdapter.Model>) {
            val jsonString = Gson().toJson(list)
            prefs.edit().putString(key, jsonString).apply()
        }

        fun deleteFilterId(prefs: SharedPreferences, key: String, id: String) {
            val list = toModelList(prefs, key)
            val newList = list.filter { it.id != id }
            saveFilter(prefs, key, newList)
        }
    }
}