package com.fieldbook.shared.screens.fields

import com.fieldbook.shared.database.models.FieldObject
import com.fieldbook.shared.database.repository.StudyRepository
import com.fieldbook.shared.database.utils.internalTimeFormatter
import com.fieldbook.shared.objects.ImportFormat
import com.fieldbook.shared.utilities.CSVUtil
import kotlinx.datetime.Clock
import kotlinx.datetime.format

data class PendingFieldImport(
    val fileName: String,
    val fieldName: String,
    val uniqueColumnOptions: List<String>,
    val distinctColumns: List<IndexedValue<String>>,
    val sanitizedColumnsByIndex: List<String>,
    val rows: List<List<String>>,
    val sanitizedColumns: Boolean,
    val duplicateColumnsSkipped: Boolean
)

data class FieldImportResult(
    val fieldId: Int,
    val importedRowCount: Int
)

object FieldImportSupport {
    private val reservedColumns = setOf("id")

    fun parseCsvImport(fileName: String, bytes: ByteArray): PendingFieldImport {
        if (!fileName.endsWith(".csv", ignoreCase = true)) {
            error("Only CSV files are currently supported in KMP field import")
        }

        val rows = CSVUtil.readRows(bytes)
        if (rows.isEmpty()) {
            error("The selected file is empty")
        }

        val headers = rows.first()
        val sanitizedColumnsByIndex = headers.map(::sanitizeColumnName)

        sanitizedColumnsByIndex.forEach { column ->
            if (column.lowercase() in reservedColumns) {
                error("Column name \"$column\" is reserved")
            }
        }

        val seenColumns = linkedSetOf<String>()
        val distinctColumns = buildList {
            sanitizedColumnsByIndex.forEachIndexed { index, column ->
                if (column.isNotEmpty() && seenColumns.add(column)) {
                    add(IndexedValue(index, column))
                }
            }
        }

        if (distinctColumns.isEmpty()) {
            error("No suitable columns found")
        }

        val sanitizedColumns = headers.indices.any { headers[it] != sanitizedColumnsByIndex[it] }
        val duplicateColumnsSkipped = distinctColumns.size != sanitizedColumnsByIndex
            .count { it.isNotEmpty() }

        return PendingFieldImport(
            fileName = fileName,
            fieldName = fieldNameFromFile(fileName),
            uniqueColumnOptions = distinctColumns.map { it.value },
            distinctColumns = distinctColumns,
            sanitizedColumnsByIndex = sanitizedColumnsByIndex,
            rows = rows.drop(1),
            sanitizedColumns = sanitizedColumns,
            duplicateColumnsSkipped = duplicateColumnsSkipped
        )
    }

    fun importPending(
        pending: PendingFieldImport,
        uniqueColumn: String,
        studyRepository: StudyRepository
    ): FieldImportResult {
        val uniqueColumnIndex = pending.distinctColumns
            .firstOrNull { it.value == uniqueColumn }
            ?.index
            ?: error("Unique column is required")

        val seenUniqueValues = linkedSetOf<String>()
        val importedRows = pending.rows.filter { row ->
            val value = row.getOrNull(uniqueColumnIndex).orEmpty()
            if (value.isBlank()) {
                false
            } else {
                if (value.contains('/') || value.contains('\\')) {
                    error("Unique values cannot contain / or \\")
                }
                if (!seenUniqueValues.add(value)) {
                    error("The selected unique column contains duplicate values")
                }
                true
            }
        }

        if (seenUniqueValues.isEmpty()) {
            error("The selected unique column must contain at least one non-empty unique value")
        }

        if (studyRepository.checkFieldNameAndObsLvl(pending.fieldName, null) != -1) {
            error("A field named \"${pending.fieldName}\" already exists")
        }

        val timestamp = Clock.System.now().format(internalTimeFormatter)
        val distinctColumns = pending.distinctColumns.map { it.value }
        val field = FieldObject().apply {
            exp_name = pending.fieldName
            exp_alias = pending.fieldName
            unique_id = uniqueColumn
            exp_source = pending.fileName
            import_format = ImportFormat.CSV.format
            count = importedRows.size.toString()
        }

        var fieldId = -1
        studyRepository.db.transaction {
            fieldId = studyRepository.createField(field, timestamp)

            importedRows.forEach { row ->
                val rowData = pending.distinctColumns.map { indexed ->
                    row.getOrNull(indexed.index).orEmpty()
                }
                studyRepository.createFieldData(fieldId.toLong(), distinctColumns, rowData)
            }
        }

        return FieldImportResult(fieldId = fieldId, importedRowCount = importedRows.size)
    }

    private fun fieldNameFromFile(fileName: String): String {
        val trimmed = fileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
        val dotIndex = trimmed.lastIndexOf('.')
        val stem = if (dotIndex > 0) trimmed.substring(0, dotIndex) else trimmed
        return stem.ifBlank { "Imported Field" }
    }

    private fun sanitizeColumnName(value: String): String {
        return buildString {
            value.forEach { char ->
                if (char != '[' && char != ']' && char != '`' && char != '"' && char != '\'') {
                    append(char)
                }
            }
        }.trim()
    }
}
