package com.fieldbook.shared.database.repository

import com.fieldbook.shared.database.models.FieldObject
import com.fieldbook.shared.sqldelight.FieldbookDatabase

class StudiesRepo(private val db: FieldbookDatabase) {

    enum class SortOrder { DateImport, Visible, Name }

    fun getAllFields(sort: SortOrder = SortOrder.DateImport): List<FieldObject> {
        val rows = when (sort) {
            SortOrder.DateImport -> db.studiesQueries.allByDateDesc().executeAsList()
            SortOrder.Visible -> db.studiesQueries.allByVisibleAsc().executeAsList()
            SortOrder.Name -> db.studiesQueries.allByNameAsc().executeAsList()
        }
        return rows.map { r ->
            FieldObject(
                expId = r.internal_id_study.toInt(),
                expName = r.name ?: "",
                expAlias = "",
                uniqueId = "",
                primaryId = "",
                secondaryId = "",
                dateImport = r.date_import?.toString() ?: "",
            )
        }
    }
}
