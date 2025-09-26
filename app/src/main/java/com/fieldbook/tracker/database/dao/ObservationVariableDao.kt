package com.fieldbook.tracker.database.dao

import android.annotation.SuppressLint
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.util.Log
import com.fieldbook.tracker.database.*
import com.fieldbook.tracker.database.Migrator.ObservationVariable
import com.fieldbook.tracker.database.models.ObservationVariableModel
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

        fun getTraitById(id: String): TraitObject? = withDatabase { db ->

            db.query(ObservationVariable.tableName,
                where = "internal_id_observation_variable = ?",
                whereArgs = arrayOf(id)).toFirst().toTraitObject()

        }

        fun getTraitByName(name: String): TraitObject? = withDatabase { db ->

            db.query(ObservationVariable.tableName,
                    where = "observation_variable_name = ? COLLATE NOCASE",
                    whereArgs = arrayOf(name)).toFirst().toTraitObject()

        }

        fun getTraitByAlias(alias: String): TraitObject? = withDatabase { db ->

            db.query(ObservationVariable.tableName,
                where = "observation_variable_alias = ? COLLATE NOCASE",
                whereArgs = arrayOf(alias)).toFirst().toTraitObject()

        }

        fun getTraitByExternalDbId(externalDbId: String, traitDataSource: String): TraitObject? = withDatabase { db ->

            db.query(ObservationVariable.tableName,
                    where = "external_db_id = ? AND trait_data_source = ? ",
                    whereArgs = arrayOf(externalDbId, traitDataSource)).toFirst().toTraitObject()

        }

        private fun Map<String, Any?>.toTraitObject() = if (this.isEmpty()) {null} else { TraitObject().also {

            it.id = this[ObservationVariable.PK].toString()
            it.name = this["observation_variable_name"] as? String ?: ""
            it.alias = this["observation_variable_alias"] as? String ?: ""
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
            it.externalDbId = this["external_db_id"] as? String ?: ""
            it.traitDataSource = this["trait_data_source"] as? String ?: ""

            it.loadAttributeAndValues()
        }
        }

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

            } == true

        fun getAllTraits(): Array<String> = withDatabase { db ->

            db.query(ObservationVariable.tableName,
                    arrayOf(ObservationVariable.PK, "observation_variable_name", "position"),
                    orderBy = "position").use {

                it.toTable().map { row ->
                    row["observation_variable_name"] as String
                }.toTypedArray()
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
                            "traitAlias" -> trait.alias
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
                            "traitAlias" -> trait.alias
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

        fun getAllTraitObjects(sortOrder: String = "position"): ArrayList<TraitObject> = withDatabase { db ->
            val traits = ArrayList<TraitObject>()

            val query = """
                SELECT * FROM ${ObservationVariable.tableName}
                ORDER BY ${if (sortOrder == "visible") "position" else sortOrder} COLLATE NOCASE ASC
            """

            db.rawQuery(query, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val trait = TraitObject().apply {
                        loadFromCursor(cursor)
                    }
                    traits.add(trait)
                }

                TraitAttributeValuesHelper.loadAttributeValuesForAllTraits(traits)
            }

            if (sortOrder == "visible") {
                val visibleTraits = traits.filter { it.visible }
                val invisibleTraits = traits.filter { !it.visible }

                ArrayList(visibleTraits.sortedBy { it.realPosition } + ArrayList(invisibleTraits.sortedBy { it.realPosition }))

            } else {
                ArrayList(traits)
            }
        } ?: ArrayList()

        fun getAllVisibleTraitObjects(sortOrder: String): ArrayList<TraitObject> = ArrayList(getAllTraitObjects(sortOrder).filter { it.visible })

        // Overload for Java compatibility
        fun getAllTraitObjects(): ArrayList<TraitObject> = getAllTraitObjects("position")

        fun getTraitVisibility(): HashMap<String, String> = withDatabase { db ->

            hashMapOf(*db.query(ObservationVariable.tableName,
                    select = arrayOf("observation_variable_name", "visible"))
                    .toTable().map {
                        it["observation_variable_name"].toString() to it["visible"].toString()
                    }.toTypedArray())

        } ?: hashMapOf()

        fun insertTraits(t: TraitObject) = withDatabase { db ->

            if (getTraitByName(t.name) != null) {
                Log.d("ObservationVariableDao", "Trait ${t.name} already exists, skipping insertion.")
                -1
            } else {
                val contentValues = ContentValues().apply {
                    put("external_db_id", t.externalDbId)
                    put("trait_data_source", t.traitDataSource)
                    put("observation_variable_name", t.name)
                    put("observation_variable_alias", t.alias)
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
                Log.d("ObservationVariableDao", "minimum: ${t.minimum}")
                Log.d("ObservationVariableDao", "maximum: ${t.maximum}")
                Log.d("ObservationVariableDao", "categories: ${t.categories}")
                Log.d("ObservationVariableDao", "closeKeyboardOnOpen: ${t.closeKeyboardOnOpen}")
                Log.d("ObservationVariableDao", "cropImage: ${t.cropImage}")
                Log.d("ObservationVariableDao", "saveImage: ${t.saveImage}")
                Log.d("ObservationVariableDao", "useDayOfYear: ${t.useDayOfYear}")
                Log.d("ObservationVariableDao", "displayValue: ${t.categoryDisplayValue}")
                Log.d("ObservationVariableDao", "resourceFile: ${t.resourceFile}")

                val varRowId = db.insert(ObservationVariable.tableName, null, contentValues)

                if (varRowId != -1L) {
                    t.id = varRowId.toString()
                    t.saveAttributeValues()
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

        fun editTraits(id: String, trait: String, traitAlias: String, format: String, defaultValue: String,
                       minimum: String, maximum: String, details: String, categories: String,
                       closeKeyboardOnOpen: Boolean,
                       cropImage: Boolean,
                       saveImage: Boolean,
                       useDayOfYear: Boolean,
                       categoryDisplayValue: Boolean,
                       resourceFile: String,
                       synonyms: List<String>,
                       decimalPlacesRequired: String,
                       mathSymbolsEnabled: Boolean,
                       allowMulticat: Boolean,
                       repeatMeasure: Boolean): Long = withDatabase { db ->

           val contentValues = ContentValues().apply {
               put("observation_variable_name", trait)
               put("observation_variable_alias", traitAlias)
               put("observation_variable_field_book_format", format)
               put("default_value", defaultValue)
               put("observation_variable_details", details)
           }

            val rowid = db.update(
                ObservationVariable.tableName,
                contentValues,
                "${ObservationVariable.PK} = ?",
                arrayOf(id)
            ).toLong()

            if (rowid > 0) {
                // save attributes and their values
                val traitObj = TraitObject().apply {
                    this.id = id
                    this.minimum = minimum
                    this.maximum = maximum
                    this.categories = categories
                    this.closeKeyboardOnOpen = closeKeyboardOnOpen
                    this.cropImage = cropImage
                    this.saveImage = saveImage
                    this.useDayOfYear = useDayOfYear
                    this.categoryDisplayValue = categoryDisplayValue
                    this.resourceFile = resourceFile
                    this.synonyms = synonyms
                    this.maxDecimalPlaces = decimalPlacesRequired
                    this.mathSymbolsEnabled = mathSymbolsEnabled
                    this.allowMulticat = allowMulticat
                    this.repeatedMeasures = repeatMeasure
                }

                traitObj.saveAttributeValues()
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

        fun updateTraitAlias(traitDbId: String, newName: String) = withDatabase { db ->
            val contentValues = ContentValues().apply { put("observation_variable_alias", newName) }
            db.update(ObservationVariable.tableName, contentValues, "${ObservationVariable.PK} = ?", arrayOf(traitDbId))
        }
    }
}