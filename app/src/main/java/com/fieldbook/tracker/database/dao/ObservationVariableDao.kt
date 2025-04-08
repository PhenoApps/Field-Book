package com.fieldbook.tracker.database.dao

import android.annotation.SuppressLint
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.util.Log
import com.fieldbook.tracker.database.*
import com.fieldbook.tracker.database.Migrator.ObservationVariable
import com.fieldbook.tracker.database.Migrator.ObservationVariableAttribute
import com.fieldbook.tracker.database.models.ObservationVariableModel
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.parameters.Parameters

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

        /**
        * Generic function to get trait objects with optional filtering criteria
        * @param id_filter Optional ID to filter by
        * @param name_filter Optional name to filter by
        * @param externalDbId_filter Optional external DB ID to filter by
        * @param traitDataSource_filter Optional data source to filter by
        * @param sortOrder How to sort the results
        * @return List of matching TraitObjects with attributes loaded
        */
        fun getTraitObjects(
            id_filter: Int? = null,
            name_filter: String? = null,
            externalDbId_filter: String? = null,
            traitDataSource_filter: String? = null,
            sortOrder: String = "position"
        ): ArrayList<TraitObject> = withDatabase { db ->
            val traits = ArrayList<TraitObject>()
            
            // Build WHERE clause based on provided parameters
            val whereClauseBuilder = StringBuilder()
            val whereArgs = ArrayList<String>()
            
            if (id_filter != null) {
                whereClauseBuilder.append("internal_id_observation_variable = ?")
                whereArgs.add(id_filter.toString())
            }
            
            if (name_filter != null) {
                if (whereClauseBuilder.isNotEmpty()) whereClauseBuilder.append(" AND ")
                whereClauseBuilder.append("observation_variable_name = ? COLLATE NOCASE")
                whereArgs.add(name_filter)
            }
            
            if (externalDbId_filter != null && traitDataSource_filter != null) {
                if (whereClauseBuilder.isNotEmpty()) whereClauseBuilder.append(" AND ")
                whereClauseBuilder.append("external_db_id = ? AND trait_data_source = ?")
                whereArgs.add(externalDbId_filter)
                whereArgs.add(traitDataSource_filter)
            }
            
            val whereClause = if (whereClauseBuilder.isEmpty()) null else whereClauseBuilder.toString()
            
            val query = """
                SELECT * FROM ${ObservationVariable.tableName}
                ${if (whereClause != null) "WHERE $whereClause" else ""}
                ORDER BY ${if (sortOrder == "visible") "position" else sortOrder} COLLATE NOCASE ASC
            """

            Log.d("ObservationVariableDao", "Full query: $query")
            Log.d("ObservationVariableDao", "Query args: ${whereArgs.joinToString()}")

            db.rawQuery(query, whereArgs.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    val trait = TraitObject().apply {
                        name = cursor.getString(cursor.getColumnIndexOrThrow("observation_variable_name")) ?: ""
                        format = cursor.getString(cursor.getColumnIndexOrThrow("observation_variable_field_book_format")) ?: ""
                        defaultValue = cursor.getString(cursor.getColumnIndexOrThrow("default_value")) ?: ""
                        details = cursor.getString(cursor.getColumnIndexOrThrow("observation_variable_details")) ?: ""
                        id = cursor.getInt(cursor.getColumnIndexOrThrow(ObservationVariable.PK)).toString()
                        externalDbId = cursor.getString(cursor.getColumnIndexOrThrow("external_db_id")) ?: ""
                        realPosition = cursor.getInt(cursor.getColumnIndexOrThrow("position"))
                        visible = cursor.getString(cursor.getColumnIndexOrThrow("visible")).toBoolean()
                        additionalInfo = cursor.getString(cursor.getColumnIndexOrThrow("additional_info")) ?: ""

                        // Initialize these to the empty string or else they will be null
                        maximum = ""
                        minimum = ""
                        categories = ""
                        closeKeyboardOnOpen = false
                        cropImage = false

                        val values = ObservationVariableValueDao.getVariableValues(id.toInt())
                        values?.forEach { value ->
                            val attrName = ObservationVariableAttributeDao.getAttributeNameById(value[ObservationVariableAttribute.FK] as Int)
                            when (attrName) {
                                "validValuesMin" -> minimum = value["observation_variable_attribute_value"] as? String ?: ""
                                "validValuesMax" -> maximum = value["observation_variable_attribute_value"] as? String ?: ""
                                "category" -> categories = value["observation_variable_attribute_value"] as? String ?: ""
                                "closeKeyboardOnOpen" -> closeKeyboardOnOpen = (value["observation_variable_attribute_value"] as? String ?: "false").toBoolean()
                                "cropImage" -> cropImage = (value["observation_variable_attribute_value"] as? String ?: "false").toBoolean()
                            }
                        }
                    }
                    traits.add(trait)
                }
            }

            if (sortOrder == "visible") {
                val visibleTraits = traits.filter { it.visible }
                val invisibleTraits = traits.filter { !it.visible }

                ArrayList(visibleTraits.sortedBy { it.realPosition } + ArrayList(invisibleTraits.sortedBy { it.realPosition }))

            } else {
                ArrayList(traits)
            }
        } ?: ArrayList()

        fun getAllTraitObjects(sortOrder: String = "position"): ArrayList<TraitObject> = 
            getTraitObjects(sortOrder = sortOrder)

        // Overload for Java compatibility
        fun getAllTraitObjects(): ArrayList<TraitObject> = 
            getAllTraitObjects("position")

        // fun getTraitById(id: Int): TraitObject? = 
        //     getTraitObjects(id_filter = id).firstOrNull()
        fun getTraitById(id: Int): TraitObject? {
            Log.d("ObservationVariableDao", "getTraitById called with id: $id")
            
            val result = getTraitObjects(id_filter = id).also { traits ->
                Log.d("ObservationVariableDao", "getTraitObjects returned ${traits.size} traits")
                if (traits.isNotEmpty()) {
                    Log.d("ObservationVariableDao", "First trait: id=${traits[0].id}, name=${traits[0].name}")
                }
            }.firstOrNull()
            
            Log.d("ObservationVariableDao", "getTraitById returning: ${result?.name ?: "null"}")
            return result
        }

        fun getTraitByName(name: String): TraitObject? = 
            getTraitObjects(name_filter = name).firstOrNull()

        fun getTraitByExternalDbId(externalDbId: String, traitDataSource: String): TraitObject? = 
            getTraitObjects(externalDbId_filter = externalDbId, traitDataSource_filter = traitDataSource).firstOrNull()

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

        fun getTraitVisibility(): HashMap<String, String> = withDatabase { db ->

            hashMapOf(*db.query(ObservationVariable.tableName,
                    select = arrayOf("observation_variable_name", "visible"))
                    .toTable().map {
                        it["observation_variable_name"].toString() to it["visible"].toString()
                    }.toTypedArray())

        } ?: hashMapOf()

        //TODO missing obs. vars. for min/max/categories
        fun insertTraits(t: TraitObject) = withDatabase { db ->

            if (getTraitByName(t.name) != null) {
                Log.d("ObservationVariableDao", "Trait ${t.name} already exists, skipping insertion.")
                -1
            } else {
                val contentValues = ContentValues().apply {
                    put("external_db_id", t.externalDbId)
                    put("trait_data_source", t.traitDataSource)
                    put("observation_variable_name", t.name)
                    put("observation_variable_details", t.details)
                    put("observation_variable_field_book_format", t.format)
                    put("default_value", t.defaultValue)
                    put("visible", t.visible.toString())
                    put("position", t.realPosition)
                    put("additional_info", t.additionalInfo)
                }

                // Log trait values being inserted
                Log.d("ObservationVariableDao", "Saving trait ${t.name} with values:")
                contentValues.keySet().forEach { key ->
                    contentValues.get(key)?.let { value ->
                        Log.d("ObservationVariableDao", "$key: $value")
                    }
                }

                // Log additional values: min, max, categories
                Log.d("ObservationVariableDao", "And additional attributes:")
                Log.d("ObservationVariableDao", "minimum: ${t.minimum.orEmpty()}")
                Log.d("ObservationVariableDao", "maximum: ${t.maximum.orEmpty()}")
                Log.d("ObservationVariableDao", "categories: ${t.categories.orEmpty()}")
                Log.d("ObservationVariableDao", "closeKeyboardOnOpen: ${t.closeKeyboardOnOpen ?: "false"}")
                Log.d("ObservationVariableDao", "cropImage: ${t.cropImage ?: "false"}")

                val varRowId = db.insert(ObservationVariable.tableName, null, contentValues)

                if (varRowId != -1L) {
                    ObservationVariableValueDao.insert(
                        t.minimum.orEmpty(),
                        t.maximum.orEmpty(),
                        t.categories.orEmpty(),
                        (t.closeKeyboardOnOpen ?: "false").toString(),
                        (t.cropImage ?: "false").toString(),
                        varRowId.toString()
                    )
                    Log.d("ObservationVariableDao", "Trait ${t.name} inserted successfully with row ID: $varRowId")
                } else {
                    Log.e("ObservationVariableDao", "Failed to insert trait ${t.name}")
                }

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

        fun updateTraitPosition(id: String, realPosition: Int) = withDatabase { db ->

            db.update(ObservationVariable.tableName, ContentValues().apply {
                put("position", realPosition)
            }, "${ObservationVariable.PK} = ?", arrayOf(id))
        }

        //TODO need to edit min/max/category obs. var. val/attrs
        fun editTraits(id: String, trait: String, format: String, defaultValue: String,
                       minimum: String, maximum: String, details: String, categories: String,
                       closeKeyboardOnOpen: Boolean,
                       cropImage: Boolean): Long = withDatabase { db ->

            val rowid = db.update(ObservationVariable.tableName, ContentValues().apply {
                put("observation_variable_name", trait)
                put("observation_variable_field_book_format", format)
                put("default_value", defaultValue)
                put("observation_variable_details", details)
            }, "${ObservationVariable.PK} = ?", arrayOf(id)).toLong()


            Parameters.System.forEach {

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
                    "closeKeyboardOnOpen" -> {
                        ObservationVariableValueDao.insertAttributeValue(it, closeKeyboardOnOpen.toString(), id)
                    }
                    "cropImage" -> {
                        ObservationVariableValueDao.insertAttributeValue(it, cropImage.toString(), id)
                    }
                }
            }

            rowid

        } ?: -1L

        fun updateTraitVisibility(traitDbId: String, visible: String) = withDatabase { db ->

            db.update(
                ObservationVariable.tableName,
                ContentValues().apply { put("visible", visible) },
                "internal_id_observation_variable = ?",
                arrayOf(traitDbId)
            )
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