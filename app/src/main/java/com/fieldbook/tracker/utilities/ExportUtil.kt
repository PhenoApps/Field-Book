package com.fieldbook.tracker.utilities

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.database.Cursor
import android.os.Build
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.documentfile.provider.DocumentFile
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ConfigActivity
import com.fieldbook.tracker.activities.brapi.BrapiExportActivity
import com.fieldbook.tracker.brapi.BrapiAuthDialog
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import dagger.hilt.android.qualifiers.ActivityContext
import org.phenoapps.utils.BaseDocumentTreeUtil
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * Checks preconditions before collect and export.
 */

class ExportUtil @Inject constructor(@ActivityContext private val context: Context, private val database: DataHelper) : CoroutineScope by MainScope() {
    companion object {
        private const val PERMISSIONS_REQUEST_EXPORT_DATA = 9990
        private const val PERMISSIONS_REQUEST_TRAIT_DATA = 9950
        const val TAG = "ExportUtil"
    }

    private var fieldIds: List<Int> = listOf()
    private var onlyUnique: RadioButton? = null
    private var allColumns: RadioButton? = null
    private var allTraits: RadioButton? = null
    private var activeTraits: RadioButton? = null
    private var exportTrait: ArrayList<TraitObject> = arrayListOf()
    private var checkDbBool = false
    private var checkTableBool = false
    private var exportFileString = ""
    private lateinit var fFile: String
    private var filesToExport: MutableList<DocumentFile> = mutableListOf()
    private var processedFieldCount = 0
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private var progressDialog: ProgressDialog? = null

    private val ep = context.getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, 0)

    fun exportMultipleFields(fieldIds: List<Int>) {
        this.fieldIds = fieldIds
        export()
    }

    fun exportActiveField() {
        val activeFieldId = ep.getInt(GeneralKeys.SELECTED_FIELD_ID, -1)
        this.fieldIds = listOf(activeFieldId)
        export()
    }

    fun export() {
        val exporter = ep.getString(GeneralKeys.EXPORT_SOURCE_DEFAULT, "")

        when (exporter) {
            "local" -> exportPermission()
            "brapi" -> fieldIds.forEach { fieldId -> exportBrAPI(fieldId) }
            else -> {
                if (ep.getBoolean(GeneralKeys.BRAPI_ENABLED, false)) {
                    showExportDialog()
                } else {
                    exportPermission()
                }
            }
        }
    }

    @AfterPermissionGranted(PERMISSIONS_REQUEST_EXPORT_DATA)
    fun exportPermission() {
        val perms = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

        if (EasyPermissions.hasPermissions(context, *perms) || Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            exportLocal(fieldIds)
        } else {
            EasyPermissions.requestPermissions(
                context as Activity,
                context.getString(R.string.permission_rationale_storage_export),
                PERMISSIONS_REQUEST_EXPORT_DATA,
                *perms
            )
        }
    }

    fun showExportDialog() {
        val inflater = LayoutInflater.from(context)
        val layout = inflater.inflate(R.layout.dialog_list_buttonless, null)
        val exportSourceList: ListView = layout.findViewById(R.id.myList)

        val exportArray = arrayOf(
            context.getString(R.string.export_source_local),
            ep.getString(GeneralKeys.BRAPI_DISPLAY_NAME, context.getString(R.string.preferences_brapi_server_test))
        )

        val adapter = ArrayAdapter(context, R.layout.list_item_dialog_list, exportArray)
        exportSourceList.adapter = adapter

        val builder = AlertDialog.Builder(context)
        builder.setTitle(R.string.export_dialog_title)
            .setView(layout)
            .setPositiveButton(context.getString(R.string.dialog_cancel)) { dialog, _ -> dialog.dismiss() }
            .create()
            .show()

        exportSourceList.setOnItemClickListener { _, _, which, _ ->
            when (which) {
                0 -> exportPermission()
                1 -> fieldIds.forEach { fieldId -> exportBrAPI(fieldId) }
            }
        }
    }

    fun exportBrAPI(fieldId: Int) {
//        val activeFieldId = ep.getInt(GeneralKeys.SELECTED_FIELD_ID, -1)
//        val activeField = if (activeFieldId != -1) {
//            database.getFieldObject(activeFieldId)
//        } else {
//            Toast.makeText(context, R.string.warning_field_missing, Toast.LENGTH_LONG).show()
//            return
//        }
        val activeField = database.getFieldObject(fieldId)
        if (activeField.getExp_source() == null ||
            activeField.getExp_source().equals("") ||
            activeField.getExp_source().equals("local")) {

            Toast.makeText(context, R.string.brapi_field_not_selected, Toast.LENGTH_LONG).show()
            return
        }

        if (!BrAPIService.checkMatchBrapiUrl(context, activeField.getExp_source())) {
            val hostURL = BrAPIService.getHostUrl(context)
            val badSourceMsg = context.resources.getString(
                R.string.brapi_field_non_matching_sources,
                activeField.getExp_source(),
                hostURL
            )
            Toast.makeText(context, badSourceMsg, Toast.LENGTH_LONG).show()
            return
        }

        if (BrAPIService.isLoggedIn(context)) {
            val exportIntent = Intent(context, BrapiExportActivity::class.java)
            exportIntent.putExtra("FIELD_ID", fieldId)
            context.startActivity(exportIntent)
        } else {
            val brapiAuth = BrapiAuthDialog(context)
            brapiAuth.show()
        }
    }

    private fun exportLocal(fieldIds: List<Int>) {
        val layout = LayoutInflater.from(context).inflate(R.layout.dialog_export, null)

        val fileNameLabel: TextView = layout.findViewById(R.id.fileNameLabel)
        val bundleInfoMessage: TextView = layout.findViewById(R.id.bundleInfo)
        val exportFile: EditText = layout.findViewById(R.id.fileName)
        val checkDB: CheckBox = layout.findViewById(R.id.formatDB)
        val checkTable: CheckBox = layout.findViewById(R.id.formatTable)
        onlyUnique = layout.findViewById(R.id.onlyUnique)
        allColumns = layout.findViewById(R.id.allColumns)
        activeTraits = layout.findViewById(R.id.activeTraits)
        allTraits = layout.findViewById(R.id.allTraits)
        val checkOverwrite: CheckBox = layout.findViewById(R.id.overwrite)
        val checkBundle: CheckBox = layout.findViewById(R.id.dialog_export_bundle_data_cb)

        checkBundle.setChecked(ep.getBoolean(GeneralKeys.DIALOG_EXPORT_BUNDLE_CHECKED, false))
        checkOverwrite.setChecked(ep.getBoolean(GeneralKeys.EXPORT_OVERWRITE, false))
        checkDB.setChecked(ep.getBoolean(GeneralKeys.EXPORT_FORMAT_DATABASE, false))
        checkTable.setChecked(ep.getBoolean(GeneralKeys.EXPORT_FORMAT_TABLE, false))

        val isOnlyUnique = ep.getBoolean(GeneralKeys.EXPORT_COLUMNS_UNIQUE, false)
        onlyUnique?.setChecked(isOnlyUnique)
        val isAllColumns = ep.getBoolean(GeneralKeys.EXPORT_COLUMNS_ALL, false)
        allColumns?.setChecked(isAllColumns)
        val isAllTraits = ep.getBoolean(GeneralKeys.EXPORT_TRAITS_ALL, false)
        allTraits?.setChecked(isAllTraits)
        val isActiveTraits = ep.getBoolean(GeneralKeys.EXPORT_TRAITS_ACTIVE, false)
        activeTraits?.setChecked(isActiveTraits)

        val timeStamp = SimpleDateFormat("yyyy-MM-dd-hh-mm-ss", Locale.getDefault())
        var fileSuffix = ""

        if (fieldIds.size > 1) {
            fileNameLabel.text = context.getString(R.string.export_zip_file_name_label)
            fileSuffix = context.getString(R.string.export_default_multi_export_suffix)
            checkBundle.visibility = View.GONE
            checkOverwrite.visibility = View.GONE
            bundleInfoMessage.visibility = View.VISIBLE
        } else {
            fileNameLabel.text = context.getString(R.string.export_csv_file_name_label)
            var fieldObject = database.getFieldObject(fieldIds[0])
            fileSuffix = fieldObject.exp_name
            if (fileSuffix.length > 4 && fileSuffix.lowercase().endsWith(".csv")) {
                fileSuffix =fileSuffix.substring(0, fileSuffix.length - 4)
            }
            checkBundle.visibility = View.VISIBLE
            checkOverwrite.visibility = View.VISIBLE
            bundleInfoMessage.visibility = View.GONE
        }

        val exportString = "${timeStamp.format(Calendar.getInstance().time)}_$fileSuffix"
        exportFile.setText(exportString)

        val builder = AlertDialog.Builder(context)
        builder.setTitle(R.string.settings_export)
            .setCancelable(true)
            .setView(layout)

        builder.setPositiveButton(context.getString(R.string.dialog_save), null)
        builder.setNegativeButton(context.getString(R.string.dialog_cancel)) { dialog, _ -> dialog.dismiss() }

        val saveDialog = builder.create()
        saveDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        saveDialog.show()

        val params = saveDialog.window?.attributes
        params?.width = WindowManager.LayoutParams.MATCH_PARENT
        saveDialog.window?.attributes = params

        // Override positive button so it doesn't automatically dismiss dialog
        val positiveButton: Button = saveDialog.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveButton.setOnClickListener {

            val isOnlyUniqueChecked = onlyUnique?.isChecked == true
            val isAllColumnsChecked = allColumns?.isChecked == true
            val isActiveTraitsChecked = activeTraits?.isChecked == true
            val isAllTraitsChecked = allTraits?.isChecked == true

            if (!checkDB.isChecked && !checkTable.isChecked) {
                Toast.makeText(context, context.getString(R.string.export_error_missing_format), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isOnlyUniqueChecked && !isAllColumnsChecked) {
                Toast.makeText(context, context.getString(R.string.export_error_missing_column), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isActiveTraitsChecked && !isAllTraitsChecked) {
                Toast.makeText(context, context.getString(R.string.export_error_missing_trait), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            with(ep.edit()) {
                putBoolean(GeneralKeys.EXPORT_COLUMNS_UNIQUE, isOnlyUniqueChecked)
                putBoolean(GeneralKeys.EXPORT_COLUMNS_ALL, isAllColumnsChecked)
                putBoolean(GeneralKeys.EXPORT_TRAITS_ALL, isAllTraitsChecked)
                putBoolean(GeneralKeys.EXPORT_TRAITS_ACTIVE, isActiveTraitsChecked)
                putBoolean(GeneralKeys.EXPORT_FORMAT_TABLE, checkTable.isChecked)
                putBoolean(GeneralKeys.EXPORT_FORMAT_DATABASE, checkDB.isChecked)
                putBoolean(GeneralKeys.EXPORT_OVERWRITE, checkOverwrite.isChecked)
                putBoolean(GeneralKeys.DIALOG_EXPORT_BUNDLE_CHECKED, checkBundle.isChecked)
                apply()
            }

            BaseDocumentTreeUtil.getDirectory(context as Activity, R.string.dir_field_export)

//            if (isOnlyUniqueChecked) {
//                newRange.add(ep.getString(GeneralKeys.UNIQUE_NAME, "") ?: "")
//            }
//
//            if (isAllColumnsChecked) {
//                val columns = database.getRangeColumns()
//                newRange.addAll(columns)
//            }

            if (isActiveTraitsChecked) {
                val traits = database.allTraitObjects
                for (t in traits) {
                    if (t.visible) {
                        exportTrait.add(t)
                    }
                }
            }

            if (isAllTraitsChecked) {
                exportTrait.addAll(database.allTraitObjects)
            }

            checkDbBool = checkDB.isChecked
            checkTableBool = checkTable.isChecked

            exportFileString = if (ep.getBoolean(GeneralKeys.EXPORT_OVERWRITE, false)) {
                getOverwriteFile(exportFile.text.toString())
            } else {
                exportFile.text.toString()
            }

            startExportTasks()
            saveDialog.dismiss()

        }
    }

    private fun showProgressDialog() {
        progressDialog = ProgressDialog(context).apply {
            isIndeterminate = true
            setCancelable(false)
            setMessage(Html.fromHtml(context.getString(R.string.export_progress)))
            show()
        }
    }

    private fun startExportTasks() {
        showProgressDialog()
        fieldIds.forEach { fieldId ->
            launchExportTask(fieldId)
        }
    }

    // Launches a new coroutine for each export task
    private fun launchExportTask(fieldId: Int) {
        ioScope.launch {
            val result = exportData(fieldId)
            withContext(Dispatchers.Main) {
                handleExportResult(result)
            }
        }
    }

    // Function that handles the actual export logic (formerly doInBackground)
//    private suspend fun exportData(fieldId: Int): ExportResult {
//        return try {
//            Log.d(TAG, "Export task started for fieldId: $fieldId")
//            val fo = database.getFieldObject(fieldId)
//            val bundleChecked = ep.getBoolean(GeneralKeys.DIALOG_EXPORT_BUNDLE_CHECKED, false)
//
//            val columns = ArrayList<String>().apply {
//                if (onlyUnique?.isChecked == true) add(fo.unique_id)
//                if (allColumns?.isChecked == true) addAll(database.getRangeColumns())
//            }
//            Log.d(TAG, "Columns are: " + columns.joinToString())
//
//            val outputStream = context.contentResolver.openOutputStream(file.uri) ?: return
//            val fw = OutputStreamWriter(outputStream)
//            val csvWriter: CSVWriter
//
////            val exportData = database.getExportDBData(columns.toTypedArray(), exportTrait, fieldId, fo.unique_id)
////            if (exportData.count == 0) {
////                return ExportResult.NoData
////            }
//
////            exportData.use { cursor ->
////                // Process cursor data and create files
//                if (checkDbBool) {
//                    val exportData = database.getExportDBData(columns.toTypedArray(), exportTrait, fieldId, fo.unique_id)
//                    if (exportData.count == 0) {
//                        return ExportResult.NoData
//                    }
//                    val dbFile = createExportFile(fieldId, "database")
//                    if (dbFile != null) {
//                        csvWriter = writeToExportFile(exportData, dbFile)
//                        csvWriter.writeDatabaseFormat(columns)
//                        filesToExport.add(dbFile)
//                    }
//                }
//                if (checkTableBool) {
//                    val convertedExportData = database.convertDatabaseToTable(columns.toTypedArray(), exportTrait, fieldId, fo.unique_id)
//                    if (convertedExportData.count == 0) {
//                        return ExportResult.NoData
//                    }
//                    val tableFile = createExportFile(fieldId, "table")
//                    if (tableFile != null) {
//                        csvWriter = writeToExportFile(convertedExportData, tableFile)
//                        val labels = ArrayList<String>()
//                        for (trait in exportTrait) labels.add(trait.name)
//                        csvWriter.writeTableFormat(columns.plus(labels), columns.size, exportTrait)
//                        filesToExport.add(tableFile)
//                    }
//                }
//
////            }
//
//            if (bundleChecked) {
//                val fieldObject = database.getFieldObject(fieldId)
//                fieldObject?.let {
//                    val studyName = it.exp_name
//                    val mediaDir = BaseDocumentTreeUtil.getFile(context, R.string.dir_plot_data, studyName)
//                    mediaDir?.let { dir ->
//                        if (dir.exists() && dir.isDirectory) {
//                            filesToExport.add(dir)
//                        }
//                    }
//                }
//            }
//
//            database.updateExpTable(false, false, true, fieldId)
//            ExportResult.Success("Export successful for fieldId: $fieldId")
//        } catch (e: Exception) {
//            Log.e(TAG, "Export failed for fieldId $fieldId: ${e.message}", e)
//            ExportResult.Failure(e)
//        }
//    }
//
//    private fun createExportFile(fieldId: Int, fileType: String): DocumentFile? {
//        val fileName = "${exportFileString}_${fieldId}_$fileType.csv" // Assuming CSV format for both types
//        val exportDir = BaseDocumentTreeUtil.getDirectory(context, R.string.dir_field_export)
//        return exportDir?.let {
//            if (it.exists()) {
//                val file = it.createFile("text/csv", fileName)
//                file
//            } else null
//        }
//    }
//
//    private fun writeToExportFile(fieldId: Int, cursor: Cursor, file: DocumentFile?, fileType: String) {
//        if (file == null) return
//
//        val outputStream = context.contentResolver.openOutputStream(file.uri) ?: return
//        outputStream.use { stream ->
//            val fw = OutputStreamWriter(stream)
//            val csvWriter: CSVWriter
//            csvWriter = CSVWriter(fw, cursor)
//
////            when (fileType) {
////                "database" -> {
////                    csvWriter = CSVWriter(fw, cursor)
////                    csvWriter.writeDatabaseFormat(newRange)
////                }
////                "table" -> {
////                    val newRanges = newRange.toTypedArray()
////                    val fo = database.getFieldObject(fieldId)
////                    val convertedExportData = database.convertDatabaseToTable(newRanges, exportTrait, fieldId, fo.unique_id)
////                    csvWriter = CSVWriter(fw, convertedExportData)
////                    val labels = ArrayList<String>()
////                    for (trait in exportTrait) labels.add(trait.name)
////                    csvWriter.writeTableFormat(newRanges.plus(labels), newRanges.size, exportTrait)
////                }
////            }
//            Unit // Explicitly specify the return type as Unit
//        }
//    }

    // ... [Other class code remains unchanged]

    private suspend fun exportData(fieldId: Int): ExportResult {
        return try {
            Log.d(TAG, "Export task started for fieldId: $fieldId")
            val fo = database.getFieldObject(fieldId)
            val bundleChecked = ep.getBoolean(GeneralKeys.DIALOG_EXPORT_BUNDLE_CHECKED, false)

            val columns = ArrayList<String>().apply {
                if (onlyUnique?.isChecked == true) add(fo.unique_id)
                if (allColumns?.isChecked == true) addAll(database.getRangeColumns())
            }
            Log.d(TAG, "Columns are: " + columns.joinToString())

            if (checkDbBool) {
                val exportData = database.getExportDBData(columns.toTypedArray(), exportTrait, fieldId, fo.unique_id)
                if (exportData.count > 0) {
                    createAndWriteToExportFile(fieldId, exportData, "database", columns)
                }
            }

            if (checkTableBool) {
                val convertedExportData = database.convertDatabaseToTable(columns.toTypedArray(), exportTrait, fieldId, fo.unique_id)
                if (convertedExportData.count > 0) {
                    createAndWriteToExportFile(fieldId, convertedExportData, "table", columns)
                }
            }

            if (bundleChecked) {
                handleBundledFiles(fieldId)
            }

            database.updateExpTable(false, false, true, fieldId)
            ExportResult.Success("Export successful for fieldId: $fieldId")
        } catch (e: Exception) {
            Log.e(TAG, "Export failed for fieldId $fieldId: ${e.message}", e)
            ExportResult.Failure(e)
        }
    }

    private fun createAndWriteToExportFile(fieldId: Int, cursor: Cursor, fileType: String, columns: ArrayList<String>) {
        val fileName = "${exportFileString}_${fieldId}_$fileType.csv"
        val exportDir = BaseDocumentTreeUtil.getDirectory(context, R.string.dir_field_export)
        exportDir?.let { dir ->
            if (dir.exists()) {
                val file = dir.createFile("text/csv", fileName)
                file?.let { docFile ->
                    val outputStream = context.contentResolver.openOutputStream(docFile.uri) ?: return
                    outputStream.use { stream ->
                        val fw = OutputStreamWriter(stream)
                        val csvWriter = CSVWriter(fw, cursor)
                        when (fileType) {
                            "database" -> csvWriter.writeDatabaseFormat(columns)
                            "table" -> {
                                val newColumns = columns.toTypedArray()
                                val labels = exportTrait.map { it.name }
                                csvWriter.writeTableFormat(newColumns.plus(labels), columns.size, exportTrait)
                            }
                        }
                    }
                    filesToExport.add(docFile)
                }
            }
        }
    }

    private fun handleBundledFiles(fieldId: Int) {
        val fieldObject = database.getFieldObject(fieldId)
        fieldObject?.let {
            val studyName = it.exp_name
            val mediaDir = BaseDocumentTreeUtil.getFile(context, R.string.dir_plot_data, studyName)
            mediaDir?.let { dir ->
                if (dir.exists() && dir.isDirectory) {
                    filesToExport.add(dir)
                }
            }
        }
    }

    private fun createZipFile(files: List<DocumentFile>, zipFileName: String): DocumentFile? {
        val exportDir = BaseDocumentTreeUtil.getDirectory(context, R.string.dir_field_export)
        return exportDir?.let { dir ->
            if (dir.exists()) {
                val zipFile = dir.createFile("application/zip", zipFileName)
                zipFile?.let { zf ->
                    val outputStream = BaseDocumentTreeUtil.getFileOutputStream(context, R.string.dir_field_export, zipFileName)
                    outputStream?.let { os ->
                        ZipUtil.zip(context, files.toTypedArray(), os)
                        // Optionally, delete the original files after zipping
                        files.forEach { it.delete() }
                        zf
                    }
                }
            } else null
        }
    }

    sealed class ExportResult {
        data class Success(val message: String): ExportResult()
        data class Failure(val error: Throwable): ExportResult()
        object NoData: ExportResult()
    }


    // Function to handle the result of the export (formerly onPostExecute)
    private fun handleExportResult(result: ExportResult) {
        when (result) {
            is ExportResult.Success -> {
                processedFieldCount++
                if (processedFieldCount == fieldIds.size) {
                    val finalFile = if (filesToExport.size > 1) {
                        val zipFileName = "${exportFileString}_final.zip"
                        createZipFile(filesToExport, zipFileName)
                    } else {
                        filesToExport.firstOrNull()
                    }

                    progressDialog?.dismiss()
                    finalFile?.let { shareFile(it) }
                    showCitationDialog()
                }
            }
            is ExportResult.Failure -> {
                Toast.makeText(context, context.getString(R.string.export_error_general), Toast.LENGTH_SHORT).show()
            }
            ExportResult.NoData -> {
                Toast.makeText(context, context.getString(R.string.export_error_data_missing), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getOverwriteFile(filename: String): String {
        val exportDir = BaseDocumentTreeUtil.getDirectory(context, R.string.dir_field_export)

        exportDir?.takeIf { it.exists() }?.listFiles()?.forEach { file ->
            val fileName = file.name ?: return@forEach

            if (filename.contains(fFile)) {
                val oldDoc = BaseDocumentTreeUtil.getFile(context, R.string.dir_field_export, fileName)
                val newDoc = BaseDocumentTreeUtil.getFile(context, R.string.dir_archive, fileName)
                val newName = newDoc?.name ?: fileName

                if (oldDoc != null && newDoc != null && newName != null) {
                    if (checkDbBool && fileName.contains(fFile) && fileName.contains("database")) {
                        oldDoc.renameTo(newName)
                    }

                    if (checkTableBool && fileName.contains(fFile) && fileName.contains("table")) {
                        oldDoc.renameTo(newName)
                    }
                }
            }
        }

        return filename
    }

    /**
     * Scan file to update file list and share exported file
     */
    private fun shareFile(docFile: DocumentFile) {
        if (!ep.getBoolean(GeneralKeys.DISABLE_SHARE, false)) {
            val intent = Intent()
            intent.action = Intent.ACTION_SEND
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_STREAM, docFile.uri)
            try {
                context.startActivity(Intent.createChooser(intent, "Sending File..."))
            } catch (e: java.lang.Exception) {
                Log.e("Field Book", "" + e.message)
            }
        }
    }

    private fun showCitationDialog() {
        val builder =
            androidx.appcompat.app.AlertDialog.Builder(context, R.style.AppAlertDialog)
        builder.setTitle(context.getString(R.string.citation_title))
            .setMessage(context.getString(R.string.citation_string) + "\n\n" + context.getString(R.string.citation_text))
            .setCancelable(false)
        builder.setPositiveButton(context.getString(R.string.dialog_ok),
            DialogInterface.OnClickListener { dialog, which ->
                dialog.dismiss()
                if (context is Activity) {
                    context.invalidateOptionsMenu()
                }

                val intent = Intent()
                intent.setClassName(
                    context,
                    ConfigActivity::class.java.name
                )
                context.startActivity(intent)
            })
        val alert = builder.create()
        alert.show()
    }

}
