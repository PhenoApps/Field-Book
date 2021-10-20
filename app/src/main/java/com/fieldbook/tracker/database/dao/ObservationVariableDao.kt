package com.fieldbook.tracker.database.dao

import android.annotation.SuppressLint
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import com.fieldbook.tracker.database.*
import com.fieldbook.tracker.database.Migrator.*
import com.fieldbook.tracker.objects.FieldObject
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
            it.trait = this["observation_variable_name"] as? String ?: ""
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

        }}
        /**
         * TODO: Replace with View.
         */
        @SuppressLint("Recycle")
        fun getTraitExists(uniqueName: String, id: Int, parent: String, trait: String): Boolean = withDatabase { db ->

            val query = """
                SELECT id, value
                FROM observations, ObservationUnitProperty
                WHERE observations.observation_unit_id = ObservationUnitProperty.'$uniqueName' 
                    AND ObservationUnitProperty.id = ? 
                    AND observations.observation_variable_name LIKE ? 
                    AND observations.observation_variable_field_book_format LIKE ?
                """.trimIndent()

//            println("$id $parent $trait")
//            println(query)

            val columnNames = db.rawQuery(query, arrayOf(id.toString(), parent, trait)).toFirst().keys

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
                            "trait" -> trait.trait
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

        fun getAllTraitObjects(): ArrayList<TraitObject> = withDatabase { db ->

            ArrayList(db.query(ObservationVariable.tableName, orderBy = "position").toTable().map {

                TraitObject().apply {

                    trait = (it["observation_variable_name"] as? String ?: "")
                    format = it["observation_variable_field_book_format"] as? String ?: ""
                    defaultValue = it["default_value"] as? String ?: ""
                    details = it["observation_variable_details"] as? String ?: ""
                    id = (it[ObservationVariable.PK] as? Int ?: -1).toString()
                    externalDbId = it["external_db_id"] as? String ?: ""
                    realPosition = (it["position"] as? Int ?: -1)
                    visible = (it["visible"] as String).toBoolean()
                    additionalInfo = it["additional_info"] as? String ?: ""

                    //initialize these to the empty string or else they will be null
                    maximum = ""
                    minimum = ""
                    categories = ""

                    ObservationVariableValueDao.getVariableValues(id.toInt()).also { values ->

                        values?.forEach { value ->

                            val attrName = ObservationVariableAttributeDao.getAttributeNameById(value[ObservationVariableAttribute.FK] as Int)

                            when (attrName) {
                                "validValuesMin" -> minimum = value["observation_variable_attribute_value"] as? String
                                        ?: ""
                                "validValuesMax" -> maximum = value["observation_variable_attribute_value"] as? String
                                        ?: ""
                                "category" -> categories = value["observation_variable_attribute_value"] as? String
                                        ?: ""
                            }
                        }
                    }
                }
            })

        } ?: ArrayList()

        fun getTraitVisibility(): HashMap<String, String> = withDatabase { db ->

            hashMapOf(*db.query(ObservationVariable.tableName,
                    select = arrayOf("observation_variable_name", "visible"))
                    .toTable().map {
                        it["observation_variable_name"].toString() to it["visible"].toString()
                    }.toTypedArray())

        } ?: hashMapOf()

        //TODO missing obs. vars. for min/max/categories
        fun insertTraits(t: TraitObject) = withDatabase { db ->

            if (getTraitByName(t.trait) != null) -1
            else {

                val varRowId = db.insert(ObservationVariable.tableName, null,
                        ContentValues().apply {
//                            put(PK, t.id)
                            put("external_db_id", t.externalDbId)
                            put("trait_data_source", t.traitDataSource)
                            put("observation_variable_name", t.trait)
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

        fun updateTraitPosition(id: String, realPosition: Int) = withDatabase { db ->

            db.update(ObservationVariable.tableName, ContentValues().apply {
                put("position", realPosition)
            }, "${ObservationVariable.PK} = ?", arrayOf(id))
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

        fun updateTraitVisibility(trait: String, visible: String) = withDatabase { db ->

            db.update(ObservationVariable.tableName,
                    ContentValues().apply { put("visible", visible) },
                    "observation_variable_name LIKE ?",
                    arrayOf(trait))
        }

        fun writeNewPosition(column: String, id: String, position: String) = withDatabase { db ->

            val queryColumn = when(column) {
                "format" -> "observation_variable_field_book_format"
                else -> "observation_variable_name"
            }
            db.update(ObservationVariable.tableName,
                    ContentValues().apply {
                        put("position", position)
                    }, "$queryColumn = ?", arrayOf(id))

        }

//        fun getTraitColumnsAsString() = getAllTraits().joinToString(",")

    }
}