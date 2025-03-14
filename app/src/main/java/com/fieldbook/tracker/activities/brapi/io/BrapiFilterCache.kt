package com.fieldbook.tracker.activities.brapi.io

import android.content.Context
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.activities.brapi.io.filter.BrapiCropsFilterActivity
import com.fieldbook.tracker.activities.brapi.io.filter.BrapiProgramFilterActivity
import com.fieldbook.tracker.activities.brapi.io.filter.BrapiSeasonsFilterActivity
import com.fieldbook.tracker.activities.brapi.io.filter.BrapiTrialsFilterActivity
import com.fieldbook.tracker.activities.brapi.io.filter.filterer.BrapiStudyFilterActivity
import com.fieldbook.tracker.activities.brapi.io.filter.filterer.BrapiTraitFilterActivity
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
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

        fun saveVariables(context: Context, models: List<BrAPIObservationVariable>) {
            saveToStorage(context,
                getStoredModels(context).also { storedModel ->
                    models.forEach {
                        storedModel.variables[it.observationVariableDbId] = it
                    }
                })
        }

        fun checkClearCache(context: Context) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val currentTime = System.currentTimeMillis()
            when (prefs.getString(
                PreferenceKeys.BRAPI_INVALIDATE_CACHE_INTERVAL,
                CacheClearInterval.NEVER.ordinal.toString()
            )) {
                CacheClearInterval.EVERY.ordinal.toString() -> delete(context, false)
                CacheClearInterval.DAILY.ordinal.toString() -> {
                    val lastCleared =
                        prefs.getLong(PreferenceKeys.BRAPI_INVALIDATE_CACHE_LAST_CLEAR, 0)
                    if (currentTime - lastCleared > 24 * 60 * 60 * 1000) {
                        delete(context, false)
                    }
                }

                CacheClearInterval.WEEKLY.ordinal.toString() -> {
                    val lastCleared =
                        prefs.getLong(PreferenceKeys.BRAPI_INVALIDATE_CACHE_LAST_CLEAR, 0)
                    if (currentTime - lastCleared > 7 * 24 * 60 * 60 * 1000) {
                        delete(context, false)
                    }
                }
            }
        }

        fun getStoredModels(context: Context): BrapiCacheModel {

            context.externalCacheDir?.let { cacheDir ->
                val file = File(cacheDir, JSON_FILE_NAME)
                if (file.exists()) {
                    val json = file.readText()
                    return Gson().fromJson(
                        json,
                        getTypeToken()
                    )
                }
            }
            return BrapiCacheModel.empty()
        }

        fun saveStudyTrialsToFile(context: Context, models: List<TrialStudyModel>) {
            saveToStorage(context,
                getStoredModels(context).also { storedModel ->
                    storedModel.studies = models
                })
        }

        fun saveToStorage(context: Context, model: BrapiCacheModel) {
            val json = Gson().toJson(model, getTypeToken())
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
                remove(PreferenceKeys.BRAPI_INVALIDATE_CACHE_LAST_CLEAR)
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
                .putLong(PreferenceKeys.BRAPI_INVALIDATE_CACHE_LAST_CLEAR, System.currentTimeMillis())
                .apply()

        }

        private fun getTypeToken(): Type =
            TypeToken.get(BrapiCacheModel::class.java).type

    }
}