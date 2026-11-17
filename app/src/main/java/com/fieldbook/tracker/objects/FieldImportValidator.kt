package com.fieldbook.tracker.objects

import com.fieldbook.tracker.database.DataHelper
import java.io.IOException

/**
 * A reason a field file cannot be imported.
 * Row numbers count the header as row 1, matching the row numbers a spreadsheet shows.
 */
sealed class FieldImportError {

    /** The unique identifier column chosen in the import dialog is not in the file's header. */
    data class UniqueColumnNotFound(val column: String) : FieldImportError()

    /** The file has a header but no rows with data. */
    object NoData : FieldImportError()

    /** A unique identifier contains a character that is not allowed in one. */
    data class IllegalCharacter(val value: String, val row: Int, val character: Char) : FieldImportError()

    /** A unique identifier appears on more than one row. */
    data class DuplicateUniqueId(val value: String, val rows: List<Int>) : FieldImportError()

    /** Rows that have data but no unique identifier. */
    data class MissingUniqueIds(val column: String, val rows: List<Int>) : FieldImportError()

    /** Unique identifiers from the file that an existing field already uses. */
    data class UniqueIdsExist(val field: String, val values: List<String>) : FieldImportError()
}

/**
 * Checks a field file before anything is written to the database, and lines each row up with the columns that will be stored.
 */
object FieldImportValidator {

    /** Unique identifiers are used to build file names, so path separators are not allowed in them. */
    private val ILLEGAL_UNIQUE_ID_CHARACTERS = charArrayOf('/', '\\')

    /** A row to import, with one value per entry in [Result.columns]. */
    class Row(val number: Int, val values: List<String>)

    class Result(
        /** Header names with special characters removed, blank names dropped, and only the first of a repeated name kept. */
        val columns: List<String>,
        /** Position of the unique identifier in [columns], or -1 when it is missing. */
        val uniqueIndex: Int,
        /** Rows to store; rows with every cell blank are left out. */
        val rows: List<Row>,
        /** True when a repeated column name was dropped. */
        val hasDuplicateColumns: Boolean,
        /** Problems that block the import; empty when the file can be imported. */
        val errors: List<FieldImportError>,
    ) {
        val uniqueIds: List<String> get() = rows.map { it.values[uniqueIndex] }
    }

    /**
     * Reads every row of [file], which must already have loaded its columns, and validates them.
     * @throws IOException if the file could not be read to the end
     */
    @JvmStatic
    @Throws(IOException::class)
    fun read(file: FieldFileObject.FieldFileBase, uniqueName: String): Result {
        file.open()
        try {
            val header = file.readNext() ?: emptyArray()
            val result = validate(header, generateSequence { file.readNext() }.asIterable(), uniqueName)
            if (file.openFailed) throw IOException("Field file could not be read")
            return result
        } finally {
            file.close()
        }
    }

    @JvmStatic
    fun validate(header: Array<out String?>, rows: Iterable<Array<out String?>>, uniqueName: String): Result {

        val columns = ArrayList<String>()
        val sourceIndices = ArrayList<Int>()
        var hasDuplicateColumns = false

        header.forEachIndexed { i, raw ->
            val name = DataHelper.replaceSpecialChars(raw ?: "")
            when {
                name.isEmpty() -> Unit
                name in columns -> hasDuplicateColumns = true
                else -> {
                    columns += name
                    sourceIndices += i
                }
            }
        }

        val uniqueIndex = columns.indexOf(uniqueName)
        if (uniqueIndex < 0) {
            return Result(columns, uniqueIndex, emptyList(), hasDuplicateColumns,
                listOf(FieldImportError.UniqueColumnNotFound(uniqueName)))
        }

        val accepted = ArrayList<Row>()
        val errors = ArrayList<FieldImportError>()
        val firstRowById = HashMap<String, Int>()
        val duplicateRowsById = LinkedHashMap<String, MutableList<Int>>()
        val missingIdRows = ArrayList<Int>()

        var rowNumber = 1 // the header
        for (raw in rows) {
            rowNumber++

            // short rows are padded so every value lines up with its column
            val values = sourceIndices.map { raw.getOrNull(it) ?: "" }
            if (values.all { it.isBlank() }) continue

            val id = values[uniqueIndex]
            if (id.isBlank()) {
                missingIdRows += rowNumber
                continue
            }

            id.firstOrNull { it in ILLEGAL_UNIQUE_ID_CHARACTERS }?.let {
                errors += FieldImportError.IllegalCharacter(id, rowNumber, it)
            }

            val firstRow = firstRowById[id]
            if (firstRow == null) {
                firstRowById[id] = rowNumber
            } else {
                duplicateRowsById.getOrPut(id) { mutableListOf(firstRow) } += rowNumber
            }

            accepted += Row(rowNumber, values)
        }

        duplicateRowsById.forEach { (id, numbers) -> errors += FieldImportError.DuplicateUniqueId(id, numbers) }
        if (missingIdRows.isNotEmpty()) errors += FieldImportError.MissingUniqueIds(uniqueName, missingIdRows)
        if (errors.isEmpty() && accepted.isEmpty()) errors += FieldImportError.NoData

        return Result(columns, uniqueIndex, accepted, hasDuplicateColumns, errors)
    }
}
