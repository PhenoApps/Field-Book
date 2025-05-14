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

        //TODO 471 trait name is used instead of trait db id
        fun getDetail(trait: String): TraitObject? = withDatabase { db ->
            val traitObject = ObservationVariableDao.getTraitByName(trait)
            traitObject?.loadAttributeAndValues()
            traitObject
        }
    }
}