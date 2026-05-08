package com.fieldbook.shared.brapi

import com.fieldbook.shared.brapi.model.v2.core.BrapiStudyDetails
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiTraitDetails
import com.fieldbook.shared.preferences.PreferenceKeys
import com.russhwolf.settings.Settings
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val JSON_FILE_NAME = "com.fieldbook.shared.brapi.filters.json"

@Serializable
data class BrapiFilterCacheModel(
    val sourceUrl: String? = null,
    val studies: List<BrapiStudyDetails> = emptyList(),
    val traits: Map<String, BrapiTraitDetails> = emptyMap(),
) {
    companion object {
        fun empty() = BrapiFilterCacheModel()
    }
}

object BrapiFilterCache {
    enum class CacheClearInterval {
        EVERY,
        DAILY,
        WEEKLY,
        NEVER
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun saveTraits(sourceUrl: String, traits: List<BrapiTraitDetails>): Boolean {
        val normalizedSourceUrl = sourceUrl.hostForCache()
        val existing = getStoredModels()
        val existingStudies = if (existing.sourceUrl == normalizedSourceUrl) {
            existing.studies
        } else {
            emptyList()
        }
        val mergedTraits = if (existing.sourceUrl == normalizedSourceUrl) {
            existing.traits.toMutableMap()
        } else {
            mutableMapOf()
        }
        traits.forEach { trait ->
            mergedTraits[trait.observationVariableDbId] = trait
        }

        return saveToStorage(
            BrapiFilterCacheModel(
                sourceUrl = normalizedSourceUrl,
                studies = existingStudies,
                traits = mergedTraits,
            )
        )
    }

    fun saveStudies(sourceUrl: String, studies: List<BrapiStudyDetails>): Boolean {
        val normalizedSourceUrl = sourceUrl.hostForCache()
        val existing = getStoredModels()
        val existingTraits = if (existing.sourceUrl == normalizedSourceUrl) {
            existing.traits
        } else {
            emptyMap()
        }

        return saveToStorage(
            BrapiFilterCacheModel(
                sourceUrl = normalizedSourceUrl,
                studies = studies,
                traits = existingTraits,
            )
        )
    }

    fun getStoredModels(sourceUrl: String? = null): BrapiFilterCacheModel {
        val model = readFromStorage()
        val expectedSource = sourceUrl?.hostForCache()
        return if (expectedSource == null || model.sourceUrl == null || model.sourceUrl == expectedSource) {
            model
        } else {
            BrapiFilterCacheModel.empty()
        }
    }

    fun checkClearCache(settings: Settings = Settings()) {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        when (settings.getString(
            PreferenceKeys.BRAPI_INVALIDATE_CACHE_INTERVAL,
            CacheClearInterval.NEVER.ordinal.toString()
        )) {
            CacheClearInterval.EVERY.ordinal.toString() -> delete(settings = settings)
            CacheClearInterval.DAILY.ordinal.toString() -> {
                val lastCleared = settings.getLong(PreferenceKeys.BRAPI_INVALIDATE_CACHE_LAST_CLEAR, 0L)
                if (currentTime - lastCleared > 24L * 60L * 60L * 1000L) {
                    delete(settings = settings)
                }
            }

            CacheClearInterval.WEEKLY.ordinal.toString() -> {
                val lastCleared = settings.getLong(PreferenceKeys.BRAPI_INVALIDATE_CACHE_LAST_CLEAR, 0L)
                if (currentTime - lastCleared > 7L * 24L * 60L * 60L * 1000L) {
                    delete(settings = settings)
                }
            }
        }
    }

    fun saveToStorage(model: BrapiFilterCacheModel): Boolean {
        return writeBrapiFilterCacheFile(JSON_FILE_NAME, json.encodeToString(model))
    }

    fun delete(
        clearPreferences: Boolean = false,
        settings: Settings = Settings(),
    ): Boolean {
        if (clearPreferences) {
            settings.remove(PreferenceKeys.BRAPI_INVALIDATE_CACHE_LAST_CLEAR)
        }

        val deleted = deleteBrapiFilterCacheFile(JSON_FILE_NAME)
        settings.putLong(PreferenceKeys.BRAPI_INVALIDATE_CACHE_LAST_CLEAR, Clock.System.now().toEpochMilliseconds())
        return deleted
    }

    private fun readFromStorage(): BrapiFilterCacheModel {
        val raw = readBrapiFilterCacheFile(JSON_FILE_NAME) ?: return BrapiFilterCacheModel.empty()
        return runCatching { json.decodeFromString<BrapiFilterCacheModel>(raw) }
            .getOrElse { BrapiFilterCacheModel.empty() }
    }

    private fun String.hostForCache(): String {
        return trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
            .substringBefore("/")
    }
}

internal expect fun readBrapiFilterCacheFile(fileName: String): String?

internal expect fun writeBrapiFilterCacheFile(fileName: String, contents: String): Boolean

internal expect fun deleteBrapiFilterCacheFile(fileName: String): Boolean
