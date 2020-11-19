package com.fieldbook.tracker.database.dao

import com.fieldbook.tracker.database.Migrator.Companion.sVisibleObservationVariableViewName
import com.fieldbook.tracker.database.Migrator.ObservationVariable
import com.fieldbook.tracker.database.Migrator.ObservationVariableAttribute
import com.fieldbook.tracker.database.models.ObservationVariableModel
import com.fieldbook.tracker.database.query
import com.fieldbook.tracker.database.toFirst
import com.fieldbook.tracker.database.toTable
import com.fieldbook.tracker.database.withDatabase
import com.fieldbook.tracker.objects.TraitObject


class VisibleObservationVariableDao {

    companion object {

        fun getVisibleTrait(): Array<String> = withDatabase { db ->

            db.query(sVisibleObservationVariableViewName,
                    select = arrayOf("observation_variable_name"),
                    orderBy = "position").toTable().map { it ->
                it["observation_variable_name"].toString()
            }.toTypedArray()

        } ?: emptyArray<String>()

        fun getVisibleTraitObjects(): Array<ObservationVariableModel> = withDatabase { db ->

            val rows = db.query(sVisibleObservationVariableViewName,
                    orderBy = "position").toTable()

            val variables: Array<ObservationVariableModel?> = arrayOfNulls(rows.size)

            rows.forEachIndexed { index, map ->

                variables[index] = ObservationVariableModel(map)

            }

            variables.mapNotNull { it }.toTypedArray()

        } ?: emptyArray()

        fun getFormat(): Array<String> = withDatabase { db ->

            db.query(sVisibleObservationVariableViewName,
                    arrayOf(ObservationVariable.PK,
                            "observation_variable_field_book_format",
                            "position")).toTable().map { row ->
                row["observation_variable_field_book_format"] as String
            }.toTypedArray()

        } ?: emptyArray()

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
                        select = arrayOf(ObservationVariable.PK,
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
                    id = (this[ObservationVariable.PK] as? Int ?: -1).toString()
                    externalDbId = this["external_db_id"] as? String ?: ""

                    minimum = ""
                    maximum = ""
                    categories = ""

                    ObservationVariableValueDao.getVariableValues(id.toInt()).also { values ->

                        values?.forEach {

                            //println(it)

//                            println(ObservationVariableValueModel.getAll()?.map {
//                                "${it[ObservationVariableAttributeModel.FK]} -> ${it["observation_variable_attribute_value"]}"
//                            })
//                            println(ObservationVariableAttributeModel.getAll()?.map { it["observation_variable_attribute_name"] })

                            val attrName = ObservationVariableAttributeDao.getAttributeNameById(it[ObservationVariableAttribute.FK] as Int)

//                            println(attrName)

                            when (attrName) {
                                "validValuesMin" -> minimum = it["observation_variable_attribute_value"] as? String ?: ""
                                "validValuesMax" -> maximum = it["observation_variable_attribute_value"] as? String ?: ""
                                "category" -> categories = it["observation_variable_attribute_value"] as? String ?: ""
                            }

//                            println(it["observation_variable_attribute_value"])
                        }

//                        minimum = values!!["validValuesMin"] as? String ?: ""
//                        maximum = values["validValuesMax"] as? String ?: ""
//                        categories = values["category"] as? String ?: ""
                    }
                }
            }
        }
    }
}