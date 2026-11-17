package com.fieldbook.tracker.objects

import com.fieldbook.tracker.objects.FieldImportError.DuplicateUniqueId
import com.fieldbook.tracker.objects.FieldImportError.IllegalCharacter
import com.fieldbook.tracker.objects.FieldImportError.MissingUniqueIds
import com.fieldbook.tracker.objects.FieldImportError.NoData
import com.fieldbook.tracker.objects.FieldImportError.UniqueColumnNotFound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Runs the example field files in src/test/resources/field_import through the same readers and validator the field import uses.
 * Each file is a copy of valid_import.csv with one problem added, and uses plot_id as its unique identifier unless a test says otherwise.
 * Row numbers count the header as row 1, matching what a spreadsheet shows.
 */
class FieldImportValidatorTest {

    companion object {
        private val VALID_COLUMNS = listOf("plot_id", "plot", "replicate")
        private val NO_ERRORS = emptyList<FieldImportError>()
    }

    private fun fieldFile(name: String): FieldFileObject.FieldFileBase {
        val bytes = requireNotNull(javaClass.classLoader?.getResourceAsStream("field_import/$name")) {
            "Missing test file field_import/$name"
        }.use { it.readBytes() }

        return when (FieldFileObject.getExtension(name)) {
            "csv" -> object : FieldFileObject.FieldFileCSV(null, null) {
                override fun getInputStream(): InputStream = ByteArrayInputStream(bytes)
            }
            "xlsx" -> object : FieldFileObject.FieldFileXlsx(null, null) {
                override fun getInputStream(): InputStream = ByteArrayInputStream(bytes)
            }
            else -> error("No reader for test file $name")
        }
    }

    /** Loads the header first, as the field editor does before it shows the import dialog. */
    private fun import(name: String, unique: String = "plot_id"): FieldImportValidator.Result {
        val file = fieldFile(name)
        assertTrue("$name has no header", file.columns.isNotEmpty())
        return FieldImportValidator.read(file, unique)
    }

    private fun assertRow(result: FieldImportValidator.Result, number: Int, vararg values: String) {
        assertEquals("row $number", values.toList(), result.rows.single { it.number == number }.values)
    }

    @Test
    fun validFileImportsEveryRow() {
        val result = import("valid_import.csv")

        assertEquals(NO_ERRORS, result.errors)
        assertEquals(VALID_COLUMNS, result.columns)
        assertFalse(result.hasDuplicateColumns)
        assertEquals(50, result.rows.size)
        assertRow(result, 2, "25TEST010_0101", "101", "1")
        assertRow(result, 51, "25TEST010_1001", "1001", "5")
    }

    @Test
    fun rowWithoutUniqueIdBlocksImportAndIsNamed() {
        assertEquals(listOf(MissingUniqueIds("plot_id", listOf(20))), import("missing_unique_id.csv").errors)
    }

    @Test
    fun duplicateUniqueIdNamesEveryRowItIsOn() {
        assertEquals(listOf(DuplicateUniqueId("25TEST010_0101", listOf(2, 11))), import("duplicate_unique_id.csv").errors)
    }

    @Test
    fun pathSeparatorInUniqueIdIsRejected() {
        assertEquals(listOf(IllegalCharacter("25TEST010/0201", 11, '/')), import("disallowed_character_in_id.csv").errors)
    }

    @Test
    fun specialCharactersAreRemovedFromColumnNames() {
        val result = import("disallowed_character_in_header.csv")

        assertEquals(NO_ERRORS, result.errors)
        assertEquals(VALID_COLUMNS, result.columns)
    }

    @Test
    fun repeatedColumnNameKeepsOnlyTheFirstColumn() {
        val result = import("duplicate_column_name.csv")

        assertEquals(NO_ERRORS, result.errors)
        assertTrue(result.hasDuplicateColumns)
        assertEquals(listOf("plot_id", "replicate"), result.columns)
        assertRow(result, 2, "25TEST010_0101", "1")
    }

    @Test
    fun columnsWithoutNamesAreDropped() {
        val result = import("empty_column_name.csv")

        assertEquals(NO_ERRORS, result.errors)
        assertEquals(VALID_COLUMNS, result.columns)
        assertRow(result, 2, "25TEST010_0101", "101", "1")
    }

    /**
     * The import dialog lists only named columns, so its position for replicate (2) is the unnamed third column in the file.
     * Finding the column by name checks replicate itself, where "1" is on rows 2 to 11 rather than rows 2 to 6.
     */
    @Test
    fun uniqueColumnIsFoundByNameNotDialogPosition() {
        val errors = import("empty_column_name.csv", unique = "replicate").errors

        assertEquals(DuplicateUniqueId("1", (2..11).toList()), errors.first())
        assertEquals(5, errors.size)
    }

    @Test
    fun emptyCellsImportAsEmptyValues() {
        val result = import("empty_column_data.csv")

        assertEquals(NO_ERRORS, result.errors)
        assertEquals(listOf("plot_id", "plot", "row", "col", "replicate"), result.columns)
        assertRow(result, 2, "25TEST010_0101", "101", "", "", "1")
    }

    @Test
    fun blankRowsAreSkippedAndRowsWithDataButNoIdAreReported() {
        val result = import("missing_rows_and_cells.csv")

        assertEquals(listOf(MissingUniqueIds("plot_id", listOf(21))), result.errors)
        assertTrue(result.rows.none { it.number in 8..12 || it.number in 35..37 })
        assertRow(result, 29, "25TEST010_0603", "", "")
    }

    /** The ids are CONCAT formulas, which POI 3.12 cannot evaluate, so the values the spreadsheet saved are used. */
    @Test
    fun formulaCellsUseTheirCalculatedValues() {
        val result = import("unique_id_formula.xlsx")

        assertEquals(NO_ERRORS, result.errors)
        assertEquals(listOf("plot_id", "plot", "replicate", "field"), result.columns)
        assertRow(result, 2, "25TEST010_0101", "101", "1", "25TEST010")
        assertRow(result, 51, "25TEST010_1001", "1001", "5", "25TEST010")
    }

    /** The file merges A20:B20; only the first cell of a merged range holds its value, so plot reads as blank on row 20. */
    @Test
    fun mergedCellsKeepTheirValueInTheFirstCellOnly() {
        val result = import("merged_cells.xlsx")

        assertEquals(NO_ERRORS, result.errors)
        assertEquals(50, result.rows.size)
        assertRow(result, 20, "25TEST010_0402", "", "2")
    }

    @Test
    fun uniqueColumnMissingFromHeaderIsReported() {
        val result = FieldImportValidator.validate(arrayOf("plot", "replicate"), listOf(arrayOf("101", "1")), "plot_id")

        assertEquals(listOf(UniqueColumnNotFound("plot_id")), result.errors)
    }

    @Test
    fun fileWithOnlyBlankRowsHasNoData() {
        val result = FieldImportValidator.validate(arrayOf("plot_id"), listOf(arrayOf(""), emptyArray()), "plot_id")

        assertEquals(listOf(NoData), result.errors)
    }

    @Test
    fun shortRowsArePaddedToTheHeader() {
        val result = FieldImportValidator.validate(arrayOf("plot_id", "plot", "replicate"), listOf(arrayOf("a")), "plot_id")

        assertEquals(listOf("a", "", ""), result.rows.single().values)
    }

    @Test
    fun everyProblemInTheFileIsReported() {
        val rows = listOf(
            arrayOf("a\\1", "1"),
            arrayOf("b", "2"),
            arrayOf("", "3"),
            arrayOf("b", "4"),
            arrayOf(" ", "5"),
        )

        val errors = FieldImportValidator.validate(arrayOf("plot_id", "plot"), rows, "plot_id").errors

        assertEquals(listOf(
            IllegalCharacter("a\\1", 2, '\\'),
            DuplicateUniqueId("b", listOf(3, 5)),
            MissingUniqueIds("plot_id", listOf(4, 6)),
        ), errors)
    }
}
