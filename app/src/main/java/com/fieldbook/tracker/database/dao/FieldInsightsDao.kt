package com.fieldbook.tracker.database.dao

import androidx.core.database.getStringOrNull
import com.fieldbook.tracker.database.Migrator.Observation
import com.fieldbook.tracker.database.Migrator.ObservationUnit
import com.fieldbook.tracker.database.Migrator.ObservationUnitAttribute
import com.fieldbook.tracker.database.Migrator.ObservationUnitValue
import com.fieldbook.tracker.database.Migrator.ObservationVariable
import com.fieldbook.tracker.database.withDatabase

/**
 * Reads for the field insights screen.
 *
 * These are deliberately narrow: the screen needs every observation in a field at once, so it
 * selects only the handful of columns it plots rather than building full models per row.
 */
class FieldInsightsDao {

    data class ObservationRow(
        val unitId: String,
        val traitId: String,
        val value: String,
        val timestamp: String?,
        val collector: String?,
        val rep: String?
    )

    data class UnitRow(
        val unitId: String
    )

    data class AttributeRow(
        val unitId: String,
        val name: String,
        val value: String
    )

    companion object {

        /**
         * Observations and traits the insights screen will show, counted the way it counts them:
         * only rows with a value (not blank, not NA), for entries still in the field and traits
         * that still exist. For the field detail page, which needs the totals without loading
         * every value.
         */
        fun getCounts(studyId: Int): Pair<Int, Int> = withDatabase { db ->

            db.rawQuery(
                """
                SELECT COUNT(*), COUNT(DISTINCT o.observation_variable_db_id)
                FROM ${Observation.tableName} AS o
                JOIN ${ObservationUnit.tableName} AS u
                    ON u.observation_unit_db_id = o.observation_unit_id AND u.study_id = o.study_id
                JOIN ${ObservationVariable.tableName} AS v
                    ON v.${ObservationVariable.PK} = o.observation_variable_db_id
                WHERE o.study_id = ? AND o.observation_variable_db_id > 0
                    AND o.value IS NOT NULL AND TRIM(o.value) != '' AND o.value != 'NA'
                """.trimIndent(),
                arrayOf(studyId.toString())
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) to cursor.getInt(1) else 0 to 0
            }

        } ?: (0 to 0)

        fun getObservations(studyId: Int): List<ObservationRow> = withDatabase { db ->

            db.rawQuery(
                """
                SELECT observation_unit_id, observation_variable_db_id, value,
                    observation_time_stamp, collector, rep
                FROM ${Observation.tableName}
                WHERE study_id = ? AND observation_variable_db_id > 0
                """.trimIndent(),
                arrayOf(studyId.toString())
            ).use { cursor ->

                val rows = ArrayList<ObservationRow>(cursor.count)
                while (cursor.moveToNext()) {
                    rows.add(
                        ObservationRow(
                            unitId = cursor.getStringOrNull(0) ?: continue,
                            traitId = cursor.getStringOrNull(1) ?: continue,
                            value = cursor.getStringOrNull(2) ?: "",
                            timestamp = cursor.getStringOrNull(3),
                            collector = cursor.getStringOrNull(4),
                            rep = cursor.getStringOrNull(5)
                        )
                    )
                }
                rows
            }

        } ?: emptyList()

        fun getUnits(studyId: Int): List<UnitRow> = withDatabase { db ->

            db.rawQuery(
                """
                SELECT observation_unit_db_id
                FROM ${ObservationUnit.tableName}
                WHERE study_id = ?
                """.trimIndent(),
                arrayOf(studyId.toString())
            ).use { cursor ->

                val rows = ArrayList<UnitRow>(cursor.count)
                while (cursor.moveToNext()) {
                    rows.add(
                        UnitRow(
                            unitId = cursor.getStringOrNull(0) ?: continue
                        )
                    )
                }
                rows
            }

        } ?: emptyList()

        /**
         * Every imported attribute value in the field, one row per unit and attribute. Values
         * are keyed by the unit's db id so they line up with [ObservationRow.unitId].
         */
        fun getAttributes(studyId: Int): List<AttributeRow> = withDatabase { db ->

            db.rawQuery(
                """
                SELECT units.observation_unit_db_id,
                    attr.${ObservationUnitAttribute.OBSERVATION_UNIT_ATTRIBUTE_NAME},
                    vals.${ObservationUnitValue.OBSERVATION_UNIT_VALUE_NAME}
                FROM ${ObservationUnitValue.tableName} AS vals
                JOIN ${ObservationUnit.tableName} AS units
                    ON units.${ObservationUnit.PK} = vals.${ObservationUnitValue.OBSERVATION_UNIT_ID}
                JOIN ${ObservationUnitAttribute.tableName} AS attr
                    ON attr.${ObservationUnitAttribute.PK} = vals.${ObservationUnitValue.OBSERVATION_UNIT_ATTRIBUTE_DB_ID}
                WHERE vals.${ObservationUnitValue.STUDY_ID} = ?
                """.trimIndent(),
                arrayOf(studyId.toString())
            ).use { cursor ->

                val rows = ArrayList<AttributeRow>(cursor.count)
                while (cursor.moveToNext()) {
                    val value = cursor.getStringOrNull(2)
                    if (value.isNullOrBlank()) continue
                    rows.add(
                        AttributeRow(
                            unitId = cursor.getStringOrNull(0) ?: continue,
                            name = cursor.getStringOrNull(1) ?: continue,
                            value = value
                        )
                    )
                }
                rows
            }

        } ?: emptyList()
    }
}
