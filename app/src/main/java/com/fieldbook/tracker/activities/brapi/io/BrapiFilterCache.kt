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

        /**
         * The parsed cache file and the file's (last modified, length) when it was read or written.
         * The file can hold every trial and study of a large server, and parsing it on each screen,
         * on the main thread, paused opening the study import. Kept until the file changes.
         */
        @Volatile
        private var memory: Pair<Pair<Long, Long>, BrapiCacheModel>? = null

        private fun File.stamp() = lastModified() to length()

        //callers reassign studies and variables when filtering, so each gets its own model,
        //gson leaves fields missing from older cache files null despite their types
        @Suppress("USELESS_ELVIS", "UNNECESSARY_SAFE_CALL")
        private fun BrapiCacheModel.shallowCopy() =
            BrapiCacheModel(studies ?: emptyList(), variables?.toMutableMap() ?: mutableMapOf())

        enum class CacheClearInterval(val value: String) {
            EVERY("0"),
            DAILY("1"),
            WEEKLY("2"),
            NEVER("3");

            companion object {
                @JvmField
                val DEFAULT = NEVER

                fun fromValue(value: String?): CacheClearInterval =
                    entries.find { it.value == value } ?: DEFAULT
            }
        }

        private const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L
        private const val ONE_WEEK_MILLIS = 7 * ONE_DAY_MILLIS

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
            when (CacheClearInterval.fromValue(
                prefs.getString(
                    PreferenceKeys.BRAPI_INVALIDATE_CACHE_INTERVAL,
                    CacheClearInterval.DEFAULT.value
                )
            )) {
                CacheClearInterval.EVERY -> delete(context, false)
                CacheClearInterval.DAILY -> {
                    val lastCleared =
                        prefs.getLong(PreferenceKeys.BRAPI_INVALIDATE_CACHE_LAST_CLEAR, 0)
                    if (currentTime - lastCleared > ONE_DAY_MILLIS) {
                        delete(context, false)
                    }
                }

                CacheClearInterval.WEEKLY -> {
                    val lastCleared =
                        prefs.getLong(PreferenceKeys.BRAPI_INVALIDATE_CACHE_LAST_CLEAR, 0)
                    if (currentTime - lastCleared > ONE_WEEK_MILLIS) {
                        delete(context, false)
                    }
                }

                CacheClearInterval.NEVER -> Unit
            }
        }

        fun getStoredModels(context: Context): BrapiCacheModel {

            context.externalCacheDir?.let { cacheDir ->
                val file = File(cacheDir, JSON_FILE_NAME)
                if (file.exists()) {

                    val stamp = file.stamp()

                    val model = memory?.takeIf { it.first == stamp }?.second
                        ?: Gson().fromJson<BrapiCacheModel>(file.readText(), getTypeToken())
                            .also { memory = stamp to it }

                    return model.shallowCopy()
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
                val file = File(it, JSON_FILE_NAME)
                file.writeText(json)
                //the caller may keep changing its model, so the saved state is copied
                memory = file.stamp() to model.shallowCopy()
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

            memory = null

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