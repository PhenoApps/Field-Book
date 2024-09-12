package com.fieldbook.tracker.database.dao

import android.annotation.SuppressLint
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.util.Log
import com.fieldbook.tracker.database.Migrator.ObservationVariable
import com.fieldbook.tracker.database.models.ObservationVariableModel
import com.fieldbook.tracker.database.query
import com.fieldbook.tracker.database.queryForMax
import com.fieldbook.tracker.database.toFirst
import com.fieldbook.tracker.database.toTable
import com.fieldbook.tracker.database.withDatabase
import com.fieldbook.tracker.objects.TraitObject

class ObservationVariableDao {

    companion object {

        fun getMaxPosition(): Int = withDatabase { db ->

            try {

                db.queryForMax(
                    ObservationVariable.tableName,
                    select = arrayOf("MAX(position) as result")
                ).toFirst()["result"].toString().toInt()

            } catch (nfe: NumberFormatException) {

                //return 0 if the position column is empty or cannot be parsed into an integer
                0

            }
        } ?: 0

        fun getTraitById(id: Int): TraitObject? = withDatabase { db ->

            db.query(ObservationVariable.tableName,
//                    select = arrayOf("observation_variable_name"),
                where = "internal_id_observation_variable = ?",
                whereArgs = arrayOf("$id")).toFirst().toTraitObject()

        }

        fun getTraitByName(name: String): TraitObject? = withDatabase { db ->

            db.query(ObservationVariable.tableName,
//                    select = arrayOf("observation_variable_name"),
                    where = "observation_variable_name = ? COLLATE NOCASE",
                    whereArgs = arrayOf(name)).toFirst().toTraitObject()

        }

        fun getTraitByExternalDbId(externalDbId: String, traitDataSource: String): TraitObject? = withDatabase { db ->

            db.query(ObservationVariable.tableName,
                    where = "external_db_id = ? AND trait_data_source = ? ",
                    whereArgs = arrayOf(externalDbId, traitDataSource)).toFirst().toTraitObject()

        }

        private fun Map<String, Any?>.toTraitObject() = if (this.isEmpty()) {null} else { TraitObject().also {

            it.id = this[ObservationVariable.PK].toString()
            it.name = this["observation_variable_name"] as? String ?: ""
            it.format = this["observation_variable_field_book_format"] as? String ?: ""
            it.defaultValue = this["default_value"].toString()
            it.details = this["observation_variable_details"].toString()

            it.realPosition = try {

                 this["position"].toString().toInt()

            } catch (nfe: java.lang.NumberFormatException) {

                //return 0 if the position column is empty or cannot be parsed into an integer
                0
            }

            it.visible = this["visible"].toString() == "true"
            it.externalDbId = this["external_db_id"].toString()
            it.traitDataSource = this["trait_data_source"].toString()

        }
        }

        /**
         * TODO: Replace with View.
         */
        @SuppressLint("Recycle")
        fun getTraitExists(uniqueName: String, id: Int, traitDbId: String): Boolean =
            withDatabase { db ->

                val query = """
                SELECT id, value
                FROM observations, ObservationUnitProperty
                WHERE observations.observation_unit_id = ObservationUnitProperty.'$uniqueName' 
                    AND ObservationUnitProperty.id = ? 
                    AND observations.observation_variable_db_id = ? 
                """.trimIndent()

//            println("$id $parent $trait")
//            println(query)

                val columnNames =
                    db.rawQuery(query, arrayOf(id.toString(), traitDbId)).toFirst().keys

            "value" in columnNames

        } ?: false

        fun getAllTraits(): Array<String> = withDatabase { db ->

            db.query(ObservationVariable.tableName,
                    arrayOf(ObservationVariable.PK, "observation_variable_name", "position"),
                    orderBy = "position").use {

                it.toTable().map { row ->
                    row["observation_variable_name"] as String
                }.toTypedArray()
            }

        } ?: arrayOf()

        fun getTraitColumnData(column: String): Array<String> = withDatabase { db ->

            val queryColumn = when(column) {
                "isVisible" -> "visible"
                "format" -> "observation_variable_field_book_format"
                else -> "observation_variable_name"
            }

            db.query(ObservationVariable.tableName,
                    arrayOf(queryColumn)).use {

                it.toTable().mapNotNull { row ->
                    row[queryColumn].toString()
                }.toTypedArray()
            }

        } ?: arrayOf()

        fun getTraitColumns(): Array<String> = withDatabase { db ->

            db.query(ObservationVariable.tableName).use {

                (it.toTable().first().keys - setOf("id", "external_db_id", "trait_data_source")).toTypedArray()
            }

        } ?: arrayOf()

        /**
         * Alternative version of getTraitColumns that also returns
         * variable attribute names.
         */
        fun getTraitPropertyColumns(): Array<String> = withDatabase { db ->

            val newTraits = db.query(ObservationVariable.tableName).use {

                val names = ObservationVariableAttributeDao.getAllNames()!!.distinct()

                val columns = names + it.toFirst().keys

                if (columns.isEmpty()) columns.toTypedArray()
                else (columns - setOf("id", "external_db_id", "trait_data_source")).toTypedArray()
            }

            val renaming = mapOf(
                    "validValuesMax" to "maximum",
                    "validValuesMin" to "minimum",
                    "observation_variable_name" to "trait",
                    "default_value" to "defaultValue",
                    "category" to "categories",
                    "observation_variable_details" to "details",
                    "visible" to "isVisible",
                    "position" to "realPosition",
                    "observation_variable_field_book_format" to "format"
            )

            val renamedTraits = newTraits.map { renaming[it] }.mapNotNull { it }.toTypedArray()

            renamedTraits

        } ?: arrayOf()

        fun getAllTraitsForExport(): Cursor {

            val requiredFields = arrayOf("trait", "format", "defaultValue", "minimum",
                "maximum", "details", "categories", "isVisible", "realPosition")
            //val requiredFields = getTraitPropertyColumns()
            //trait,format,defaultValue,minimum,maximum,details,categories,isVisible,realPosition
            return MatrixCursor(requiredFields).also { cursor ->
                val traits = getAllTraitObjects()
                traits.sortBy { it.id.toInt() }
                traits.forEach { trait ->
                    cursor.addRow(requiredFields.map {
                        when (it) {
                            "trait" -> trait.name
                            "format" -> trait.format
                            "defaultValue" -> trait.defaultValue
                            "minimum" -> trait.minimum
                            "maximum" -> trait.maximum
                            "details" -> trait.details
                            "categories" -> trait.categories
                            "isVisible" -> trait.visible
                            "realPosition" -> trait.realPosition
                            else -> null!!
                        }
                    })
                }
            }
        }

        fun getAllTraitObjectsForExport(): Cursor {
            val requiredFields = arrayOf(
                "trait", "format", "defaultValue", "minimum", "maximum",
                "details", "categories", "isVisible", "realPosition"
            )

            return MatrixCursor(requiredFields).apply {
                getAllTraitObjects().forEach { trait ->
                    addRow(requiredFields.map { field ->
                        when (field) {
                            "trait" -> trait.name
                            "format" -> trait.format
                            "defaultValue" -> trait.defaultValue
                            "minimum" -> trait.minimum
                            "maximum" -> trait.maximum
                            "details" -> trait.details
                            "categories" -> trait.categories
                            "isVisible" -> trait.visible.toString()
                            "realPosition" -> trait.realPosition.toString()
                            else -> throw IllegalArgumentException("Unexpected field: $field")
                        }
                    })
                }
            }
        }

        fun getById(id: String): ObservationVariableModel? = withDatabase { db ->

            ObservationVariableModel(
                db.query(
                    ObservationVariable.tableName,
                    where = "internal_id_observation_variable = ?",
                    whereArgs = arrayOf(id)
                ).toFirst()
            )
        }

        fun getAllTraitObjects(
            studyId: Int? = null,
            sortOrder: String? = null
        ): ArrayList<TraitObject> = withDatabase { db ->
            val traits = ArrayList<TraitObject>()

            // If sortOrder is null or empty, use the default "real_position" from the link table
            val actualSortOrder = sortOrder ?: "real_position"

            // Sort ascending, except for visibility which is descending
            val orderDirection = if (sortOrder == "study_visibility") "DESC" else "ASC"

            // Main query with conditional sorting
            val query = """
                SELECT ov.*, 
                    COALESCE(sovl.position, ov.position) AS real_position, 
                    sovl.visibility AS study_visibility
                FROM ${ObservationVariable.tableName} ov
                LEFT JOIN studies_observation_variables_link sovl
                ON ov.internal_id_observation_variable = sovl.observation_variable_id 
                AND sovl.study_id = ?
                ORDER BY $actualSortOrder COLLATE NOCASE $orderDirection
            """

            // Log the query for debugging
            Log.d("ObservationVariableDao", "Executing query: $query with fieldId: $studyId")

            db.rawQuery(query, arrayOf(studyId.toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    val trait = TraitObject().apply {
                        name = cursor.getString(cursor.getColumnIndexOrThrow("observation_variable_name")) ?: ""
                        format = cursor.getString(cursor.getColumnIndexOrThrow("observation_variable_field_book_format")) ?: ""
                        defaultValue = cursor.getString(cursor.getColumnIndexOrThrow("default_value")) ?: ""
                        details = cursor.getString(cursor.getColumnIndexOrThrow("observation_variable_details")) ?: ""
                        id = cursor.getInt(cursor.getColumnIndexOrThrow(ObservationVariable.PK)).toString()
                        externalDbId = cursor.getString(cursor.getColumnIndexOrThrow("external_db_id")) ?: ""

                        // Use position from the link table if available
                        realPosition = cursor.getInt(cursor.getColumnIndexOrThrow("real_position"))

                        // Use visibility from the link table if available, otherwise fall back to the default
                        visible = cursor.getInt(cursor.getColumnIndexOrThrow("study_visibility")).takeIf {
                            !cursor.isNull(cursor.getColumnIndexOrThrow("study_visibility"))
                        } == 1 ?: (cursor.getInt(cursor.getColumnIndexOrThrow("visible")) == 1)

                        additionalInfo = cursor.getString(cursor.getColumnIndexOrThrow("additional_info")) ?: ""

                        // Initialize optional fields
                        maximum = ""
                        minimum = ""
                        categories = ""
                    }

                    // Log each trait added
                    Log.d("ObservationVariableDao", "Adding trait: ${trait.name}, ID: ${trait.id}, Real Position: ${trait.realPosition}, Visible: ${trait.visible}")

                    traits.add(trait)
                }
            }

            // Log total traits retrieved
            Log.d("ObservationVariableDao", "Total traits retrieved: ${traits.size}")

            traits
        } ?: ArrayList()


        // Overload for Java compatibility
        fun getAllTraitObjects(): ArrayList<TraitObject> = getAllTraitObjects(0)

        fun getTraitVisibility(): HashMap<String, String> = withDatabase { db ->

            hashMapOf(*db.query(ObservationVariable.tableName,
                    select = arrayOf("observation_variable_name", "visible"))
                    .toTable().map {
                        it["observation_variable_name"].toString() to it["visible"].toString()
                    }.toTypedArray())

        } ?: hashMapOf()

        //TODO missing obs. vars. for min/max/categories
        fun insertTraits(t: TraitObject) = withDatabase { db ->

            if (getTraitByName(t.name) != null) -1
            else {

                val varRowId = db.insert(
                    ObservationVariable.tableName, null,
                    ContentValues().apply {
//                            put(PK, t.id)
                        put("external_db_id", t.externalDbId)
                        put("trait_data_source", t.traitDataSource)
                        put("observation_variable_name", t.name)
                        put("observation_variable_details", t.details)
                        put("observation_variable_field_book_format", t.format)
                        put("default_value", t.defaultValue)
                        put("visible", t.visible.toString())
                        put("position", t.realPosition)
                        put("additional_info", t.additionalInfo)
                        })

                ObservationVariableValueDao.insert(
                        t.minimum.orEmpty(),
                        t.maximum.orEmpty(),
                        t.categories.orEmpty(),
                        varRowId.toString())

                varRowId

            }

        } ?: -1

        fun deleteTrait(id: String) = withDatabase { db ->
            db.delete(ObservationVariable.tableName,
                    "${ObservationVariable.PK} = ?", arrayOf(id))
        }

        fun deleteTraits() = withDatabase { db ->
            db.delete(ObservationVariable.tableName, null, null)
        }

//        fun updateTraitPosition(id: String, realPosition: Int) = withDatabase { db ->
//
//            db.update(ObservationVariable.tableName, ContentValues().apply {
//                put("position", realPosition)
//            }, "${ObservationVariable.PK} = ?", arrayOf(id))
//        }

        fun updateTraitPosition(traitDbId: String, realPosition: Int, fieldId: Int) = withDatabase { db ->
            // Check if the link exists in the studies_observation_variables_link table
            val exists = db.rawQuery(
                "SELECT COUNT(*) FROM studies_observation_variables_link WHERE observation_variable_id = ? AND study_id = ?",
                arrayOf(traitDbId, fieldId.toString())
            ).use { cursor ->
                cursor.moveToFirst() && cursor.getInt(0) > 0
            }

            if (exists) {
                // Update the position in the link table if it exists
                db.execSQL(
                    "UPDATE studies_observation_variables_link SET position = ? WHERE observation_variable_id = ? AND study_id = ?",
                    arrayOf(realPosition.toString(), traitDbId, fieldId.toString())
                )
                Log.d("ObservationVariableDao", "Updated position: traitDbId=$traitDbId, fieldId=$fieldId, position=$realPosition")
            } else {
                // Insert a new link if it doesn't exist
                db.execSQL(
                    "INSERT INTO studies_observation_variables_link (observation_variable_id, study_id, visibility, position) VALUES (?, ?, ?, ?)",
                    arrayOf(traitDbId, fieldId.toString(), 0, realPosition.toString()) // Default visibility to 1 (true)
                )
                Log.d("ObservationVariableDao", "Inserted new link with position: traitDbId=$traitDbId, fieldId=$fieldId, position=$realPosition")
            }
        }

        //TODO need to edit min/max/category obs. var. val/attrs
        fun editTraits(id: String, trait: String, format: String, defaultValue: String,
                       minimum: String, maximum: String, details: String, categories: String): Long = withDatabase { db ->

            val rowid = db.update(ObservationVariable.tableName, ContentValues().apply {
                put("observation_variable_name", trait)
                put("observation_variable_field_book_format", format)
                put("default_value", defaultValue)
                put("observation_variable_details", details)
            }, "${ObservationVariable.PK} = ?", arrayOf(id)).toLong()

            arrayOf("validValuesMin", "validValuesMax", "category").forEach {

                val attrId = ObservationVariableAttributeDao.getAttributeIdByName(it)

                when(it) {
                    "validValuesMin" -> {
                        ObservationVariableValueDao.update(id, attrId.toString(), minimum)
                    }
                    "validValuesMax" -> {
                        ObservationVariableValueDao.update(id, attrId.toString(), maximum)
                    }
                    "category" -> {
                        ObservationVariableValueDao.update(id, attrId.toString(), categories)
                    }
                }
            }

            rowid

        } ?: -1L

//        fun updateTraitVisibility(traitDbId: String, visible: Boolean, fieldId: Int) = withDatabase { db ->
//            val currentStudyIds = db.rawQuery(
//                "SELECT study_ids FROM ${ObservationVariable.tableName} WHERE internal_id_observation_variable = ?",
//                arrayOf(traitDbId)
//            ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else "[]" }
//
//            val jsonArray = org.json.JSONArray(currentStudyIds).apply {
//                if (visible && !jsonArrayContains(this, fieldId)) put(fieldId)
//                if (!visible) removeFieldIdFromJsonArray(this, fieldId)
//            }
//
//            db.execSQL(
//                "UPDATE ${ObservationVariable.tableName} SET study_ids = ? WHERE internal_id_observation_variable = ?",
//                arrayOf(jsonArray.toString(), traitDbId)
//            )
//        }

        fun updateTraitVisibility(traitDbId: String, visible: Boolean, fieldId: Int) = withDatabase { db ->
            // Check if the link between the trait and the field already exists
            val exists = db.rawQuery(
                "SELECT COUNT(*) FROM studies_observation_variables_link WHERE observation_variable_id = ? AND study_id = ?",
                arrayOf(traitDbId, fieldId.toString())
            ).use { cursor ->
                cursor.moveToFirst() && cursor.getInt(0) > 0
            }

            if (exists) {
                // Update the existing link to set the visibility
                db.execSQL(
                    "UPDATE studies_observation_variables_link SET visibility = ? WHERE observation_variable_id = ? AND study_id = ?",
                    arrayOf(if (visible) 1 else 0, traitDbId, fieldId.toString())
                )
                Log.d("ObservationVariableDao", "Updated link: traitDbId=$traitDbId, fieldId=$fieldId, visibility=$visible")
            } else {
                // Insert a new link if it doesn't exist (without position for now)
                db.execSQL(
                    "INSERT INTO studies_observation_variables_link (observation_variable_id, study_id, visibility) VALUES (?, ?, ?)",
                    arrayOf(traitDbId, fieldId.toString(), if (visible) 1 else 0)
                )
                Log.d("ObservationVariableDao", "Inserted new link: traitDbId=$traitDbId, fieldId=$fieldId, visibility=$visible")
            }
        }

        private fun removeFieldIdFromJsonArray(jsonArray: org.json.JSONArray, fieldId: Int) {
            for (i in jsonArray.length() - 1 downTo 0) {
                if (jsonArray.getInt(i) == fieldId) {
                    jsonArray.remove(i)
                }
            }
        }
        private fun jsonArrayContains(jsonArray: org.json.JSONArray, value: Int): Boolean {
            for (i in 0 until jsonArray.length()) {
                val currentElement = jsonArray.getInt(i)
                if (currentElement == value) {
                    return true
                }
            }
            return false
        }

        fun writeNewPosition(queryColumn: String, id: String, position: String) = withDatabase { db ->

            db.update(ObservationVariable.tableName,
                    ContentValues().apply {
                        put("position", position)
                    }, "$queryColumn = ?", arrayOf(id))

        }

//        fun getTraitColumnsAsString() = getAllTraits().joinToString(",")

    }
}