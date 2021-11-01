package com.fieldbook.tracker.database.dao

import android.content.ContentValues
import com.fieldbook.tracker.database.*
import com.fieldbook.tracker.database.models.ObservationUnitModel
import java.util.HashMap
import com.fieldbook.tracker.database.Migrator.Study
import com.fieldbook.tracker.database.Migrator.ObservationUnit

class ObservationUnitDao {

    companion object {

        fun checkUnique(values: HashMap<String, String>): Boolean? = withDatabase { db ->

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
        fun updateObservationUnit(unit: ObservationUnitModel, geoCoordinates: String) = withDatabase { db ->

            db.update(ObservationUnit.tableName,
                    ContentValues().apply {
                        put("geo_coordinates", geoCoordinates)
                    },
                    "${ObservationUnit.PK} = ?", arrayOf(unit.internal_id_observation_unit.toString()))

        }
    }
}