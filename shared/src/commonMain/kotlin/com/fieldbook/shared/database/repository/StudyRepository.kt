package com.fieldbook.shared.database.repository

import app.cash.sqldelight.db.SqlDriver
import com.fieldbook.shared.AppContext
import com.fieldbook.shared.database.Migrator
import com.fieldbook.shared.database.Migrator.Companion.sObservationUnitPropertyViewName
import com.fieldbook.shared.database.models.FieldObject
import com.fieldbook.shared.sqldelight.FieldbookDatabase

class StudyRepository {

    private val driver: SqlDriver = AppContext.driverFactory().getDriver()
    private val db: FieldbookDatabase = FieldbookDatabase(driver)

    enum class SortOrder { DateImport, Visible, Name }

    fun getAllFields(sort: SortOrder = SortOrder.DateImport): List<FieldObject> {
        return when (sort) {
            SortOrder.DateImport ->
                db.studiesQueries.allByDateDesc().executeAsList().map { r ->
                    FieldObject(
                        exp_id = r.internal_id_study.toInt(),
                        exp_name = r.study_name.orEmpty(),
                        exp_alias = r.study_alias.orEmpty(),
                        unique_id = r.study_unique_id_name.orEmpty(),
                        primary_id = r.study_primary_id_name.orEmpty(),
                        secondary_id = r.study_secondary_id_name.orEmpty(),
                        date_import = r.date_import?.toString() ?: "",
                        date_edit = r.date_edit,
                        date_export = null,
                        date_sync = null,
                        import_format = r.import_format,
                        exp_source = null,
                        count = null,
                        observation_level = null,
                        attribute_count = r.attribute_count.toString(),
                        trait_count = r.trait_count.toString(),
                        observation_count = r.observation_count.toString(),
                        trial_name = null,
                        search_attribute = null
                    )
                }

            SortOrder.Visible ->
                db.studiesQueries.allByVisibleAsc().executeAsList().map { r ->
                    FieldObject(
                        exp_id = r.internal_id_study.toInt(),
                        exp_name = r.study_name.orEmpty(),
                        exp_alias = r.study_alias.orEmpty(),
                        unique_id = r.study_unique_id_name.orEmpty(),
                        primary_id = r.study_primary_id_name.orEmpty(),
                        secondary_id = r.study_secondary_id_name.orEmpty(),
                        date_import = r.date_import?.toString() ?: "",
                        date_edit = r.date_edit,
                        date_export = null,
                        date_sync = null,
                        import_format = null,
                        exp_source = null,
                        count = null,
                        observation_level = null,
                        attribute_count = r.attribute_count.toString(),
                        trait_count = r.trait_count.toString(),
                        observation_count = r.observation_count.toString(),
                        trial_name = null,
                        search_attribute = null
                    )
                }

            SortOrder.Name ->
                db.studiesQueries.allByNameAsc().executeAsList().map { r ->
                    FieldObject(
                        exp_id = r.internal_id_study.toInt(),
                        exp_name = r.study_name.orEmpty(),
                        exp_alias = r.study_alias.orEmpty(),
                        unique_id = r.study_unique_id_name.orEmpty(),
                        primary_id = r.study_primary_id_name.orEmpty(),
                        secondary_id = r.study_secondary_id_name.orEmpty(),
                        date_import = r.date_import?.toString() ?: "",
                        date_edit = r.date_edit,
                        date_export = null,
                        date_sync = null,
                        import_format = null,
                        exp_source = null,
                        count = null,
                        observation_level = null,
                        attribute_count = r.attribute_count.toString(),
                        trait_count = r.trait_count.toString(),
                        observation_count = r.observation_count.toString(),
                        trial_name = null,
                        search_attribute = null
                    )
                }
        }
    }

    /**
     * Ensures observation_units_attributes is synchronized with plot attributes for the given study.
     * This is a type-safe version of fixPlotAttributes from StudyDao.
     */
    private fun fixPlotAttributes(studyId: Int) {
        db.observation_units_attributesQueries.insertOrReplaceFromPlotAttributes(studyId.toLong())
    }

    /**
     * Transpose observation unit attribute/values into a table for the selected study.
     * This mimics the switchField logic from StudyDao.
     */
    fun switchField(studyId: Int) {
        fixPlotAttributes(studyId)

        val attributeNames =
            db.observation_units_attributesQueries.getAllNamesByStudyId(studyId.toLong())
                .executeAsList()
                .filter { it != "geo_coordinates" }

        val selectClauses = attributeNames.map { col ->
            "MAX(CASE WHEN attr.observation_unit_attribute_name = \"$col\" THEN vals.observation_unit_value_name ELSE NULL END) AS \"$col\""
        }
        val selectStatement = if (selectClauses.isNotEmpty()) {
            selectClauses.joinToString(", ") + ", "
        } else ""

        driver.execute(
            identifier = null,
            sql = "DROP TABLE IF EXISTS $sObservationUnitPropertyViewName",
            parameters = 0
        )

        /**
         * Creating a view here is faster, but
         * using a table gives better performance for getRangeByIdAndPlot query
         */
        val query = """
            CREATE TABLE $sObservationUnitPropertyViewName AS 
            SELECT $selectStatement units.${Migrator.ObservationUnit.PK} AS id, units.`geo_coordinates` as "geo_coordinates"
            FROM ${Migrator.ObservationUnit.tableName} AS units
            LEFT JOIN ${Migrator.ObservationUnitValue.tableName} AS vals ON units.${Migrator.ObservationUnit.PK} = vals.${Migrator.ObservationUnit.FK}
            LEFT JOIN ${Migrator.ObservationUnitAttribute.tableName} AS attr on vals.${Migrator.ObservationUnitAttribute.FK} = attr.${Migrator.ObservationUnitAttribute.PK}
            LEFT JOIN plot_attributes as a on vals.observation_unit_attribute_db_id = a.attribute_id
            WHERE units.${Migrator.Study.FK} = $studyId
            GROUP BY units.${Migrator.ObservationUnit.PK}
        """.trimIndent()

        driver.execute(
            identifier = null,
            sql = query,
            parameters = 0
        )
    }
}
