package com.fieldbook.tracker.database.dao

import com.fieldbook.tracker.database.Migrator.Companion.sVisibleObservationVariableViewName
import com.fieldbook.tracker.database.Migrator.ObservationVariable
import com.fieldbook.tracker.database.Migrator.ObservationVariableAttribute
import com.fieldbook.tracker.database.query
import com.fieldbook.tracker.database.toTable
import com.fieldbook.tracker.database.withDatabase
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys


class VisibleObservationVariableDao {

    companion object {

        fun getVisibleTrait(sortOrder: String = "position"): Array<String> = withDatabase { db ->

            db.query(sVisibleObservationVariableViewName)
                .toTable()
                .sortedBy { (it[if (sortOrder == "visible") "position" else sortOrder] as? String ?: "position").lowercase() }
                .map { it["observation_variable_name"] as? String ?: ""}
                .toTypedArray()

        } ?: emptyArray<String>()

        fun getVisibleTraitObjects(sortOrder: String = "position"): ArrayList<TraitObject> = withDatabase { db ->
            val rows = db.query(sVisibleObservationVariableViewName)
                .toTable()
                .sortedBy { (it[if (sortOrder == "visible") "position" else sortOrder] as? String ?: "position").lowercase() }

            val variables: ArrayList<TraitObject> = ArrayList()

            rows.forEach { map ->
                variables.add(TraitObject().apply {
                    id = (map[ObservationVariable.PK] as? Int ?: -1).toString()
                    name = map["observation_variable_name"] as? String ?: ""
                })
            }

            variables
        } ?: ArrayList()

        fun getFormat(): Array<String> = withDatabase { db ->

            db.query(sVisibleObservationVariableViewName,
                    arrayOf(ObservationVariable.PK,
                            "observation_variable_field_book_format",
                            "position")).toTable().map { row ->
                row["observation_variable_field_book_format"] as String
            }.toTypedArray()

        } ?: emptyArray()

//        //todo switched name to 'getDetails'
//        fun getDetails(trait: String): Array<ObservationVariableModel> = withDatabase { db ->
//
//            arrayOf(*db.query(sVisibleObservationVariableViewName,
//                    where = "observation_variable_name LIKE $trait")
//                    .toTable().map {
//                        ObservationVariableModel(it)
//                    }.toTypedArray())
//
//        } ?: arrayOf()

        fun getDetail(trait: String): TraitObject? = withDatabase { db ->

            //return a trait object but requires multiple queries to use the attr/values table.
            ObservationVariableDao.getAllTraitObjects().first { it.name == trait }.apply {
                ObservationVariableValueDao.getVariableValues(id.toInt()).also { values ->

                    values?.forEach {

                        val attrName =
                            ObservationVariableAttributeDao.getAttributeNameById(it[ObservationVariableAttribute.FK] as Int)


                        when (attrName) {
                            "validValuesMin" -> minimum =
                                it["observation_variable_attribute_value"] as? String ?: ""

                            "validValuesMax" -> maximum =
                                it["observation_variable_attribute_value"] as? String ?: ""

                            "category" -> categories =
                                it["observation_variable_attribute_value"] as? String ?: ""

                            "closeKeyboardOnOpen" -> closeKeyboardOnOpen =
                                (it["observation_variable_attribute_value"] as? String ?: "false").toBoolean()
                        }

                    }
                }
            }
        }
    }
}