package com.fieldbook.tracker.preferences

import android.content.SharedPreferences
import androidx.core.content.edit

object TraitScopePreferences {

    private const val STUDY_TRAIT_IDS_PREFIX = "study_trait_ids_"
    private const val STUDY_TRAIT_VISIBILITY_PREFIX = "study_trait_visibility_"

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

    @JvmStatic
    fun getOrInitializeStudyTraitIds(
        preferences: SharedPreferences,
        studyId: Int,
        fallbackTraitIds: Set<String>,
    ): Set<String> {
        if (studyId < 0) return emptySet()

        val existing = getStudyTraitIds(preferences, studyId)
        if (existing.isNotEmpty()) return existing

        if (fallbackTraitIds.isEmpty()) return emptySet()

        preferences.edit { putStringSet("$STUDY_TRAIT_IDS_PREFIX$studyId", fallbackTraitIds) }
        return fallbackTraitIds
    }

    @JvmStatic
    fun addStudyTraitIds(preferences: SharedPreferences, studyId: Int, traitIds: Set<String>) {
        if (studyId < 0 || traitIds.isEmpty()) return

        val merged = getStudyTraitIds(preferences, studyId).toMutableSet().apply {
            addAll(traitIds)
        }

        preferences.edit { putStringSet("$STUDY_TRAIT_IDS_PREFIX$studyId", merged) }
    }

    @JvmStatic
    fun setStudyTraitIds(preferences: SharedPreferences, studyId: Int, traitIds: Set<String>) {
        if (studyId < 0) return
        preferences.edit { putStringSet("$STUDY_TRAIT_IDS_PREFIX$studyId", traitIds) }
    }
}
