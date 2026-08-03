package com.fieldbook.tracker.utilities

import android.content.Context
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.Formats
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

object TreeDerivedTraitHelper {

    private const val KEY_SOURCE = "treeSourceTraitId"
    private const val KEY_SUMMARY = "treeSummaryTraitId"

    fun isExportOnlySummary(trait: TraitObject): Boolean =
        trait.format.equals(Formats.TREE_SUMMARY.getDatabaseName(), ignoreCase = true)

    /**
     * Tree summary is export-only regardless of import JSON / CSV visibility.
     * Call from import paths — keep format clamp out of [TraitObject.fromJson].
     */
    fun coerceExportOnlySummaryVisibility(trait: TraitObject) {
        if (isExportOnlySummary(trait)) {
            trait.visible = false
        }
    }

    fun createSummaryTrait(source: TraitObject, position: Int): TraitObject =
        TraitObject().apply {
            name = "${source.name} (summary)"
            alias = name
            synonyms = listOf(name)
            format = Formats.TREE_SUMMARY.getDatabaseName()
            // Export-only: not shown in Collect trait carousel (getVisibleTraits).
            // ExportUtil still includes linked summaries when the source tree is exported.
            visible = false
            realPosition = position
            details = "Derived from ${source.name}"
            additionalInfo = buildJsonObject {
                put(KEY_SOURCE, JsonPrimitive(source.id))
            }.toString()
        }

    fun linkTraits(source: TraitObject, summary: TraitObject) {
        val sourceInfo = parseInfo(source.additionalInfo).toMutableMap()
        sourceInfo[KEY_SUMMARY] = summary.id
        source.additionalInfo = Json.encodeToString(JsonObject.serializer(), JsonObject(sourceInfo.mapValues { JsonPrimitive(it.value) }))
        summary.additionalInfo = buildJsonObject {
            put(KEY_SOURCE, JsonPrimitive(source.id))
        }.toString()
    }

    fun resolveDerivedTrait(database: DataHelper, sourceTraitId: String): TraitObject? {
        val source = database.allTraitObjects.firstOrNull { it.id == sourceTraitId } ?: return null
        if (!source.format.equals(Formats.TREE_ARCHITECTURE.getDatabaseName(), ignoreCase = true)) {
            return null
        }
        val summaryId = parseInfo(source.additionalInfo)[KEY_SUMMARY] ?: return null
        val summary = database.allTraitObjects.firstOrNull { it.id == summaryId } ?: return null
        if (!summary.format.equals(Formats.TREE_SUMMARY.getDatabaseName(), ignoreCase = true)) {
            return null
        }
        return summary
    }

    fun resolveSourceTrait(database: DataHelper, summaryTraitId: String): TraitObject? {
        val summary = database.allTraitObjects.firstOrNull { it.id == summaryTraitId } ?: return null
        if (!summary.format.equals(Formats.TREE_SUMMARY.getDatabaseName(), ignoreCase = true)) {
            return null
        }
        val sourceId = parseInfo(summary.additionalInfo)[KEY_SOURCE] ?: return null
        val source = database.allTraitObjects.firstOrNull { it.id == sourceId } ?: return null
        if (!source.format.equals(Formats.TREE_ARCHITECTURE.getDatabaseName(), ignoreCase = true)) {
            return null
        }
        return source
    }

    /** Strip tree↔summary link keys (used on copy / before remapping import IDs). */
    fun clearTreeLinkKeys(raw: String?): String? {
        if (raw.isNullOrBlank()) return raw
        val map = parseInfo(raw).toMutableMap()
        map.remove(KEY_SOURCE)
        map.remove(KEY_SUMMARY)
        if (map.isEmpty()) return ""
        return Json.encodeToString(
            JsonObject.serializer(),
            JsonObject(map.mapValues { JsonPrimitive(it.value) }),
        )
    }

    fun summaryTraitId(source: TraitObject): String? =
        parseInfo(source.additionalInfo)[KEY_SUMMARY]

    fun sourceTraitId(summary: TraitObject): String? =
        parseInfo(summary.additionalInfo)[KEY_SOURCE]

    /** Export / UI: skip companion summaries that only hold blank or literal "0". */
    fun isMeaningfulSummaryValue(raw: String?): Boolean {
        val v = raw?.trim().orEmpty()
        return v.isNotEmpty() && v != "0"
    }

    fun hasMeaningfulSummaryObservations(database: DataHelper, summaryTraitId: String): Boolean {
        return try {
            database.getAllObservationsOfVariable(summaryTraitId).any {
                isMeaningfulSummaryValue(it.value)
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Create+link a summary companion if missing. Used only when [TreeSummary.hasContent]
     * so empty trees never get a useless `(summary)` trait.
     */
    fun ensureSummaryCompanion(database: DataHelper, sourceTraitId: String): TraitObject? {
        resolveDerivedTrait(database, sourceTraitId)?.let { return it }
        val source = database.getTraitById(sourceTraitId) ?: return null
        if (!source.format.equals(Formats.TREE_ARCHITECTURE.getDatabaseName(), ignoreCase = true)) {
            return null
        }
        val summary = createSummaryTrait(source, database.maxPositionFromTraits + 1)
        if (database.insertTraits(summary) == -1L) return null
        linkTraits(source, summary)
        database.updateTrait(source)
        database.updateTrait(summary)
        database.updateTraitVisibility(summary.id, false)
        return summary
    }

    /** Remove a linked summary that has no meaningful observations (or none at all). */
    fun dropEmptySummaryCompanion(database: DataHelper, sourceTraitId: String) {
        val summary = resolveDerivedTrait(database, sourceTraitId) ?: return
        if (hasMeaningfulSummaryObservations(database, summary.id)) return
        val source = database.getTraitById(sourceTraitId)
        if (source != null) {
            source.additionalInfo = clearTreeLinkKeys(source.additionalInfo)
            database.updateTrait(source)
        }
        database.deleteTrait(summary.id)
    }

    /**
     * After a bulk trait import, remap stale DB ids in additionalInfo to the newly
     * inserted traits by matching companion names (`X` ↔ `X (summary)`).
     */
    fun remapLinksAfterImport(traits: List<TraitObject>): List<TraitObject> {
        val byName = traits.associateBy { it.name }
        val updated = mutableListOf<TraitObject>()
        for (trait in traits) {
            if (!trait.format.equals(Formats.TREE_ARCHITECTURE.getDatabaseName(), ignoreCase = true)) {
                continue
            }
            val expectedSummaryName = "${trait.name} (summary)"
            val summary = byName[expectedSummaryName]
                ?: traits.firstOrNull {
                    it.format.equals(Formats.TREE_SUMMARY.getDatabaseName(), ignoreCase = true) &&
                        sourceTraitId(it) == trait.id
                }
            if (summary != null) {
                linkTraits(trait, summary)
                updated += trait
                updated += summary
            }
        }
        return updated.distinctBy { it.id }
    }

    private fun parseInfo(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val obj = Json.parseToJsonElement(raw) as? JsonObject ?: return emptyMap()
            obj.mapNotNull { (k, v) ->
                (v as? JsonPrimitive)?.content?.let { k to it }
            }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
