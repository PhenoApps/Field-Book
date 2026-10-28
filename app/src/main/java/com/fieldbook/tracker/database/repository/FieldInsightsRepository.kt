package com.fieldbook.tracker.database.repository

import android.content.SharedPreferences
import com.fieldbook.tracker.application.IoDispatcher
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.dao.FieldInsightsDao
import com.fieldbook.tracker.database.internalTimeFormatter
import com.fieldbook.tracker.insights.FieldInsightsData
import com.fieldbook.tracker.insights.InsightObservation
import com.fieldbook.tracker.insights.InsightStats
import com.fieldbook.tracker.insights.InsightTrait
import com.fieldbook.tracker.insights.InsightTraitKind
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.traits.CategoricalTraitLayout
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.traits.formats.feature.ChartableData
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import com.fieldbook.tracker.utilities.DateJsonUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.threeten.bp.OffsetDateTime as ThreeTenOffsetDateTime
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject

class FieldInsightsRepository @Inject constructor(
    private val database: DataHelper,
    private val prefs: SharedPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** Loads the whole field. Returns null when the field no longer exists. */
    suspend fun load(studyId: Int): FieldInsightsData? = withContext(ioDispatcher) {

        val field = database.getFieldObject(studyId) ?: return@withContext null

        val units = database.getFieldInsightsUnits(studyId)
        val unitIds = units.mapTo(HashSet()) { it.unitId }

        // Observations left behind by entries no longer in the field are dropped, so counts never
        // run past the field's size.
        val rows = database.getFieldInsightsObservations(studyId)
            .filter { it.value.isNotBlank() && it.value != "NA" && it.unitId in unitIds }
        val attributes = loadAttributes(database.getFieldInsightsAttributes(studyId))

        val rowsByTrait = rows.groupBy { it.traitId }
        val traitObjects = database.allTraitObjects.filter { it.id in rowsByTrait.keys }

        val traits = traitObjects.map { trait ->
            val values = rowsByTrait[trait.id].orEmpty().map { it.value }
            InsightTrait(
                id = trait.id,
                name = trait.alias.ifBlank { trait.name },
                format = trait.format,
                kind = kindOf(trait, values),
                categoryOrder = categoryOrder(trait)
            )
        }

        val traitObjectsById = traitObjects.associateBy { it.id }

        // Many observations share a timestamp's day, but parsing is cheap next to the query, so
        // each one is parsed as it comes rather than cached.
        val observations = traits.flatMap { trait ->
            val traitObject = traitObjectsById.getValue(trait.id)
            rowsByTrait[trait.id].orEmpty().map { row -> toObservation(row, trait, traitObject) }
        }

        val observationsByTrait = observations.groupBy { it.traitId }

        val plotNumeric = traits
            .filter { it.kind.isQuantitative }
            .associate { trait ->
                trait.id to observationsByTrait[trait.id].orEmpty()
                    .filter { it.numeric != null }
                    .groupBy { it.unitId }
                    .mapValues { (_, unitObservations) -> unitObservations.mapNotNull { it.numeric }.average() }
            }

        val plotCategories = traits
            .filter { it.kind == InsightTraitKind.CATEGORICAL }
            .associate { trait ->
                trait.id to observationsByTrait[trait.id].orEmpty()
                    .filter { it.categories.isNotEmpty() }
                    .groupBy { it.unitId }
                    .mapValues { (_, unitObservations) -> unitObservations.maxBy { it.rep }.categories }
            }

        val attributeNames = attributes.keys.toList()

        FieldInsightsData(
            fieldName = field.alias?.takeIf { it.isNotBlank() } ?: field.name.orEmpty(),
            unitIds = units.map { it.unitId },
            attributes = attributes,
            traits = traits,
            observations = observations,
            observationsByTrait = observationsByTrait,
            plotNumeric = plotNumeric,
            plotCategories = plotCategories,
            defaultRowAttribute = defaultAttribute(
                attributeNames,
                prefs.getString(GeneralKeys.MAP_ROW_ATTR, null),
                InsightStats.guessRowAttribute(attributeNames)
            ),
            defaultColumnAttribute = defaultAttribute(
                attributeNames,
                prefs.getString(GeneralKeys.MAP_COL_ATTR, null),
                InsightStats.guessColumnAttribute(attributeNames)
            )
        )
    }

    /**
     * Imported attributes by name. The units' position coordinate columns are not read: not
     * every database has them, and fields that carry a layout import it as attributes.
     */
    private fun loadAttributes(rows: List<FieldInsightsDao.AttributeRow>): Map<String, Map<String, String>> {

        val attributes = sortedMapOf<String, Map<String, String>>(String.CASE_INSENSITIVE_ORDER)

        rows.groupBy { it.name }.forEach { (name, values) ->
            attributes[name] = values.associate { it.unitId to it.value }
        }

        return LinkedHashMap(attributes)
    }

    private fun defaultAttribute(names: List<String>, saved: String?, guess: String?): String? =
        listOf(saved, guess).firstOrNull { it != null && it in names }

    private fun kindOf(trait: TraitObject, values: List<String>): InsightTraitKind {

        // Checked before the format lookup: the legacy "qualitative" name is categorical but has
        // no entry in Formats.
        if (trait.format in CategoricalTraitLayout.POSSIBLE_VALUES) return InsightTraitKind.CATEGORICAL

        val allNumeric = values.all { finiteOrNull(it) != null }

        // Plugin traits have no format here; their values decide.
        val format = Formats.entries.find { it.getDatabaseName() == trait.format }
            ?: return if (allNumeric) InsightTraitKind.NUMERIC else InsightTraitKind.OTHER

        return when {
            format.getTraitFormatDefinition() !is ChartableData -> InsightTraitKind.OTHER
            format == Formats.DATE -> InsightTraitKind.DATE
            format == Formats.BOOLEAN -> InsightTraitKind.CATEGORICAL
            allNumeric -> InsightTraitKind.NUMERIC
            else -> InsightTraitKind.CATEGORICAL
        }
    }

    private fun toObservation(
        row: FieldInsightsDao.ObservationRow,
        trait: InsightTrait,
        traitObject: TraitObject
    ): InsightObservation {

        val (day, time) = parseTimestamp(row.timestamp)

        return InsightObservation(
            unitId = row.unitId,
            traitId = row.traitId,
            numeric = when (trait.kind) {
                InsightTraitKind.NUMERIC -> finiteOrNull(row.value)
                InsightTraitKind.DATE -> DateJsonUtil.extractDayOfYear(row.value)?.toDouble()
                else -> null
            },
            categories = if (trait.kind == InsightTraitKind.CATEGORICAL) categoriesOf(row.value, traitObject) else emptyList(),
            collectedOn = day,
            collectedAt = time,
            collector = row.collector?.trim().orEmpty(),
            rep = row.rep?.toIntOrNull() ?: 1
        )
    }

    /**
     * The value as a number. toDoubleOrNull() also accepts "NaN" and "Infinity", which imported
     * or plugin data can carry and which no axis or bin can be built around, so those count as
     * not numeric.
     */
    private fun finiteOrNull(value: String): Double? = value.toDoubleOrNull()?.takeIf { it.isFinite() }

    /** Labels or values, following the trait's display setting, as the collect screen shows them. */
    private fun categoriesOf(value: String, trait: TraitObject): List<String> = try {
        CategoryJsonUtil.decode(value).mapNotNull { category ->
            (if (trait.categoryDisplayValue) category.value else category.label) ?: category.value
        }.ifEmpty { listOf(value) }
    } catch (_: Exception) {
        // Booleans and legacy plain-text categories are not JSON lists.
        listOf(value)
    }

    private fun categoryOrder(trait: TraitObject): List<String> {

        val categories = trait.categories

        return try {
            if (categories.startsWith("[")) {
                CategoryJsonUtil.decode(categories).mapNotNull { category ->
                    (if (trait.categoryDisplayValue) category.value else category.label) ?: category.value
                }
            } else {
                categories.split("/").map { it.trim() }.filter { it.isNotEmpty() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Field Book writes timestamps with [internalTimeFormatter]. BrAPI imports may carry plain
     * ISO timestamps instead, and anything else still yields a day if it starts with a date.
     */
    private fun parseTimestamp(timestamp: String?): Pair<LocalDate?, Long?> {

        if (timestamp.isNullOrBlank()) return null to null

        // The internal formatter is ThreeTenABP's, so its result is carried over by epoch day.
        val stamped = try {
            ThreeTenOffsetDateTime.parse(timestamp, internalTimeFormatter)
        } catch (_: Exception) {
            null
        }

        if (stamped != null) {
            return LocalDate.ofEpochDay(stamped.toLocalDate().toEpochDay()) to stamped.toInstant().toEpochMilli()
        }

        val dateTime = try {
            OffsetDateTime.parse(timestamp)
        } catch (_: Exception) {
            null
        }

        if (dateTime != null) return dateTime.toLocalDate() to dateTime.toInstant().toEpochMilli()

        val day = try {
            LocalDate.parse(timestamp.take(10))
        } catch (_: Exception) {
            null
        }

        return day to day?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    }
}
