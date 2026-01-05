package com.fieldbook.shared.database.repository

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.fieldbook.shared.AppContext
import com.fieldbook.shared.database.Migrator
import com.fieldbook.shared.database.Migrator.Companion.sObservationUnitPropertyViewName
import com.fieldbook.shared.objects.RangeObject
import com.fieldbook.shared.preferences.GeneralKeys
import com.fieldbook.shared.sqldelight.FieldbookDatabase
import com.fieldbook.shared.sqldelight.createDatabase
import com.russhwolf.settings.Settings

class ObservationUnitPropertyRepository() {
    private val db: FieldbookDatabase = createDatabase()
    private val driver: SqlDriver = AppContext.driverFactory().getDriver()

    /**
     * Get a RangeObject from the observation_units_property view/table by id.
     * The column names for primary, secondary, and unique id must be provided.
     * This matches the Android implementation, but for KMP/SQLDelight.
     */
    fun getRangeFromId(
        id: Long,
        firstName: String,
        secondName: String,
        uniqueName: String
    ): RangeObject {
        val sql =
            "SELECT $firstName, $secondName, $uniqueName FROM $sObservationUnitPropertyViewName WHERE id = ? LIMIT 1"

        val result: QueryResult<RangeObject> = driver.executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor ->
                val hasRow = cursor.next().value
                val range = if (hasRow) {
                    val primaryId = cursor.getString(0) ?: ""
                    val secondaryId = cursor.getString(1) ?: ""
                    val uniqueId = cursor.getString(2) ?: ""
                    RangeObject(primaryId, secondaryId, uniqueId)
                } else {
                    RangeObject("", "", "")
                }
                QueryResult.Value(range)
            },
            parameters = 1
        ) {
            bindLong(0, id)
        }

        return result.value
    }

    private fun getSortOrderClause(studyId: String): String? {
        val sortOrder = if (Settings().getString(
                "${GeneralKeys.SORT_ORDER}.$studyId",
                "ASC"
            ) == "ASC"
        ) "ASC" else "DESC"

        val sortNameResult =
            db.studiesQueries.getSortNameById(studyId.toLong()).executeAsOneOrNull()
        val sortName = sortNameResult?.study_sort_name

        var sortCols = ""
        if (!sortName.isNullOrEmpty()) {
            sortCols = sortName.split(',')
                .joinToString(",") { col: String -> "cast(`$col` as integer), `$col`" }
        }

        return if (sortCols.isNotEmpty()) "ORDER BY $sortCols COLLATE NOCASE $sortOrder" else null
    }

    fun getSortedObservationUnitData(studyId: Int): Array<Int> {
        val headers = ObservationUnitAttributeRepository().getAllNames(studyId.toLong())
            .filter { it != "geo_coordinates" }

        val selectStatement = headers.joinToString(", ") { col ->
            "MAX(CASE WHEN attr.observation_unit_attribute_name = \"$col\" THEN vals.observation_unit_value_name ELSE NULL END) AS \"$col\""
        }

        val orderByClause = getSortOrderClause(studyId.toString()) ?: ""

        val query = """
                SELECT ${if (selectStatement.isNotEmpty()) "$selectStatement, " else ""} units.internal_id_observation_unit AS id, units.geo_coordinates
                FROM ${Migrator.ObservationUnit.tableName} AS units
                LEFT JOIN ${Migrator.ObservationUnitValue.tableName} AS vals ON units.internal_id_observation_unit = vals.observation_unit_id
                LEFT JOIN ${Migrator.ObservationUnitAttribute.tableName} AS attr ON vals.observation_unit_attribute_db_id = attr.internal_id_observation_unit_attribute
                WHERE units.study_id = $studyId
                GROUP BY units.internal_id_observation_unit
                $orderByClause
            """.trimIndent()

        val result = driver.executeQuery(
            identifier = null,
            sql = query,
            mapper = { cursor ->
                val ids = mutableListOf<Int>()
                /*
                 * Compute index of the `id` column based on number of projected attribute columns
                 * if no dynamic columns, id is at 0; otherwise it's after the header columns
                 * TODO is there another way?
                 */
                val idIndex = headers.size
                while (cursor.next().value) {
                    val id = cursor.getLong(idIndex)?.toInt() ?: 0
                    ids.add(id)
                }
                QueryResult.Value(ids.toTypedArray())
            },
            parameters = 0
        ) {}

        return result.value
    }

    fun allRangeID(studyId: Int): Array<Int> {
        return getSortedObservationUnitData(studyId)
    }

}
