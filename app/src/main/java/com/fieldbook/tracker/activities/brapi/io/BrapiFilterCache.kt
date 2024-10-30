package com.fieldbook.tracker.activities.brapi.io

import android.content.Context
import android.os.SystemClock
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.preferences.GeneralKeys
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.brapi.v2.model.pheno.BrAPIObservationVariable
import java.io.File
import java.lang.reflect.Type

class BrapiFilterCache {
    companion object {

        private const val JSON_FILE_NAME = "com.fieldbook.tracker.activities.filters.json"

        enum class CacheClearInterval {
            EVERY, DAILY, WEEKLY, NEVER
        }

        fun saveVariablesToStudy(context: Context, studyDbId: String, models: List<BrAPIObservationVariable>) {
            saveToStorage(context,
                getStoredModels(context).map {
                    if (it.study.studyDbId == studyDbId) {
                        it.copy(variables = models)
                    } else {
                        it
                    }
                }
            )
            saveToStorage(context,
                getStoredModels(context).map {
                    if (it.variables == null) {
                        it.copy(variables = listOf())
                    } else {
                        it
                    }
                }
            )
        }

        fun checkClearCache(context: Context) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val currentTime = System.currentTimeMillis()
            when (prefs.getString(GeneralKeys.BRAPI_INVALIDATE_CACHE_INTERVAL, CacheClearInterval.NEVER.ordinal.toString())) {
                CacheClearInterval.EVERY.ordinal.toString() -> delete(context, false)
                CacheClearInterval.DAILY.ordinal.toString() -> {
                    val lastCleared = prefs.getLong(GeneralKeys.BRAPI_INVALIDATE_CACHE_LAST_CLEAR, 0)
                    if (currentTime - lastCleared > 24 * 60 * 60 * 1000) {
                        delete(context, false)
                    }
                }
                CacheClearInterval.WEEKLY.ordinal.toString() -> {
                    val lastCleared = prefs.getLong(GeneralKeys.BRAPI_INVALIDATE_CACHE_LAST_CLEAR, 0)
                    if (currentTime - lastCleared > 7 * 24 * 60 * 60 * 1000) {
                        delete(context, false)
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

        fun clearPreferences(context: Context, filterer: String = "") {
            with(PreferenceManager.getDefaultSharedPreferences(context).edit()) {
                for (f in listOf(
                    BrapiTrialsFilterActivity.FILTER_NAME,
                    BrapiSeasonsFilterActivity.FILTER_NAME,
                    BrapiProgramFilterActivity.FILTER_NAME,
                    BrapiCropsFilterActivity.FILTER_NAME,
                    BrapiStudyFilterActivity.FILTER_NAME,
                    BrapiTraitFilterActivity.FILTER_NAME,
                )) {
                    remove("$filterer$f")
                    remove("${f}${GeneralKeys.LIST_FILTER_TEXTS}")
                }
                remove(GeneralKeys.BRAPI_INVALIDATE_CACHE_LAST_CLEAR)
                apply()
            }
        }

        fun delete(context: Context, clearPreferences: Boolean = false) {

            if (clearPreferences) {
                clearPreferences(context)
                clearPreferences(context, BrapiStudyFilterActivity.FILTERER_KEY)
                clearPreferences(context, BrapiTraitFilterActivity.FILTERER_KEY)
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