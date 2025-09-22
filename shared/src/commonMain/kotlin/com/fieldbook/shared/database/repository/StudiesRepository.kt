package com.fieldbook.shared.database.repository

import com.fieldbook.shared.database.models.FieldObject
import com.fieldbook.shared.sqldelight.FieldbookDatabase

class StudiesRepository(private val db: FieldbookDatabase) {

    enum class SortOrder { DateImport, Visible, Name }

    fun getAllFields(sort: SortOrder = SortOrder.DateImport): List<FieldObject> {
        return when (sort) {
            SortOrder.DateImport ->
                db.studiesQueries.allByDateDesc().executeAsList().map { r ->
                    FieldObject(
                        expId = r.internal_id_study.toInt(),
                        expName = r.study_name.orEmpty(),
                        expAlias = r.study_alias.orEmpty(),
                        uniqueId = r.study_unique_id_name.orEmpty(),
                        primaryId = r.study_primary_id_name.orEmpty(),
                        secondaryId = r.study_secondary_id_name.orEmpty(),
                        dateImport = r.date_import?.toString().orEmpty(),
                        dateEdit = r.date_edit
                    )
                }
            SortOrder.Visible ->
                db.studiesQueries.allByVisibleAsc().executeAsList().map { r ->
                    FieldObject(
                        expId = r.internal_id_study.toInt(),
                        expName = r.study_name.orEmpty(),
                        expAlias = r.study_alias.orEmpty(),
                        uniqueId = r.study_unique_id_name.orEmpty(),
                        primaryId = r.study_primary_id_name.orEmpty(),
                        secondaryId = r.study_secondary_id_name.orEmpty(),
                        dateImport = r.date_import?.toString().orEmpty(),
                        dateEdit = r.date_edit
                    )
                }
            SortOrder.Name ->
                db.studiesQueries.allByNameAsc().executeAsList().map { r ->
                    FieldObject(
                        expId = r.internal_id_study.toInt(),
                        expName = r.study_name.orEmpty(),
                        expAlias = r.study_alias.orEmpty(),
                        uniqueId = r.study_unique_id_name.orEmpty(),
                        primaryId = r.study_primary_id_name.orEmpty(),
                        secondaryId = r.study_secondary_id_name.orEmpty(),
                        dateImport = r.date_import?.toString().orEmpty(),
                        dateEdit = r.date_edit
                    )
                }
        }
    }
}
