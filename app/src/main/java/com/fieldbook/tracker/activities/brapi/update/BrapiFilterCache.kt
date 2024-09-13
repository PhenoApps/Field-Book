package com.fieldbook.tracker.activities.brapi.update

import android.content.Context
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.preferences.GeneralKeys
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.lang.reflect.Type

class BrapiFilterCache {
    companion object {

        private const val JSON_FILE_NAME = "com.fieldbook.tracker.activities.filters.json"

        fun getStoredModels(context: Context): List<TrialStudyModel> {

            context.externalCacheDir?.let { cacheDir ->
                val file = File(cacheDir, JSON_FILE_NAME)
                if (file.exists()) {
                    val json = file.readText()
                    return Gson().fromJson(
                        json,
                        TypeToken.getParameterized(
                            List::class.java,
                            TrialStudyModel::class.java
                        ).type
                    )
                }
            }
            return listOf()
        }

        fun saveToStorage(context: Context, list: List<TrialStudyModel>) {
            val json = Gson().toJson(list, getTypeToken())
            context.externalCacheDir?.let {
                File(it, JSON_FILE_NAME).writeText(json)
            }
        }

        fun delete(context: Context, clearPreferences: Boolean = false) {

            if (clearPreferences) {
                PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .remove(GeneralKeys.LIST_FILTER_TEXTS).apply()
            }

            context.externalCacheDir?.let {
                File(it, JSON_FILE_NAME).delete()
            }
        }

        private fun getTypeToken(): Type =
            TypeToken.getParameterized(List::class.java, TrialStudyModel::class.java).type

    }
}