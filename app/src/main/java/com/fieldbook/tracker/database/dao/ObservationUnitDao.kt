package com.fieldbook.tracker.database.dao

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.fieldbook.tracker.database.Migrator.ObservationUnit
import com.fieldbook.tracker.database.Migrator.Study
import com.fieldbook.tracker.database.models.ObservationUnitModel
import com.fieldbook.tracker.database.query
import com.fieldbook.tracker.database.toFirst
import com.fieldbook.tracker.database.toTable
import com.fieldbook.tracker.database.withDatabase

class ObservationUnitDao {

    companion object {

        fun checkUnique(values: HashMap<String, String>): Boolean = withDatabase { db ->

            var result = true

            db.query(ObservationUnit.tableName,
                    select = arrayOf("observation_unit_db_id")).toTable().forEach {
                if (it["observation_unit_db_id"] in values.keys) {
                    result = false
                }
            }

            result

        } ?: false

        fun getAll(): Array<ObservationUnitModel> = withDatabase { db ->

            arrayOf(*db.query(ObservationUnit.tableName)
                .toTable()
                .map { ObservationUnitModel(it) }
                .toTypedArray())

        } ?: emptyArray()

        fun getAll(db: SQLiteDatabase): Array<ObservationUnitModel> =
            arrayOf(*db.query(ObservationUnit.tableName)
                .toTable()
                .map { ObservationUnitModel(it) }
                .toTypedArray())

        fun getById(id: String): ObservationUnitModel? = withDatabase { db ->

            val map = db.query(
                ObservationUnit.tableName,
                where = "observation_unit_db_id = ?",
                whereArgs = arrayOf(id)
            ).toFirst()

            if ("observation_unit_db_id" in map.keys) {
                ObservationUnitModel(map)
            } else {
                null
            }
        }

        /**
        * Find observation units matching a search attribute value
        * @param studyId The study ID to search within
        * @param searchValue The value to search for
        * @return Array of matching observation unit models, or empty array if none found
        */
        fun getBySearchAttribute(studyId: Int, searchValue: String): Array<ObservationUnitModel> = withDatabase { db ->

            Log.d("Field Book", "getBySearchAttribute called with studyId: $studyId, searchValue: $searchValue")

            val query = """
                SELECT ou.*
                FROM ${ObservationUnit.tableName} ou
                JOIN observation_units_values ouv ON 
                    ou.${ObservationUnit.PK} = ouv.observation_unit_id
                    AND ouv.study_id = ?
                    AND ouv.observation_unit_value_name = ?
                    AND ouv.observation_unit_attribute_db_id IN (
                        SELECT internal_id_observation_unit_attribute
                        FROM observation_units_attributes
                        WHERE observation_unit_attribute_name = (
                            SELECT observation_unit_search_attribute
                            FROM ${Study.tableName}
                            WHERE ${Study.PK} = ?
                        )
                    )
            """

            db.rawQuery(query, arrayOf(
                studyId.toString(),
                searchValue,
                studyId.toString()
            ))
            .toTable()
            .map { ObservationUnitModel(it) }
            .toTypedArray()
        } ?: emptyArray()

        fun getAll(eid: Int): Array<ObservationUnitModel> = withDatabase { db ->

            arrayOf(*db.query(ObservationUnit.tableName,
                    where = "${Study.FK} = ?",
                    whereArgs = arrayOf(eid.toString())).toTable()
                    .map { ObservationUnitModel(it) }
                    .toTypedArray())

        } ?: emptyArray()

        /**
         * Updates a given observation unit row with a geo coordinates string.
         */
        fun updateObservationUnit(unit: ObservationUnitModel, geoCoordinates: String) =
            withDatabase { db ->

                db.update(
                    ObservationUnit.tableName,
                    ContentValues().apply {
                        put("geo_coordinates", geoCoordinates)
                    },
                    "${ObservationUnit.PK} = ?",
                    arrayOf(unit.internal_id_observation_unit.toString())
                )

            }

        fun updateObservationUnitModel(
            db: SQLiteDatabase,
            unit: ObservationUnitModel,
            geoCoordinates: String
        ) {

            db.update(
                ObservationUnit.tableName,
                ContentValues().apply {
                    put("geo_coordinates", geoCoordinates)
                },
                "${ObservationUnit.PK} = ?", arrayOf(unit.internal_id_observation_unit.toString())
            )

        }

        fun updateObservationUnitModels(db: SQLiteDatabase, models: List<ObservationUnitModel>) {

            models.forEach { unit ->

                updateObservationUnitModel(db, unit, unit.geo_coordinates ?: "")
            }
        }
    }
}