package com.fieldbook.shared.utilities

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.fieldbook.shared.AppContext
import com.fieldbook.shared.database.models.TraitObject
import com.fieldbook.shared.database.repository.StudyRepository
import com.fieldbook.shared.database.repository.TraitRepository
import com.fieldbook.shared.export.ExportOptions
import com.fieldbook.shared.export.ExportResult
import com.fieldbook.shared.preferences.GeneralKeys
import com.fieldbook.shared.sqldelight.createDatabase
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class ExportUtil : CoroutineScope by MainScope() {
    private val studyRepo = StudyRepository()
    private val traitRepo = TraitRepository()
    private val db = createDatabase()
    private val driver: SqlDriver = AppContext.driverFactory().getDriver()
    private val prefs = Settings()

    private var fieldIds: List<Int> = emptyList()
    private var multipleFields = false

    private data class PendingExport(
        val generatedFiles: MutableList<DocumentFile> = mutableListOf(),
        val tempDirectories: MutableList<DocumentFile> = mutableListOf()
    )

    private fun timestampString(): String {
        val local = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val hour12 = when (val hour = local.hour % 12) {
            0 -> 12
            else -> hour
        }
        fun Int.pad(width: Int): String = toString().padStart(width, '0')

        return buildString {
            append(local.year.pad(4))
            append('-')
            append(local.monthNumber.pad(2))
            append('-')
            append(local.dayOfMonth.pad(2))
            append('-')
            append(hour12.pad(2))
            append('-')
            append(local.minute.pad(2))
            append('-')
            append(local.second.pad(2))
        }
    }

    fun defaultTimestampString(): String = timestampString()

    private fun activeFieldId(): Int = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID.key, -1)
    private fun resolveFieldIds(fieldIds: List<Int>): List<Int> =
        fieldIds.takeIf { it.isNotEmpty() } ?: listOf(activeFieldId())

    fun exportMultipleFields(fieldIds: List<Int>) {
        this.fieldIds = fieldIds
        this.multipleFields = fieldIds.size > 1
        export()
    }

    fun exportActiveField() {
        this.fieldIds = listOf(activeFieldId())
        this.multipleFields = false
        export()
    }

    private fun export() {
        exportPermissionAndRun()
    }

    private fun exportPermissionAndRun() {
        val resolvedFieldIds = resolveFieldIds(fieldIds)
        this.fieldIds = resolvedFieldIds
        this.multipleFields = resolvedFieldIds.size > 1
        val defaultOptions = ExportOptions(
            formatDb = prefs.getBoolean(GeneralKeys.EXPORT_FORMAT_DATABASE.key, true),
            formatTable = prefs.getBoolean(GeneralKeys.EXPORT_FORMAT_TABLE.key, true),
            onlyUnique = prefs.getBoolean(GeneralKeys.EXPORT_COLUMNS_UNIQUE.key, true),
            allColumns = prefs.getBoolean(GeneralKeys.EXPORT_COLUMNS_ALL.key, false),
            activeTraits = prefs.getBoolean(GeneralKeys.EXPORT_TRAITS_ACTIVE.key, true),
            allTraits = prefs.getBoolean(GeneralKeys.EXPORT_TRAITS_ALL.key, false),
            bundleMedia = prefs.getBoolean(GeneralKeys.DIALOG_EXPORT_BUNDLE_CHECKED.key, false),
            overwrite = prefs.getBoolean(GeneralKeys.EXPORT_OVERWRITE.key, false),
            fileName = defaultExportFileName(resolvedFieldIds),
            multipleFields = multipleFields
        )
        startExportTasks(defaultOptions)
    }

    fun exportLocalWithOptions(
        fieldIds: List<Int>,
        options: ExportOptions,
        onComplete: (ExportResult) -> Unit
    ) {
        val resolvedFieldIds = resolveFieldIds(fieldIds)
        this.fieldIds = resolvedFieldIds
        this.multipleFields = resolvedFieldIds.size > 1
        persistOptions(options)
        startExportTasks(options, onComplete)
    }

    private fun persistOptions(options: ExportOptions) {
        prefs.putBoolean(GeneralKeys.EXPORT_FORMAT_DATABASE.key, options.formatDb)
        prefs.putBoolean(GeneralKeys.EXPORT_FORMAT_TABLE.key, options.formatTable)
        prefs.putBoolean(GeneralKeys.EXPORT_COLUMNS_UNIQUE.key, options.onlyUnique)
        prefs.putBoolean(GeneralKeys.EXPORT_COLUMNS_ALL.key, options.allColumns)
        prefs.putBoolean(GeneralKeys.EXPORT_TRAITS_ACTIVE.key, options.activeTraits)
        prefs.putBoolean(GeneralKeys.EXPORT_TRAITS_ALL.key, options.allTraits)
        prefs.putBoolean(GeneralKeys.DIALOG_EXPORT_BUNDLE_CHECKED.key, options.bundleMedia)
        prefs.putBoolean(GeneralKeys.EXPORT_OVERWRITE.key, options.overwrite)
    }

    private fun startExportTasks(
        options: ExportOptions,
        onComplete: ((ExportResult) -> Unit)? = null
    ) {
        launch {
            val pendingExport = PendingExport()
            val results = withContext(Dispatchers.Default) {
                fieldIds.map { fieldId -> runExportForField(fieldId, options, pendingExport) }
            }

            val finalResult = when {
                results.any { it is ExportResult.Failure } -> {
                    cleanupGeneratedFiles(pendingExport)
                    results.first { it is ExportResult.Failure }
                }
                pendingExport.generatedFiles.isEmpty() -> {
                    cleanupGeneratedFiles(pendingExport)
                    ExportResult.NoData
                }
                else -> finalizeExport(options, pendingExport)
            }
            onComplete?.invoke(finalResult)
        }
    }

    private fun runExportForField(
        fieldId: Int,
        options: ExportOptions,
        pendingExport: PendingExport
    ): ExportResult {
        return try {
            val field = studyRepo.getById(fieldId)
            val exportFileBaseName = if (multipleFields) {
                "${timestampString()}_${field.exp_name}"
            } else {
                options.fileName
            }

            val exportTraits = resolveTraits(options)
            var hasExportedData = false

            if (options.formatDb) {
                createDatabaseExportFile(
                    fieldId = fieldId,
                    uniqueName = field.unique_id,
                    exportTraits = exportTraits,
                    fileName = "${exportFileBaseName}_database.csv",
                    onlyUnique = options.onlyUnique
                )?.let {
                    pendingExport.generatedFiles.add(it)
                    hasExportedData = true
                }
            }

            if (options.formatTable) {
                createTableExportFile(
                    fieldId = fieldId,
                    uniqueName = field.unique_id,
                    exportTraits = exportTraits,
                    fileName = "${exportFileBaseName}_table.csv",
                    onlyUnique = options.onlyUnique
                )?.let {
                    pendingExport.generatedFiles.add(it)
                    hasExportedData = true
                }
            }

            if (options.bundleMedia) {
                DocumentTreeUtil.getStudyMediaDirectory(field.exp_name)?.let { studyMediaDir ->
                    val tempDir = createDir("dir_field_export", "temp_export_${timestampString()}")
                    val studyDir = tempDir?.createDirectory(field.exp_name)

                    if (studyDir != null) {
                        val allowedTraitDirs = exportTraits.map { sanitizeFileName(it.name) }.toSet()
                        listFiles(studyMediaDir).forEach { traitDir ->
                            val traitDirName = traitDir.name() ?: return@forEach
                            if (allowedTraitDirs.contains(traitDirName)) {
                                val copiedTraitDir = studyDir.createDirectory(traitDirName) ?: return@forEach
                                copyDirectoryContents(traitDir, copiedTraitDir)
                            }
                        }
                        if (listFiles(studyDir).isNotEmpty()) {
                            pendingExport.generatedFiles.add(studyDir)
                            pendingExport.tempDirectories.add(tempDir)
                            hasExportedData = true
                        } else {
                            tempDir.let(::deleteFile)
                        }
                    }
                }
            }

            if (!hasExportedData) {
                return ExportResult.NoData
            }

            studyRepo.updateExportDate(fieldId, Clock.System.now().toString())
            ExportResult.Success("Export successful for field ${field.exp_name}")
        } catch (e: Exception) {
            ExportResult.Failure(e)
        }
    }

    private fun finalizeExport(options: ExportOptions, pendingExport: PendingExport): ExportResult {
        val finalFile = try {
            val generatedFiles = pendingExport.generatedFiles.toList()
            val file = if (generatedFiles.size > 1) {
                zipFiles(generatedFiles, options.fileName)
            } else {
                generatedFiles.firstOrNull()
            } ?: return ExportResult.Failure(IllegalStateException("Failed to create export file"))

            if (generatedFiles.size > 1) {
                cleanupGeneratedFiles(pendingExport)
            }

            if (options.overwrite) {
                archivePreviousExports(file)
            }

            shareFile(file)
            file
        } catch (e: Exception) {
            cleanupGeneratedFiles(pendingExport)
            return ExportResult.Failure(e)
        }

        return ExportResult.Success("Export completed: ${finalFile.name().orEmpty()}")
    }

    private fun createCsvExportFile(fileName: String, header: List<String>, rows: List<List<String?>>): DocumentFile? {
        if (rows.isEmpty()) return null
        val exportDir = getExportDirectory() ?: return null
        val file = exportDir.createFile("text/csv", fileName) ?: return null
        val contents = buildString {
            appendCsvRow(header)
            rows.forEach { appendCsvRow(it) }
        }
        file.writeBytes(contents.encodeToByteArray())
        return file
    }

    private fun createDatabaseExportFile(
        fieldId: Int,
        uniqueName: String,
        exportTraits: List<TraitObject>,
        fileName: String,
        onlyUnique: Boolean
    ): DocumentFile? {
        val allColumns = getObservationUnitAttributeNames(fieldId)
        val outputColumns = if (onlyUnique) listOf(uniqueName) else allColumns
        val traitRequiredFields = listOf("trait", "value", "timestamp", "person", "location", "number", "device_name")
        val selectedTraits = exportTraits.map { it.name }
        val rows = queryDatabaseExportRows(fieldId, allColumns, selectedTraits).map { row ->
            outputColumns.map { column -> row[column] } +
                listOf(
                    row["trait"],
                    row["value"],
                    row["timestamp"],
                    row["person"],
                    row["location"],
                    row["number"],
                    exportDeviceName()
                )
        }
        return createCsvExportFile(fileName, outputColumns + traitRequiredFields, rows)
    }

    private fun createTableExportFile(
        fieldId: Int,
        uniqueName: String,
        exportTraits: List<TraitObject>,
        fileName: String,
        onlyUnique: Boolean
    ): DocumentFile? {
        val attributeColumns = getObservationUnitAttributeNames(fieldId)
        val traitNames = exportTraits.map { it.name }
        val outputColumns = if (onlyUnique) listOf(uniqueName) + traitNames else attributeColumns + traitNames
        val traitFormats = exportTraits.associateBy { it.name }
        val shortTableLeadColumn = attributeColumns.firstOrNull().orEmpty()
        val rows = queryTableExportRows(fieldId, attributeColumns, traitNames).map { row ->
            outputColumns.mapIndexed { index, column ->
                val rawValue = when {
                    onlyUnique && index == 0 -> row[shortTableLeadColumn]
                    else -> row[column]
                }
                val trait = traitFormats[column]
                if (trait != null) flattenTraitValue(trait, rawValue) else rawValue
            }
        }
        return createCsvExportFile(fileName, outputColumns, rows)
    }

    private fun resolveTraits(options: ExportOptions): List<TraitObject> {
        val allTraits = traitRepo.getAllTraitsOrdered()
        return when {
            options.allTraits -> allTraits
            options.activeTraits -> allTraits.filter { it.visible == "true" }
            else -> emptyList()
        }
    }

    private fun copyDirectoryContents(sourceDir: DocumentFile, destinationDir: DocumentFile) {
        listFiles(sourceDir).forEach { source ->
            val name = source.name() ?: return@forEach
            if (name == ".nomedia") return@forEach
            if (source.isDirectory()) {
                val childDir = destinationDir.createDirectory(name) ?: return@forEach
                copyDirectoryContents(source, childDir)
            } else {
                copyFileToDirectory(source, destinationDir, name)
            }
        }
    }

    private fun archivePreviousExports(newFile: DocumentFile) {
        val exportDir = getExportDirectory() ?: return
        val archiveDir = getArchiveDirectory() ?: return
        val newFileName = newFile.name() ?: return
        val truncatedNewFileName = newFileName.substringAfter("_", newFileName)

        listFiles(exportDir).forEach { existingFile ->
            val existingName = existingFile.name() ?: return@forEach
            val truncatedExistingName = existingName.substringAfter("_", existingName)
            if (existingName != newFileName && truncatedExistingName == truncatedNewFileName) {
                if (copyFileToDirectory(existingFile, archiveDir, existingName) != null) {
                    deleteFile(existingFile)
                }
            }
        }
    }

    private fun defaultExportFileName(fieldIds: List<Int>): String {
        val suffix = if (fieldIds.size > 1) {
            "multiple_fields"
        } else {
            val fieldName = studyRepo.getById(fieldIds.first()).exp_name
            fieldName.removeSuffix(".csv").ifBlank { "export" }
        }
        return "${timestampString()}_$suffix"
    }

    private fun getObservationUnitAttributeNames(fieldId: Int): List<String> =
        db.observation_units_attributesQueries.getAllNamesByStudyId(fieldId.toLong())
            .executeAsList()
            .filter { it != "geo_coordinates" }

    private fun queryDatabaseExportRows(
        fieldId: Int,
        attributeColumns: List<String>,
        traitNames: List<String>
    ): List<Map<String, String?>> {
        if (traitNames.isEmpty()) return emptyList()

        val unitSelectAttributes = attributeColumns.joinToString(", ") { attributeName ->
            "MAX(CASE WHEN attr.observation_unit_attribute_name = ${sqlString(attributeName)} THEN vals.observation_unit_value_name ELSE NULL END) AS ${sqlIdentifier(attributeName)}"
        }
        val observationColumns = listOf(
            "observation_variable_name",
            "observation_variable_field_book_format",
            "value",
            "observation_time_stamp",
            "collector",
            "geoCoordinates",
            "rep"
        )
        val selectClause = buildList {
            if (unitSelectAttributes.isNotEmpty()) add(unitSelectAttributes)
            addAll(observationColumns.map { "obs.${sqlIdentifier(it)} AS ${sqlIdentifier(it)}" })
        }.joinToString(", ")
        val placeholders = List(traitNames.size) { "?" }.joinToString(", ")
        val query = """
            SELECT $selectClause
            FROM observations AS obs
            LEFT JOIN observation_units AS units ON units.observation_unit_db_id = obs.observation_unit_id
            LEFT JOIN observation_units_values AS vals ON units.internal_id_observation_unit = vals.observation_unit_id
            LEFT JOIN observation_units_attributes AS attr ON vals.observation_unit_attribute_db_id = attr.internal_id_observation_unit_attribute
            WHERE obs.study_id = ?
              AND obs.observation_variable_name IN ($placeholders)
            GROUP BY obs.internal_id_observation
            ${getSortOrderClause(fieldId)}
        """.trimIndent()
        val rowOrder = attributeColumns + observationColumns
        return executeQueryRows(query, traitNames.size + 1, rowOrder.size) {
            bindLong(0, fieldId.toLong())
            traitNames.forEachIndexed { index, traitName ->
                bindString(index + 1, traitName)
            }
        }.map { values ->
            val row = rowOrder.zip(values).toMap().toMutableMap()
            row["trait"] = row["observation_variable_name"]
            row["value"] = CategoryJsonUtil.processValue(
                mapOf(
                    "observation_variable_field_book_format" to row["observation_variable_field_book_format"],
                    "value" to row["value"]
                )
            )
            row["timestamp"] = row["observation_time_stamp"]
            row["person"] = row["collector"]
            row["location"] = row["geoCoordinates"]
            row["number"] = row["rep"]
            row
        }
    }

    private fun queryTableExportRows(
        fieldId: Int,
        attributeColumns: List<String>,
        traitNames: List<String>
    ): List<Map<String, String?>> {
        val selectAttributes = attributeColumns.joinToString(", ") { attributeName ->
            "MAX(CASE WHEN attr.observation_unit_attribute_name = ${sqlString(attributeName)} THEN vals.observation_unit_value_name ELSE NULL END) AS ${sqlIdentifier(attributeName)}"
        }
        val selectObservations = traitNames.joinToString(", ") { traitName ->
            "MAX(CASE WHEN obs.observation_variable_name = ${sqlString(traitName)} THEN obs.value ELSE NULL END) AS ${sqlIdentifier(traitName)}"
        }
        val combinedSelection = listOf(selectAttributes, selectObservations).filter { it.isNotBlank() }.joinToString(", ")
        if (combinedSelection.isBlank()) return emptyList()

        val query = """
            SELECT $combinedSelection
            FROM observation_units AS units
            LEFT JOIN observation_units_values AS vals ON units.internal_id_observation_unit = vals.observation_unit_id
            LEFT JOIN observation_units_attributes AS attr ON vals.observation_unit_attribute_db_id = attr.internal_id_observation_unit_attribute
            LEFT JOIN observations AS obs ON units.observation_unit_db_id = obs.observation_unit_id AND obs.study_id = $fieldId
            WHERE units.study_id = $fieldId
            GROUP BY units.internal_id_observation_unit
            ${getSortOrderClause(fieldId)}
        """.trimIndent()
        val rowOrder = attributeColumns + traitNames
        return executeQueryRows(query, 0, rowOrder.size).map { values ->
            rowOrder.zip(values).toMap()
        }
    }

    private fun executeQueryRows(
        sql: String,
        parameterCount: Int,
        columnCount: Int,
        binder: app.cash.sqldelight.db.SqlPreparedStatement.() -> Unit = {}
    ): List<List<String?>> {
        val result: QueryResult<List<List<String?>>> = driver.executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor ->
                val rows = mutableListOf<List<String?>>()
                while (cursor.next().value) {
                    rows += List(columnCount) { index -> cursor.getString(index) }
                }
                QueryResult.Value(rows)
            },
            parameters = parameterCount,
            binders = binder
        )
        return result.value
    }

    private fun getSortOrderClause(studyId: Int): String {
        val sortOrder = if (prefs.getString("${GeneralKeys.SORT_ORDER.key}.$studyId", "ASC") == "ASC") {
            "ASC"
        } else {
            "DESC"
        }
        val sortName = db.studiesQueries.getSortNameById(studyId.toLong()).executeAsOneOrNull()?.study_sort_name
        val sortCols = sortName
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.split(',')
            ?.joinToString(",") { col ->
                val trimmed = col.trim()
                "cast(${sqlIdentifier(trimmed)} as integer), ${sqlIdentifier(trimmed)}"
            }
            .orEmpty()
        return if (sortCols.isNotEmpty()) "ORDER BY $sortCols COLLATE NOCASE $sortOrder" else ""
    }

    private fun flattenTraitValue(trait: TraitObject, rawValue: String?): String? {
        if (rawValue == null) return null
        return when (trait.format) {
            "categorical", "multicat", "qualitative" -> {
                try {
                    CategoryJsonUtil.flattenMultiCategoryValue(CategoryJsonUtil.decode(rawValue), false)
                } catch (_: Exception) {
                    rawValue
                }
            }
            else -> rawValue
        }
    }

    private fun cleanupGeneratedFiles(pendingExport: PendingExport) {
        pendingExport.generatedFiles
            .distinctBy { it.uri() }
            .forEach(::deleteFile)
        pendingExport.tempDirectories
            .distinctBy { it.uri() }
            .forEach(::deleteFile)
    }

    private fun StringBuilder.appendCsvRow(values: List<Any?>) {
        append(values.joinToString(",") { value ->
            val text = value?.toString().orEmpty().replace("\"", "\"\"")
            "\"$text\""
        })
        append('\n')
    }

    private fun sqlIdentifier(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private fun sqlString(value: String): String = "'${value.replace("'", "''")}'"
}
