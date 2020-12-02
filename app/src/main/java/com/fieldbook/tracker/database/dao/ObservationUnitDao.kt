package com.fieldbook.tracker.database.dao

import com.fieldbook.tracker.database.models.ObservationUnitModel
import com.fieldbook.tracker.database.query
import com.fieldbook.tracker.database.toTable
import com.fieldbook.tracker.database.withDatabase
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

        fun getAll(eid: Int): Array<ObservationUnitModel> = withDatabase { db ->

            arrayOf(*db.query(ObservationUnit.tableName,
                    where = "${Study.FK} = ?",
                    whereArgs = arrayOf(eid.toString())).toTable()
                    .map { ObservationUnitModel(it) }
                    .toTypedArray())

        } ?: emptyArray()

        fun getAll(): Array<ObservationUnitModel> = withDatabase { db ->

            arrayOf(*db.query(ObservationUnit.tableName).toTable()
                    .map { ObservationUnitModel(it) }
                    .toTypedArray())

        } ?: emptyArray()
    }
}