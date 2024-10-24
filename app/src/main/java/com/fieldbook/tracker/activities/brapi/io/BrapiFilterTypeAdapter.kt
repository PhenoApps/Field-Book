package com.fieldbook.tracker.activities.brapi.io

import android.content.SharedPreferences
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class BrapiFilterTypeAdapter {

    companion object {

        fun toModelList(prefs: SharedPreferences, filterName: String): List<CheckboxListAdapter.Model> {

            val jsonString = prefs.getString(filterName, "")

            return try {
                Gson().fromJson<List<CheckboxListAdapter.Model>>(jsonString,
                    object : TypeToken<List<CheckboxListAdapter.Model>>() {}.type) ?: listOf()
            } catch (e: Exception) {
                e.printStackTrace()
                listOf()
            }
        }

        fun saveFilter(prefs: SharedPreferences, filterName: String, list: List<CheckboxListAdapter.Model>) {
            val jsonString = Gson().toJson(list)
            prefs.edit().putString(filterName, jsonString).apply()
        }

        fun deleteFilterId(prefs: SharedPreferences, filterName: String, id: String) {
            val list = toModelList(prefs, filterName)
            val newList = list.filter { it.id != id }
            saveFilter(prefs, filterName, newList)
        }
    }
}