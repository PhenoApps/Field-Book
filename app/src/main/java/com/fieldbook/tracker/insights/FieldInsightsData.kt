package com.fieldbook.tracker.insights

import java.time.LocalDate

/**
 * How a trait's values can be plotted. Decided once per trait when the field is loaded, so every
 * view agrees on it.
 */
enum class InsightTraitKind {
    /** Plain numbers: numeric, percent, counter, angle, and any other format whose values all parse. */
    NUMERIC,

    /** Dates, plotted by day of year. */
    DATE,

    /** Values drawn from a set of labels: categorical, boolean, and non-numeric ratings. */
    CATEGORICAL,

    /** Media, text, locations and the like. Only coverage can be shown for these. */
    OTHER;

    val isQuantitative get() = this == NUMERIC || this == DATE
}

data class InsightTrait(
    val id: String,
    val name: String,
    val format: String,
    val kind: InsightTraitKind,
    /** The trait's defined categories in their configured order, for categorical traits. */
    val categoryOrder: List<String> = emptyList()
)

data class InsightObservation(
    val unitId: String,
    val traitId: String,
    /** The value as a number, or the day of year for date traits. Null when it does not parse. */
    val numeric: Double?,
    /** Category labels for categorical traits; more than one when multicat is enabled. */
    val categories: List<String>,
    /** The day the value was recorded, from the observation timestamp. */
    val collectedOn: LocalDate?,
    /** When the value was recorded, in epoch milliseconds. */
    val collectedAt: Long?,
    val collector: String,
    val rep: Int
)

/**
 * Everything the insights screen shows for one field, loaded in a single pass.
 *
 * Plot level values are precomputed because the map, the comparisons and the correlations all
 * work from them: repeated measures are averaged for numbers, and the latest repeat wins for
 * categories. Only entries in the field are included.
 *
 * Observations recorded as NA are kept apart in [naObservations]. They have no value to chart,
 * but the entry was assessed, so they count toward completeness through [recorded].
 */
data class FieldInsightsData(
    val fieldName: String,
    val unitIds: List<String>,
    /** Attribute name to (unit id to value), for every attribute that has values. */
    val attributes: Map<String, Map<String, String>>,
    val traits: List<InsightTrait>,
    /** Observations with a value. */
    val observations: List<InsightObservation>,
    val observationsByTrait: Map<String, List<InsightObservation>>,
    /** Observations recorded as NA. */
    val naObservations: List<InsightObservation>,
    /** Trait id to (unit id to mean value across repeats). */
    val plotNumeric: Map<String, Map<String, Double>>,
    /** Trait id to (unit id to the latest repeat's categories; more than one for multicat). */
    val plotCategories: Map<String, Map<String, List<String>>>,
    val defaultRowAttribute: String?,
    val defaultColumnAttribute: String?
) {
    val attributeNames: List<String> get() = attributes.keys.toList()

    fun trait(id: String?): InsightTrait? = traits.firstOrNull { it.id == id }

    /**
     * Everything recorded for one trait, or for all of them when [traitId] is null, NA included.
     * For completeness: an entry assessed as NA is done, even though it has no value.
     */
    fun recorded(traitId: String?): List<InsightObservation> =
        if (traitId == null) observations + naObservations
        else observationsByTrait[traitId].orEmpty() + naObservations.filter { it.traitId == traitId }

    /** Observations recorded as NA for one trait, or for all of them when [traitId] is null. */
    fun naFor(traitId: String?): List<InsightObservation> =
        if (traitId == null) naObservations else naObservations.filter { it.traitId == traitId }
}
