package com.fieldbook.shared.screens.datagrid

import com.fieldbook.shared.database.repository.ObservationUnitAttributeRepository
import com.fieldbook.shared.database.repository.TraitRepository
import com.fieldbook.shared.preferences.GeneralKeys
import com.fieldbook.shared.preferences.PreferenceKeys
import com.fieldbook.shared.sqldelight.FieldbookDatabase
import com.fieldbook.shared.sqldelight.createDatabase
import com.fieldbook.shared.utilities.CategoryJsonUtil
import com.russhwolf.settings.Settings

class DataGridRepository(
    private val settings: Settings = Settings(),
    private val traitRepository: TraitRepository = TraitRepository(),
    private val attributeRepository: ObservationUnitAttributeRepository = ObservationUnitAttributeRepository(),
    private val dbProvider: () -> FieldbookDatabase = { createDatabase() },
) {
    private val db: FieldbookDatabase
        get() = dbProvider()

    fun load(activeCell: DataGridCellKey? = null): DataGridState {
        val studyId = settings.getInt(GeneralKeys.SELECTED_FIELD_ID.key, 0).toLong()
        if (studyId <= 0L) {
            return DataGridState(loading = false, error = "No active field selected.")
        }

        val uniqueHeader = settings.getString(GeneralKeys.UNIQUE_NAME.key, "")
        val availableHeaders = attributeRepository.getAllNames(studyId)
        val savedHeader = settings.getString(GeneralKeys.DATAGRID_PREFIX_TRAIT.key, uniqueHeader)
        val rowHeader = when {
            savedHeader in availableHeaders -> savedHeader
            uniqueHeader in availableHeaders -> uniqueHeader
            else -> availableHeaders.firstOrNull().orEmpty()
        }

        if (rowHeader.isBlank()) {
            return DataGridState(
                loading = false,
                availableRowHeaders = availableHeaders,
                error = "No row header is available for the active field."
            )
        }

        val traits = traitRepository.getVisibleTraitsWithAttributes()
            .mapNotNull { trait ->
                trait.id?.let { id ->
                    DataGridTrait(
                        id = id,
                        name = trait.name,
                        format = trait.format,
                    )
                }
            }

        val observations = db.observationsQueries.getDataGridObservations(studyId)
            .executeAsList()
            .associateBy { row ->
                ObservationKey(
                    plotId = row.observation_unit_id,
                    traitId = row.observation_variable_db_id
                )
            }

        val showCategoryLabels = settings.getString(PreferenceKeys.LABELVAL_CUSTOMIZE, "value") != "value"
        val rows = db.observation_unitsQueries.selectDataGridUnits(rowHeader, studyId)
            .executeAsList()
            .map { unit ->
                val plotId = unit.observation_unit_db_id.orEmpty()
                DataGridRow(
                    plotId = plotId,
                    header = unit.header_value ?: plotId,
                    cells = traits.map { trait ->
                        val observation = observations[ObservationKey(plotId, trait.id)]
                        val rawValue = observation?.cell_value.orEmpty()
                        val repeated = (observation?.repeated_count ?: 0L) > 1L
                        DataGridCell(
                            value = if (repeated) "..." else rawValue.formatCellValue(trait, showCategoryLabels),
                            repeated = repeated,
                        )
                    }
                )
            }

        return DataGridState(
            loading = false,
            rowHeaderName = rowHeader,
            availableRowHeaders = availableHeaders,
            traits = traits,
            rows = rows,
            activeCell = activeCell,
        )
    }

    fun setRowHeader(rowHeader: String) {
        settings.putString(GeneralKeys.DATAGRID_PREFIX_TRAIT.key, rowHeader)
    }

    private fun String.formatCellValue(trait: DataGridTrait, showCategoryLabels: Boolean): String {
        if (isBlank()) return ""

        return if (trait.format?.lowercase() in categoryFormats) {
            try {
                CategoryJsonUtil.flattenMultiCategoryValue(
                    CategoryJsonUtil.decode(this),
                    showLabel = showCategoryLabels
                ).ifBlank { this }
            } catch (_: Exception) {
                this
            }
        } else {
            this
        }
    }

    private data class ObservationKey(
        val plotId: String,
        val traitId: Long,
    )

    private companion object {
        val categoryFormats = setOf("categorical", "multicat", "qualitative")
    }
}
