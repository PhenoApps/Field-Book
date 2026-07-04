package com.fieldbook.tracker.preferences

import android.content.SharedPreferences
import androidx.core.content.edit

object TraitScopePreferences {

    private const val STUDY_TRAIT_IDS_PREFIX = "study_trait_ids_"
    private const val STUDY_TRAIT_VISIBILITY_PREFIX = "study_trait_visibility_"
    private const val STUDY_TRAIT_INITIALIZED_PREFIX = "study_trait_initialized_"

    @JvmStatic
    fun getStudyTraitIds(preferences: SharedPreferences, studyId: Int): Set<String> {
        if (studyId < 0) return emptySet()
        return preferences.getStringSet("$STUDY_TRAIT_IDS_PREFIX$studyId", emptySet()) ?: emptySet()
    }

    @JvmStatic
    fun getStudyVisibleTraitIds(preferences: SharedPreferences, studyId: Int): Set<String>? {
        if (studyId < 0) return null
        return preferences.getStringSet("$STUDY_TRAIT_VISIBILITY_PREFIX$studyId", null)
    }

    @JvmStatic
    fun setStudyVisibleTraitIds(preferences: SharedPreferences, studyId: Int, traitIds: Set<String>) {
        if (studyId < 0) return
        preferences.edit { putStringSet("$STUDY_TRAIT_VISIBILITY_PREFIX$studyId", traitIds) }
    }

    /**
     * Returns true if a study's trait scope has been explicitly initialized
     * (even if the resulting set is empty due to all traits being removed).
     */
    @JvmStatic
    fun isStudyTraitScopeInitialized(preferences: SharedPreferences, studyId: Int): Boolean {
        if (studyId < 0) return false
        return preferences.getBoolean("$STUDY_TRAIT_INITIALIZED_PREFIX$studyId", false)
    }

    /**
     * Returns the scoped trait IDs for a study, initializing them from [fallbackTraitIds]
     * only if the scope has never been initialized before.
     *
     * If the scope was previously initialized and the set is empty (e.g. all traits
     * were removed), the empty set is returned as-is — it will NOT be repopulated.
     */
    @JvmStatic
    fun getOrInitializeStudyTraitIds(
        preferences: SharedPreferences,
        studyId: Int,
        fallbackTraitIds: Set<String>,
    ): Set<String> {
        if (studyId < 0) return emptySet()

        // If already initialized (even to empty), respect the current value
        if (isStudyTraitScopeInitialized(preferences, studyId)) {
            return getStudyTraitIds(preferences, studyId)
        }

        // First-time initialization: populate from fallback
        if (fallbackTraitIds.isEmpty()) return emptySet()

        preferences.edit {
            putStringSet("$STUDY_TRAIT_IDS_PREFIX$studyId", fallbackTraitIds)
            putBoolean("$STUDY_TRAIT_INITIALIZED_PREFIX$studyId", true)
        }
        return fallbackTraitIds
    }

    @JvmStatic
    fun addStudyTraitIds(preferences: SharedPreferences, studyId: Int, traitIds: Set<String>) {
        if (studyId < 0 || traitIds.isEmpty()) return

        val merged = getStudyTraitIds(preferences, studyId).toMutableSet().apply {
            addAll(traitIds)
        }

        preferences.edit {
            putStringSet("$STUDY_TRAIT_IDS_PREFIX$studyId", merged)
            putBoolean("$STUDY_TRAIT_INITIALIZED_PREFIX$studyId", true)
        }
    }

    @JvmStatic
    fun setStudyTraitIds(preferences: SharedPreferences, studyId: Int, traitIds: Set<String>) {
        if (studyId < 0) return
        preferences.edit {
            putStringSet("$STUDY_TRAIT_IDS_PREFIX$studyId", traitIds)
            putBoolean("$STUDY_TRAIT_INITIALIZED_PREFIX$studyId", true)
        }
    }
}
