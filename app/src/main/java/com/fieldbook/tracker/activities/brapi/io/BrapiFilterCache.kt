package com.fieldbook.tracker.activities.brapi.io

import android.content.Context
import android.os.SystemClock
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.preferences.GeneralKeys
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.lang.reflect.Type

class BrapiFilterCache {
    companion object {

        private const val JSON_FILE_NAME = "com.fieldbook.tracker.activities.filters.json"

        enum class CacheClearInterval {
            EVERY, DAILY, WEEKLY, NEVER
        }

        fun checkClearCache(context: Context) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val currentTime = System.currentTimeMillis()
            when (prefs.getString(GeneralKeys.BRAPI_INVALIDATE_CACHE_INTERVAL, CacheClearInterval.NEVER.ordinal.toString())) {
                CacheClearInterval.EVERY.ordinal.toString() -> delete(context, true)
                CacheClearInterval.DAILY.ordinal.toString() -> {
                    val lastCleared = prefs.getLong(GeneralKeys.BRAPI_INVALIDATE_CACHE_LAST_CLEAR, 0)
                    if (currentTime - lastCleared > 24 * 60 * 60 * 1000) {
                        delete(context, true)
                    }
                }
                CacheClearInterval.WEEKLY.ordinal.toString() -> {
                    val lastCleared = prefs.getLong(GeneralKeys.BRAPI_INVALIDATE_CACHE_LAST_CLEAR, 0)
                    if (currentTime - lastCleared > 7 * 24 * 60 * 60 * 1000) {
                        delete(context, true)
                    }
                }
            }
        }

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

        fun clearPreferences(context: Context) {
            with(PreferenceManager.getDefaultSharedPreferences(context).edit()) {
                for (f in listOf(
                    BrapiTrialsFilterActivity.FILTER_NAME,
                    BrapiSeasonsFilterActivity.FILTER_NAME,
                    BrapiProgramFilterActivity.FILTER_NAME,
                    BrapiCropsFilterActivity.FILTER_NAME
                )) {
                    remove(f)
                }
                remove(GeneralKeys.LIST_FILTER_TEXTS)
                remove(GeneralKeys.BRAPI_INVALIDATE_CACHE_LAST_CLEAR)
                apply()
            }
        }

        fun delete(context: Context, clearPreferences: Boolean = false) {

            if (clearPreferences) {
                clearPreferences(context)
            }

            context.externalCacheDir?.let {
                File(it, JSON_FILE_NAME).delete()
            }

            PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putLong(GeneralKeys.BRAPI_INVALIDATE_CACHE_LAST_CLEAR, System.currentTimeMillis())
                .apply()

        }

        private fun getTypeToken(): Type =
            TypeToken.getParameterized(List::class.java, TrialStudyModel::class.java).type

    }
}