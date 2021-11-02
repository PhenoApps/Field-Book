package com.fieldbook.tracker.database.dao

import com.fieldbook.tracker.database.*
import com.fieldbook.tracker.database.Migrator.Study
import com.fieldbook.tracker.database.Migrator.ObservationUnitAttribute

class ObservationUnitAttributeDao {

    companion object {

        fun getAllNames(eid: Int): Array<String> = withDatabase { db ->
            db.query(ObservationUnitAttribute.tableName,
                    where = "${Study.FK} = ?",
                    whereArgs = arrayOf(eid.toString())).toTable()
                .map { it["observation_unit_attribute_name"] as? String ?: "" }
                .filter { it.isNotBlank() && it.isNotEmpty() }
                .toTypedArray()
        } ?: emptyArray()

        fun getIdByName(name: String): Int = withDatabase { db ->
            db.query(ObservationUnitAttribute.tableName,
                where = "observation_unit_attribute_name = ?",
                whereArgs = arrayOf(name)).toFirst()[ObservationUnitAttribute.PK] as Int
        } ?: -1
    }
}