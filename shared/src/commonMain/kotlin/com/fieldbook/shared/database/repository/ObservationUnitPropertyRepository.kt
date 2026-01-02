package com.fieldbook.shared.database.repository

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.fieldbook.shared.AppContext
import com.fieldbook.shared.objects.RangeObject
import com.fieldbook.shared.sqldelight.FieldbookDatabase
import com.fieldbook.shared.sqldelight.createDatabase

class ObservationUnitPropertyRepository() {
    private val db: FieldbookDatabase = createDatabase()
    private val driver: SqlDriver = AppContext.driverFactory().getDriver()
    val sObservationUnitPropertyViewName = "ObservationUnitProperty"

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
}
