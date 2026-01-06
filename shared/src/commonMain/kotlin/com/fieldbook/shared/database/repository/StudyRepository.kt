package com.fieldbook.shared.database.repository

import app.cash.sqldelight.db.SqlDriver
import com.fieldbook.shared.AppContext
import com.fieldbook.shared.database.Migrator
import com.fieldbook.shared.database.Migrator.Companion.sObservationUnitPropertyViewName
import com.fieldbook.shared.database.models.FieldObject
import com.fieldbook.shared.sqldelight.FieldbookDatabase

class StudyRepository(
    val db: FieldbookDatabase = FieldbookDatabase(
        AppContext.driverFactory().getDriver()
    )
) {
    private val driver: SqlDriver = AppContext.driverFactory().getDriver()

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
                        date_import = r.date_import ?: "",
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
                        date_import = r.date_import ?: "",
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
                        date_import = r.date_import ?: "",
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

    /**
     * Search for the first studies row that matches the name and observationLevel parameter.
     * Default return value is -1
     */
    fun checkFieldNameAndObsLvl(name: String, observationLevel: String?): Int  {
        val id = db.studiesQueries.getIdByNameAndObsLvl(name, observationLevel ?: "").executeAsOneOrNull()
        return id?.toInt() ?: -1
    }

    fun checkBrapiStudyUnique(observationLevel: String?, brapiId: String?): Int   {
        val id = db.studiesQueries.getIdByBrapiStudyUnique(observationLevel, brapiId ?: "").executeAsOneOrNull()
        return id?.toInt() ?: -1
    }

    /**
     * This function uses a field object to create a exp/study row in the database.
     * Columns are new observation unit attribute names that are inserted as well.
     */
    fun createField(e: FieldObject, timestamp: String, fromBrapi: Boolean = false): Int {
        return when (val sid = if (fromBrapi) checkBrapiStudyUnique(
            e.observation_level,
            e.study_db_id
        ) else checkFieldNameAndObsLvl(e.exp_name, e.observation_level)) {
            -1 -> {
                val rowId = db.studiesQueries.insert(
                    study_db_id = e.study_db_id,
                    study_name = e.exp_name,
                    study_alias = e.exp_alias,
                    study_unique_id_name = e.unique_id,
                    study_primary_id_name = e.primary_id,
                    study_secondary_id_name = e.secondary_id,
                    study_sort_name = e.exp_sort,
                    date_import = timestamp,
                    date_edit = e.date_edit,
                    date_export = e.date_export,
                    date_sync = e.date_sync,
                    import_format = e.import_format,
                    experimental_design = e.exp_layout,
                    common_crop_name = e.exp_species,
                    study_source = e.exp_source,
                    count = e.count,
                    observation_levels = e.observation_level
                )
                rowId.value.toInt()
            }
            else -> sid
        }
    }

    data class FieldPreferenceNames(val unique: String, val primary: String, val secondary: String)

    /**
     * Function that queries the field/study table for the imported unique/primary/secondary names.
     */
    fun getNames(exp_id: Long): FieldPreferenceNames {
        val fieldNames = db.studiesQueries.getFieldPreferenceNamesById(exp_id).executeAsOneOrNull()
        return if (fieldNames == null) {
            FieldPreferenceNames("", "", "")
        } else {
            FieldPreferenceNames(
                unique = fieldNames.study_unique_id_name.orEmpty(),
                primary = fieldNames.study_primary_id_name.orEmpty(),
                secondary = fieldNames.study_secondary_id_name.orEmpty()
            )
        }
    }

    fun createFieldData(
        studyId: Long, columns: List<String>, data: List<String>
    ) {
        val names = getNames(studyId)

        // input data corresponds to original database column names
        val uniqueIndex = columns.indexOf(names.unique)
        val primaryIndex = columns.indexOf(names.primary)
        val secondaryIndex = columns.indexOf(names.secondary)

        // Fill missing data with "NA"
        val actualData = if (data.size != columns.size) {
            val tempData = data.toMutableList()
            repeat(columns.size - data.size) { tempData += "NA" }
            tempData
        } else data

        // Insert into observation_units
        val observationUnitDbId = if (uniqueIndex >= 0) actualData[uniqueIndex] else ""
        val primaryId = if (primaryIndex >= 0) actualData[primaryIndex] else ""
        val secondaryId = if (secondaryIndex >= 0) actualData[secondaryIndex] else ""
        val geoCoordinatesIndex = columns.indexOf("geo_coordinates")
        val geoCoordinates =
            if (geoCoordinatesIndex >= 0 && geoCoordinatesIndex < actualData.size) actualData[geoCoordinatesIndex] else ""

        val rowId = db.observation_unitsQueries.insert(
            study_id = studyId,
            observation_unit_db_id = observationUnitDbId,
            primary_id = primaryId,
            secondary_id = secondaryId,
            geo_coordinates = geoCoordinates,
            additional_info = null,
            germplasm_db_id = null,
            germplasm_name = null,
            observation_level = null,
            position_coordinate_x = null,
            position_coordinate_x_type = null,
            position_coordinate_y = null,
            position_coordinate_y_type = null
        )

        val attributes =
            db.observation_units_attributesQueries.getAllNamesByStudyId(studyId).executeAsList()

        // Insert attribute values for this observation unit
        columns.forEachIndexed { index, colName ->
            if (colName != "geo_coordinates") {
                val attrId = attributes.indexOf(colName)
                // Only insert if attribute exists
                if (attrId >= 0) {
                    db.observation_units_valuesQueries.insert(
                        study_id = studyId,
                        observation_unit_id = rowId.value,
                        observation_unit_attribute_db_id = attrId.toLong(),
                        observation_unit_value_name = actualData[index]
                    )
                }
            }
        }
    }

    fun getById(fieldId: Int): FieldObject {
        return db.studiesQueries.getById(fieldId.toLong()).executeAsOneOrNull()?.let { r ->
            FieldObject(
                exp_id = r.internal_id_study.toInt(),
                exp_name = r.study_name.orEmpty(),
                exp_alias = r.study_alias.orEmpty(),
                unique_id = r.study_unique_id_name.orEmpty(),
                primary_id = r.study_primary_id_name.orEmpty(),
                secondary_id = r.study_secondary_id_name.orEmpty(),
                date_import = r.date_import ?: "",
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
        } ?: FieldObject()
    }
}
