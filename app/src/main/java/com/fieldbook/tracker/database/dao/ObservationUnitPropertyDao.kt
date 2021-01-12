package com.fieldbook.tracker.database.dao

import android.database.Cursor
import android.database.MatrixCursor
import com.fieldbook.tracker.database.Migrator.*
import com.fieldbook.tracker.database.Migrator.Companion.sObservationUnitPropertyViewName
import com.fieldbook.tracker.database.query
import com.fieldbook.tracker.database.toFirst
import com.fieldbook.tracker.database.toTable
import com.fieldbook.tracker.database.withDatabase
import com.fieldbook.tracker.objects.RangeObject

class ObservationUnitPropertyDao {

    companion object {

//        internal fun selectAllRange(): Array<Map<String, Any?>>? = withDatabase { db ->
//            db.query(sObservationUnitPropertyViewName).toTable().toTypedArray()
//        }

        fun getAllRangeId(): Array<Integer> = withDatabase { db ->
            val table = db.query(sObservationUnitPropertyViewName,
                    select = arrayOf("id"),
                    orderBy = "id").toTable()

            table.map { (it["id"] as java.lang.Integer) }
                    .toTypedArray()
        } ?: emptyArray()

        //TODO original code uses switchField if object is null
//        fun getRangeByIdAndPlot(firstName: String,
//                                secondName: String,
//                                uniqueName: String,
//                                id: Int, pid: String): RangeObject? = withDatabase { db ->
//
//            with(db.query(sObservationUnitPropertyViewName,
//                    select = arrayOf(firstName, secondName, uniqueName, "id").map { "`$it`" }.toTypedArray(),
//                    where = "id = ? AND plot_id = ?",
//                    whereArgs = arrayOf(id.toString(), pid)
//            ).toFirst()) {
//                RangeObject().apply {
//                    range = this@with[firstName] as? String ?: ""
//                    plot = this@with[secondName] as? String ?: ""
//                    plot_id = this@with[uniqueName] as? String ?: ""
//                }
//            }
//
//        }

        fun getRangeFromId(firstName: String, secondName: String, uniqueName: String, id: Int): RangeObject = withDatabase { db ->
//            data.range = cursor.getString(0);
//                data.plot = cursor.getString(1);
//                data.plot_id = cursor.getString(2);
            RangeObject().apply {

                val model = db.query(sObservationUnitPropertyViewName,
                    select = arrayOf(firstName, secondName, uniqueName, "id"),
                    where = "id = ?",
                    whereArgs = arrayOf(id.toString())).toFirst()

                range = model[firstName].toString()

                plot = model[secondName].toString()

                plot_id = model[uniqueName].toString()
            }

        } ?: RangeObject().apply {
            range = ""
            plot = ""
            plot_id = ""
        }

        fun getDropDownRange(uniqueName: String, trait: String, plotId: String): Array<String>? = withDatabase { db ->

            db.query(sObservationUnitPropertyViewName,
                    select = arrayOf("`$trait`"),
                    where = "`$uniqueName` LIKE ?",
                    whereArgs = arrayOf(plotId)).toTable().map {
                it[trait].toString()
            }.toTypedArray()

        }

        fun getRangeColumnNames(): Array<String?> = withDatabase { db ->

            val cursor = db.rawQuery("SELECT * FROM $sObservationUnitPropertyViewName limit 1", null)
            //Cursor cursor = db.rawQuery("SELECT * from range limit 1", null);
            //Cursor cursor = db.rawQuery("SELECT * from range limit 1", null);
            var data: Array<String?>? = null

            if (cursor.moveToFirst()) {
                val i = cursor.columnCount - 1
                data = arrayOfNulls(i)
                var k = 0
                for (j in 0 until cursor.columnCount) {
                    if (cursor.getColumnName(j) != "id") {
                        data[k] = cursor.getColumnName(j).replace("//", "/")
                        k += 1
                    }
                }
            }

            if (!cursor.isClosed) {
                cursor.close()
            }

            data

        } ?: emptyArray()

        fun getRangeColumns(): Array<String?> = withDatabase { db ->

            val cursor = db.rawQuery("SELECT * from $sObservationUnitPropertyViewName limit 1", null)

            var data: Array<String?>? = null

            if (cursor.moveToFirst()) {
                val i = cursor.columnCount - 1
                data = arrayOfNulls(i)
                var k = 0
                for (j in 0 until cursor.columnCount) {
                    if (cursor.getColumnName(j) != "id") {
                        data[k] = cursor.getColumnName(j)
                        k += 1
                    }
                }
            }

            if (!cursor.isClosed) {
                cursor.close()
            }

            data
//    db.query(sObservationUnitPropertyViewName).toFirst().keys
//            .filter { it != "id" }.toTypedArray()

        } ?: emptyArray<String?>()


        fun getExportDbData(uniqueName: String, fieldList: Array<String?>, traits: Array<String>): Cursor? = withDatabase { db ->

            val traitRequiredFields = arrayOf("trait", "userValue", "timeTaken", "person", "location", "rep")
            val requiredFields = fieldList + traitRequiredFields
            MatrixCursor(requiredFields).also { cursor ->
//        "select " + fields + ", traits.trait, user_traits.userValue, " +
//                "user_traits.timeTaken, user_traits.person, user_traits.location, user_traits.rep" +
//                " from user_traits, range, traits where " +
//                "user_traits.rid = range." + TICK + ep.getString("ImportUniqueName", "") + TICK +
//                " and user_traits.parent = traits.trait and " +
//                "user_traits.trait = traits.format and user_traits.userValue is not null and " + activeTraits;

                val varSelectFields = arrayOf("observation_variable_name")
                val obsSelectFields = arrayOf("value", "observation_time_stamp", "collector", "geoCoordinates")
                val outputFields = fieldList + varSelectFields + obsSelectFields
                val query = """
        SELECT ${fieldList.joinToString { "props.`$it` AS `$it`" }} ${if (fieldList.isNotEmpty()) "," else " "} 
            ${varSelectFields.joinToString { "vars.`$it` AS `$it`" }} ${if (varSelectFields.isNotEmpty()) "," else " "}
            ${obsSelectFields.joinToString { "obs.`$it` AS `$it`" }}
        FROM ${Observation.tableName} AS obs, 
             $sObservationUnitPropertyViewName AS props, 
             ${ObservationVariable.tableName} AS vars
        WHERE obs.${ObservationUnit.FK} = props.`$uniqueName`
            AND obs.observation_variable_name = vars.observation_variable_name
            AND obs.observation_variable_field_book_format = vars.observation_variable_field_book_format
            AND obs.value IS NOT NULL 
            AND vars.observation_variable_name IN ${traits.map { "\"${it.replace("'", "''")}\"" }.joinToString(",", "(", ")")}
        
    """.trimIndent()

//        println(query)
                val traitRequiredFields = arrayOf("trait", "userValue", "timeTaken", "person", "location", "rep")

                val table = db.rawQuery(query, null).toTable()

                table.forEach { row ->
                    cursor.addRow(fieldList.map { row[it] } + traitRequiredFields.map {
                        when (it) {
                            "trait" -> row["observation_variable_name"]
                            "userValue" -> row["value"]
                            "timeTaken" -> row["observation_time_stamp"]
                            "person" -> row["collector"]
                            "location" -> row["geoCoordinates"]
                            else -> 2 //TODO: Trevor what do do with rep
                        }
                    })
                }
            }
        }

        fun convertDatabaseToTable(uniqueName: String, col: Array<String?>, traits: Array<String>): Cursor? = withDatabase { db ->

            val query: String
            val rangeArgs = arrayOfNulls<String>(col.size)
            val traitArgs = arrayOfNulls<String>(traits.size)
            var joinArgs = ""

            for (i in col.indices) {
                rangeArgs[i] = "prop.`${col[i]}`"
            }

            for (i in traits.indices) {
                traitArgs[i] = "m" + i + ".value AS '" + traits[i] + "'"
                joinArgs = (joinArgs + "LEFT JOIN observations m" + i + " ON prop.`$uniqueName`"
                        + " = m" + i + ".observation_unit_id AND m" + i + ".observation_variable_name = '" + traits[i] + "' ")
            }

            query = "SELECT " + rangeArgs.joinToString(",") + " , " + traitArgs.joinToString(",") +
                    " FROM $sObservationUnitPropertyViewName AS prop " + joinArgs + " GROUP BY prop.`$uniqueName` ORDER BY prop.id"

            println(query)

            db.rawQuery(query, null)
        }
    }
}
