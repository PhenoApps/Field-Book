package com.fieldbook.tracker.database.models

import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import androidx.core.content.contentValuesOf
import com.fieldbook.tracker.database.*
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.objects.TraitObject
import io.swagger.client.model.Trait

/**
 * Trait-level table structure.
 */
data class ObservationVariableModel(val map: Row) {
        val observation_variable_name: String? by map
        val observation_variable_field_book_format: String? by map
        val default_value: String? by map
        val visible: String? by map
        val position: Int by map
        val external_db_id: String? by map
        val trait_data_source: String? by map
        val additional_info: String? by map //use value/attr
        val common_crop_name: String? by map
        val language: String? by map
        val data_type: String? by map
        val observation_variable_db_id: String? by map //brapId ?
        val ontology_db_id: String? by map //brapId
        val ontology_name: String? by map
        val minimum: String? by map
        val maximum: String? by map
        val categories: String? by map
        val observation_variable_details: String? by map
        val internal_id_observation_variable: Int by map
    companion object {
        const val PK = "internal_id_observation_variable"
        const val FK = "observation_variable_db_id"
        val migrateFromTableName = "traits"
        val tableName = "observation_variables"
        val columnDefs by lazy {
            mapOf("observation_variable_name" to "TEXT",
                    "observation_variable_field_book_format" to "TEXT",
                    "default_value" to "TEXT",
                    "visible" to "TEXT",
                    "position" to "INT",
                    "external_db_id" to "TEXT",
                    "trait_data_source" to "TEXT",
                    "additional_info" to "TEXT",
                    "common_crop_name" to "TEXT",
                    "language" to "TEXT",
                    "data_type" to "TEXT",
                    "observation_variable_db_id" to "TEXT",
                    "ontology_db_id" to "TEXT",
                    "ontology_name" to "TEXT",
                    "observation_variable_details" to "TEXT",
                    PK to "INTEGER PRIMARY KEY AUTOINCREMENT")
        }

        val migratePattern by lazy {
            mapOf(  "id" to PK,
                    "trait" to "observation_variable_name",
                    "format" to "observation_variable_field_book_format",
                    "defaultValue" to "default_value",
                    "isVisible" to "visible",
                    "realPosition" to "position",
                    "external_db_id" to "external_db_id",
                    "trait_data_source" to "trait_data_source",
                    "details" to "observation_variable_details")
        }

        fun hasTrait(name: String): Boolean = withDatabase { db ->

            db.query(tableName,
                    select = arrayOf("observation_variable_name"),
                    where = "observation_variable_name = ? COLLATE NOCASE",
                    whereArgs = arrayOf(name)).toFirst().isNotEmpty()

        } ?: false

        /**
         * TODO: Replace with View.
         */
        fun getTraitExists(uniqueName: String, id: Int, parent: String, trait: String): Boolean = withDatabase { db ->

            "value" in db.rawQuery("""
                SELECT id, value
                FROM observations, ObservationUnitProperty
                WHERE observations.observation_unit_db_id = ObservationUnitProperty.'$uniqueName' 
                    AND ObservationUnitProperty.id = ? 
                    AND observations.observation_variable_name LIKE ? 
                    AND observations.observation_variable_field_book_format LIKE ?
                """.trimIndent(), arrayOf(id.toString(), parent, trait)).toFirst().keys

        } ?: false

        fun getAllTraits(): Array<String> = withDatabase { db ->

            db.query(ObservationVariableModel.tableName,
                    arrayOf(ObservationVariableModel.PK, "observation_variable_name", "position")).use {

                it.toTable().map { row ->
                    row["observation_variable_name"] as String
                }.toTypedArray()
            }

        } ?: arrayOf()

        fun getTraitColumnData(column: String): Array<Any> = withDatabase { db ->

            db.query(ObservationVariableModel.tableName,
                    arrayOf(column)).use {

                it.toTable().mapNotNull { row ->
                    row[column]
                }.toTypedArray()
            }

        } ?: arrayOf()

        fun getTraitColumns(): Array<String> = withDatabase { db ->

            db.query(ObservationVariableModel.tableName).use {

                (it.toTable().first().keys - setOf("id", "external_db_id", "trait_data_source")).toTypedArray()
            }

        } ?: arrayOf()

        /**
         * Alternative version of getTraitColumns that also returns
         * variable attribute names.
         */
        fun getTraitPropertyColumns(): Array<String> = withDatabase { db ->

            val newTraits = db.query(ObservationVariableModel.tableName).use {

                val names =  ObservationVariableAttributeModel.getAllNames()!!
                (names + it.toTable().first().keys - setOf("id", "external_db_id", "trait_data_source")).toTypedArray()
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

        fun getAllTraitsForExport(): Cursor? = withDatabase { db ->

            //trait,format,defaultValue,minimum,maximum,details,categories,isVisible,realPosition
            MatrixCursor(getTraitPropertyColumns()).also { cursor ->
                val traits = ObservationVariableModel.getAllTraitObjects()
                traits.sortBy { it.id.toInt() }
                traits.forEach { trait ->
                    cursor.addRow(arrayOf(trait.trait, trait.format, trait.defaultValue,
                            trait.minimum, trait.maximum, trait.details, trait.categories,
                            trait.visible, trait.realPosition))
                }

            }
        }

        fun getAllTraitObjects(): Array<TraitObject> = withDatabase { db ->

            db.query(tableName, orderBy = "position").toTable().map {

                TraitObject().apply {

                    trait = (it["observation_variable_name"] as? String ?: "")
                    format = it["observation_variable_field_book_format"] as? String ?: ""
                    defaultValue = it["default_value"] as? String ?: ""
                    details = it["observation_variable_details"] as? String ?: ""
                    id = (it[PK] as? Int ?: -1).toString()
                    externalDbId = it["external_db_id"] as? String ?: ""
                    realPosition = (it["position"] as? Int ?: -1).toString()
                    visible = (it["visible"] as String).toBoolean()

                    //initialize these to the empty string or else they will be null
                    maximum = ""
                    minimum = ""
                    categories = ""

                    ObservationVariableValueModel.getVariableValues(id.toInt()).also { values ->

                        values?.forEach { value ->

                            val attrName = ObservationVariableAttributeModel.getAttributeNameById(value[ObservationVariableAttributeModel.FK] as Int)

                            when (attrName) {
                                "validValuesMin" -> minimum = value["observation_variable_attribute_value"] as? String ?: ""
                                "validValuesMax" -> maximum = value["observation_variable_attribute_value"] as? String ?: ""
                                "category" -> categories = value["observation_variable_attribute_value"] as? String ?: ""
                            }
                        }
                    }
                }
            }.toTypedArray()

        } ?: arrayOf()

        fun getTraitVisibility(): Map<String, String> = withDatabase { db ->

            db.query(tableName,
                    select = arrayOf("observation_variable_name", "visible"))
                    .toTable().asSequence().flatMap { it.asSequence() }
                    .groupBy({ it.key }, { it.value })
                    .mapValues { it.value.first() as String }

        } ?: emptyMap()

        //todo switched name to 'getDetails'
        fun getDetails(trait: String): Array<ObservationVariableModel> = withDatabase { db ->

            arrayOf(*db.query(sVisibleObservationVariableViewName,
                    where = "observation_variable_name LIKE $trait")
                    .toTable().map {
                        ObservationVariableModel(it)
                    }.toTypedArray())

        } ?: arrayOf()

        fun getDetail(trait: String): TraitObject? = withDatabase { db ->

            //return a trait object but requires multiple queries to use the attr/values table.
            TraitObject().apply {
                //creates local scoping around the map result from querying for variable names
                with(db.query(sVisibleObservationVariableViewName,
                        select = arrayOf(PK,
                                "observation_variable_name",
                                "observation_variable_field_book_format",
                                "observation_variable_details",
                                "default_value"),
                        where = "observation_variable_name LIKE ?",
                        whereArgs = arrayOf(trait)).toFirst()) {
                    //use the local scoping to initialize trait object fields
                    setTrait(this["observation_variable_name"] as? String ?: "")
                    format = this["observation_variable_field_book_format"] as? String ?: ""
                    defaultValue = this["default_value"] as? String ?: ""
                    details = this["observation_variable_details"] as? String ?: ""
                    id = (this[PK] as? Int ?: -1).toString()
                    externalDbId = this["external_db_id"] as? String ?: ""

                    minimum = ""
                    maximum = ""
                    categories = ""

                    ObservationVariableValueModel.getVariableValues(id.toInt()).also { values ->

                        values?.forEach {

                            //println(it)

                            println(ObservationVariableValueModel.getAll()?.map {
                                "${it[ObservationVariableAttributeModel.FK]} -> ${it["observation_variable_attribute_value"]}"
                            })
//                            println(ObservationVariableAttributeModel.getAll()?.map { it["observation_variable_attribute_name"] })

                            val attrName = ObservationVariableAttributeModel.getAttributeNameById(it[ObservationVariableAttributeModel.FK] as Int)

                            println(attrName)

                            when (attrName) {
                                "validValuesMin" -> minimum = it["observation_variable_attribute_value"] as? String ?: ""
                                "validValuesMax" -> maximum = it["observation_variable_attribute_value"] as? String ?: ""
                                "category" -> categories = it["observation_variable_attribute_value"] as? String ?: ""
                            }

                            println(it["observation_variable_attribute_value"])
                        }

//                        minimum = values!!["validValuesMin"] as? String ?: ""
//                        maximum = values["validValuesMax"] as? String ?: ""
//                        categories = values["category"] as? String ?: ""
                    }
                }
            }
        }

        //TODO missing obs. vars. for min/max/categories
        fun insertTraits(t: TraitObject) = withDatabase { db ->

            if (hasTrait(t.trait)) -1
            else {

                //iterate trhough mapping of the old columns that are now attr/vals
//                    mapOf(
//                        "validValuesMin" to trait.minimum as String,
//                        "validValuesMax" to trait.maximum as String,
//                        "category" to trait.categories as String,
//                    ).asSequence().forEach { attrValue ->
//
//                        //TODO: commenting this out would create a sparse table from the unused attribute values
////                        if (attrValue.value.isNotEmpty()) {
//
//                            val rowid = db.insert(ObservationVariableValueModel.tableName, null, contentValuesOf(
//
//                                    ObservationVariableModel.FK to trait.id,
//                                    ObservationVariableAttributeModel.FK to attrIds[attrValue.key],
//                                    "observation_variable_attribute_value" to attrValue.value,
//                            ))
//
//                            println("$rowid Inserting ${attrValue.key} = ${attrValue.value} at ${attrIds[attrValue.key]}")
////                        }
//                    }

                val varRowId = db.insert(tableName, null,
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
                        })

                //iterate trhough mapping of the old columns that are now attr/vals
                mapOf(
                    "validValuesMin" to t.minimum as String,
                    "validValuesMax" to t.maximum as String,
                    "category" to t.categories as String,
                ).asSequence().forEach { attrValue ->

                    //TODO: commenting this out would create a sparse table from the unused attribute values
//                    if (attrValue.value.isNotEmpty()) {

                        val attrId = ObservationVariableAttributeModel.getAttributeIdByName(attrValue.key)

                        val rowid = db.insert(ObservationVariableValueModel.tableName, null, contentValuesOf(

                                FK to varRowId,
                                ObservationVariableAttributeModel.FK to attrId,
                                "observation_variable_attribute_value" to attrValue.value

                        ))
//                    }
                }

                varRowId

            }

        } ?: -1

        fun deleteTrait(id: String) = withDatabase { db ->
            db.delete(ObservationVariableModel.tableName,
                    "$PK = ?", arrayOf(id))
        }

        fun updateTraitPosition(id: String, realPosition: String) = withDatabase { db ->

            db.update(ObservationVariableModel.tableName, ContentValues().apply {
                put("position", realPosition)
            }, "$PK = ?", arrayOf(id))
        }

        //TODO need to edit min/max/category obs. var. val/attrs
        fun editTraits(id: String, trait: String, format: String, defaultValue: String,
                       minimum: String, maximum: String, details: String, categories: String): Long = withDatabase { db ->

            val rowid = db.update(ObservationVariableModel.tableName, ContentValues().apply {
                put("observation_variable_name", trait)
                put("observation_variable_field_book_format", format)
                put("default_value", defaultValue)
            }, "$PK = ?", arrayOf(id)).toLong()

            arrayOf("validValuesMin", "validValuesMax", "category").forEach {

                val attrId = ObservationVariableAttributeModel.getAttributeIdByName(it)

                when(it) {
                    "validValuesMin" -> {
                        db.update(ObservationVariableValueModel.tableName, ContentValues().apply {
                            put("observation_variable_attribute_value", minimum)

                        }, "$FK = ? AND ${ObservationVariableAttributeModel.FK} = ?", arrayOf(id, attrId.toString()))
                    }
                    "validValuesMax" -> {
                        db.update(ObservationVariableValueModel.tableName, ContentValues().apply {
                            put("observation_variable_attribute_value", maximum)

                        }, "$FK = ? AND ${ObservationVariableAttributeModel.FK} = ?", arrayOf(id, attrId.toString()))
                    }
                    "category" -> {
                        db.update(ObservationVariableValueModel.tableName, ContentValues().apply {
                            put("observation_variable_attribute_value", categories)
                        }, "$FK = ? AND ${ObservationVariableAttributeModel.FK} = ?", arrayOf(id, attrId.toString()))
                    }
                }
            }

            rowid

        } ?: -1L

        fun updateTraitVisibility(trait: String, visible: Boolean) = withDatabase { db ->

            db.update(ObservationVariableModel.tableName,
                    ContentValues().apply { put("visible", visible) },
                    "observation_variable_name LIKE ?",
                    arrayOf(trait))
        }

        fun writeNewPosition(column: String, id: String, position: String) = withDatabase { db ->

            db.update(ObservationVariableModel.tableName,
                    ContentValues().apply {
                        put("position", position)
                    }, "$column = ?", arrayOf(id))

        }

        fun getTraitColumnsAsString() = getAllTraits().joinToString(",")

    }
}