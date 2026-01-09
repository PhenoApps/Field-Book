package com.fieldbook.shared.utilities

import com.fieldbook.shared.database.repository.StudyRepository
import com.fieldbook.shared.database.repository.TraitRepository
import com.fieldbook.shared.export.ExportOptions
import com.fieldbook.shared.export.ExportResult
import com.fieldbook.shared.preferences.GeneralKeys
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExportUtil() : CoroutineScope by MainScope() {
    private val studyRepo = StudyRepository()
    private val traitRepo = TraitRepository()

    private fun timestampString(): String = nowMillis().toString()

    private var fieldIds: List<Int> = listOf()
    private var multipleFields = false

    private val prefs = Settings()

    fun exportMultipleFields(fieldIds: List<Int>) {
        this.fieldIds = fieldIds
        this.multipleFields = fieldIds.size > 1
        export()
    }

    fun exportActiveField() {
        val selected = prefs.getLong(GeneralKeys.SELECTED_FIELD_ID.key, -1).toInt()
        this.fieldIds = listOf(selected)
        export()
    }

    private fun export() {
        // For now, always run local export flow
        exportPermissionAndRun()
    }

    private fun exportPermissionAndRun() {
        val defaultOptions = ExportOptions(
            formatDb = true,
            formatTable = true,
            onlyUnique = true,
            allColumns = false,
            activeTraits = true,
            allTraits = false,
            bundleMedia = prefs.getBoolean(GeneralKeys.EXPORT_OVERWRITE.key, false),
            overwrite = prefs.getBoolean(GeneralKeys.EXPORT_OVERWRITE.key, false),
            fileName = timestampString(),
            multipleFields = multipleFields
        )
        startExportTasks(defaultOptions)
    }

    fun exportLocalWithOptions(fieldIds: List<Int>, options: ExportOptions, onComplete: (ExportResult) -> Unit) {
        this.fieldIds = fieldIds
        this.multipleFields = fieldIds.size > 1
        startExportTasks(options, onComplete)
    }

    private fun startExportTasks(options: ExportOptions, onComplete: ((ExportResult) -> Unit)? = null) {
        launch {
            val results = mutableListOf<ExportResult>()
            withContext(Dispatchers.Default) {
                for (fieldId in fieldIds) {
                    results.add(runExportForField(fieldId, options))
                }
            }
            val final = when {
                results.any { it is ExportResult.Failure } -> results.first { it is ExportResult.Failure }
                results.all { it is ExportResult.NoData } -> ExportResult.NoData
                else -> ExportResult.Success("Export completed")
            }
            onComplete?.invoke(final)
        }
    }

    private fun runExportForField(fieldId: Int, options: ExportOptions): ExportResult {
        try {
            val fo = studyRepo.getById(fieldId)
            val exportFileBaseName = if (multipleFields) "${timestampString()}_${fo.exp_name}" else options.fileName

            val traits = mutableListOf<com.fieldbook.shared.database.models.TraitObject>()
            if (options.activeTraits) {
                traitRepo.getAllTraits().filterTo(traits) { it.visible == "true" }
            }
            if (options.allTraits) {
                traits.addAll(traitRepo.getAllTraits())
            }

            val filesToExport = mutableListOf<DocumentFile>()

            if (options.formatDb) {
                val columns = listOf<String>()
                val fileName = "${exportFileBaseName}_database.csv"
                val exportDir = getExportDirectory()
                val file = exportDir?.createFile("text/csv", fileName)
                if (file != null) {
                    openOutputStream(file)?.let { os ->
                        // simple placeholder write
                        (os as? java.io.OutputStream)?.use { out ->
                            out.write("id,placeholder\n".toByteArray())
                        }
                        filesToExport.add(file)
                    }
                }
            }

            if (options.formatTable) {
                val columns = listOf<String>()
                val fileName = "${exportFileBaseName}_table.csv"
                val exportDir = getExportDirectory()
                val file = exportDir?.createFile("text/csv", fileName)
                if (file != null) {
                    openOutputStream(file)?.let { os ->
                        (os as? java.io.OutputStream)?.use { out ->
                            out.write("id,table_placeholder\n".toByteArray())
                        }
                        filesToExport.add(file)
                    }
                }
            }

            if (options.bundleMedia) {
                val mediaDir = DocumentTreeUtil.getFieldMediaDirectory(fo.exp_name)
                mediaDir?.let { dir ->
                    val tmpName = "temp_export_${timestampString()}"
                    val tmpDir = createDir("shared", tmpName)
                    if (tmpDir != null) {
                        listFiles(dir).forEach { f ->
                            if (!f.exists()) return@forEach
                            // copy only files
                            copyFileToDirectory(f, tmpDir, f.findFile(f.uri())?.uri() ?: f.uri())
                        }
                        filesToExport.add(tmpDir)
                    }
                }
            }

            val finalFile = if (filesToExport.size > 1) {
                zipFiles(filesToExport, exportFileBaseName)
            } else filesToExport.firstOrNull()

            finalFile?.let {
                if (options.overwrite) {
                    // archive: copy to archive dir
                    val archived = copyFileToDirectory(it, getExportDirectory() ?: it, it.uri())
                    // delete original
                    deleteFile(it)
                }
                shareFile(it)
            }

            return ExportResult.Success("Export successful for field ${fo.exp_name}")
        } catch (e: Exception) {
            return ExportResult.Failure(e)
        }
    }
}
