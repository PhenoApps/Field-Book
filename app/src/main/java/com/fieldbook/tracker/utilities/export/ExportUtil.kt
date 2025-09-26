package com.fieldbook.tracker.utilities.export

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
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
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.brapi.BrapiExportActivity
import com.fieldbook.tracker.brapi.BrapiAuthDialogFragment
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.dialogs.CitationDialog
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.objects.ImportFormat
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.utilities.CSVWriter
import com.fieldbook.tracker.utilities.FileUtil
import com.fieldbook.tracker.utilities.ZipUtil
import dagger.hilt.android.qualifiers.ActivityContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.phenoapps.utils.BaseDocumentTreeUtil
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * Checks preconditions before collect and export.
 */

class ExportUtil @Inject constructor(
    @ActivityContext private val context: Context,
    private val database: DataHelper,
    private val spectralFileExporter: SpectralFileExporter
) : CoroutineScope by MainScope() {

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
    private var defaultFieldString = ""
    private var exportFileString = ""
    private var filesToExport: MutableList<DocumentFile> = mutableListOf()
    private var processedFieldCount = 0
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private var progressDialog: ProgressDialog? = null

    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val timeStamp = SimpleDateFormat("yyyy-MM-dd-hh-mm-ss", Locale.getDefault())
    private var multipleFields = false

    // a temporary directory is created for bundled media export
    // this needs to be deleted AFTER zipping is completed
    private var tempDirectory: DocumentFile? = null

    fun exportMultipleFields(fieldIds: List<Int>) {
        this.fieldIds = fieldIds
        this.multipleFields = fieldIds.size > 1
        export()
    }

    fun exportActiveField() {
        val activeFieldId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, -1)
        this.fieldIds = listOf(activeFieldId)
        export()
    }

    fun export() {
        val exporter = preferences.getString(PreferenceKeys.EXPORT_SOURCE_DEFAULT, "")

        if (!allFieldsBrAPI() || exporter == "local" || !preferences.getBoolean(PreferenceKeys.BRAPI_ENABLED, false)) {
            // use local export if any fields aren't all brapi, if brapi is disabled, or if local pref is set
            exportPermission()
        } else if (exporter == "brapi") {
            exportBrAPI(fieldIds)
        } else {
            showExportDialog() // provide export type choice if fields are all brapi but pref is not set
        }
    }

    fun allFieldsBrAPI(): Boolean {
        fieldIds.forEach { fieldId ->
            val field = database.getFieldObject(fieldId)
            val importFormat = field?.dataSourceFormat
            if (importFormat != ImportFormat.BRAPI) {
                Log.d(TAG, "Not all fields are BrAPI fields")
                return false
            }
        }
        return true
    }

    @AfterPermissionGranted(PERMISSIONS_REQUEST_EXPORT_DATA)
    fun exportPermission() {
        val perms = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

        if (EasyPermissions.hasPermissions(context, *perms) || Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {

            val fieldsToExport = fieldIds.mapNotNull { database.getFieldObject(it) }

            if (fieldsToExport.isNotEmpty()) {

                exportLocal(fieldsToExport)
            }

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
            preferences.getString(PreferenceKeys.BRAPI_DISPLAY_NAME, context.getString(R.string.brapi_edit_display_name_default))
        )

        val adapter = ArrayAdapter(context, R.layout.list_item_dialog_list, exportArray)
        exportSourceList.adapter = adapter

        val builder = AlertDialog.Builder(context, R.style.AppAlertDialog)
        builder.setTitle(R.string.export_dialog_title)
            .setView(layout)
            .setPositiveButton(context.getString(R.string.dialog_cancel)) { dialog, _ -> dialog.dismiss() }

        val dialog = builder.create()
        dialog.show()
        exportSourceList.setOnItemClickListener { _, _, which, _ ->
            when (which) {
                0 -> exportPermission()
                1 -> exportBrAPI(fieldIds)
            }
            dialog.dismiss()
        }
    }

    fun exportBrAPI(fieldIds: List<Int>) {
        val activeFields = fieldIds.map { fieldId -> database.getFieldObject(fieldId) }
        val nonMatchingSourceFields = activeFields.filter { !BrAPIService.checkMatchBrapiUrl(context, it.getDataSource()) }

        if (nonMatchingSourceFields.isNotEmpty()) {
            val hostURL = BrAPIService.getHostUrl(context)
            val badSourceMsg = context.resources.getString(
                R.string.brapi_field_non_matching_sources,
                nonMatchingSourceFields.joinToString(", ") { it.getDataSource() },
                hostURL
            )
            Toast.makeText(context, badSourceMsg, Toast.LENGTH_LONG).show()
            return
        }

        if (BrAPIService.isLoggedIn(context)) {
            val exportIntent = Intent(context, BrapiExportActivity::class.java)
            exportIntent.putIntegerArrayListExtra(BrapiExportActivity.FIELD_IDS, ArrayList(fieldIds))
            context.startActivity(exportIntent)
        } else {
            val brapiAuth = BrapiAuthDialogFragment().newInstance()
            brapiAuth?.show((context as FragmentActivity).supportFragmentManager, "BrapiAuthDialogFragment")
        }
    }

    private fun exportLocal(fields: List<FieldObject>) {
        val layout = LayoutInflater.from(context).inflate(R.layout.dialog_export, null)

        val bundleInfoMessage: TextView = layout.findViewById(R.id.bundleInfo)
        val fileName: EditText = layout.findViewById(R.id.fileName)
        val checkDB: CheckBox = layout.findViewById(R.id.formatDB)
        val checkTable: CheckBox = layout.findViewById(R.id.formatTable)
        onlyUnique = layout.findViewById(R.id.onlyUnique)
        allColumns = layout.findViewById(R.id.allColumns)
        activeTraits = layout.findViewById(R.id.activeTraits)
        allTraits = layout.findViewById(R.id.allTraits)
        val checkOverwrite: CheckBox = layout.findViewById(R.id.overwrite)
        val checkBundle: CheckBox = layout.findViewById(R.id.dialog_export_bundle_data_cb)

        checkBundle.setChecked(preferences.getBoolean(GeneralKeys.DIALOG_EXPORT_BUNDLE_CHECKED, false))
        checkOverwrite.setChecked(preferences.getBoolean(GeneralKeys.EXPORT_OVERWRITE, false))
        checkDB.setChecked(preferences.getBoolean(GeneralKeys.EXPORT_FORMAT_DATABASE, false))
        checkTable.setChecked(preferences.getBoolean(GeneralKeys.EXPORT_FORMAT_TABLE, false))

        val isOnlyUnique = preferences.getBoolean(GeneralKeys.EXPORT_COLUMNS_UNIQUE, false)
        onlyUnique?.setChecked(isOnlyUnique)
        val isAllColumns = preferences.getBoolean(GeneralKeys.EXPORT_COLUMNS_ALL, false)
        allColumns?.setChecked(isAllColumns)
        val isAllTraits = preferences.getBoolean(GeneralKeys.EXPORT_TRAITS_ALL, false)
        allTraits?.setChecked(isAllTraits)
        val isActiveTraits = preferences.getBoolean(GeneralKeys.EXPORT_TRAITS_ACTIVE, false)
        activeTraits?.setChecked(isActiveTraits)

        var defaultFileString = ""
        if (multipleFields) {
            defaultFieldString = context.getString(R.string.export_multiple_fields_name)
            checkOverwrite.visibility = View.GONE
            bundleInfoMessage.visibility = View.VISIBLE
        } else {
            val fo = fields.first()
            defaultFieldString = fo.name
            if (defaultFieldString.length > 4 && defaultFieldString.lowercase().endsWith(".csv")) {
                defaultFieldString = defaultFieldString.substring(0, defaultFieldString.length - 4)
            }
            checkOverwrite.visibility = View.VISIBLE
            bundleInfoMessage.visibility = View.GONE
        }

        defaultFileString = "${timeStamp.format(Calendar.getInstance().time)}_$defaultFieldString"
        fileName.setText(defaultFileString)

        val builder = AlertDialog.Builder(context, R.style.AppAlertDialog)
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

        fun onOkExportClicked() {
            val isOnlyUniqueChecked = onlyUnique?.isChecked == true
            val isAllColumnsChecked = allColumns?.isChecked == true
            val isActiveTraitsChecked = activeTraits?.isChecked == true
            val isAllTraitsChecked = allTraits?.isChecked == true

            if (!checkDB.isChecked && !checkTable.isChecked) {
                Toast.makeText(context, context.getString(R.string.export_error_missing_format), Toast.LENGTH_SHORT).show()
                return
            }

            if (!isOnlyUniqueChecked && !isAllColumnsChecked) {
                Toast.makeText(context, context.getString(R.string.export_error_missing_column), Toast.LENGTH_SHORT).show()
                return
            }

            if (!isActiveTraitsChecked && !isAllTraitsChecked) {
                Toast.makeText(context, context.getString(R.string.export_error_missing_trait), Toast.LENGTH_SHORT).show()
                return
            }

            with(preferences.edit()) {
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

            exportTrait.clear()
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
            exportFileString = fileName.text.toString()

            startExportTasks()
            saveDialog.dismiss()
        }

        // Override positive button so it doesn't automatically dismiss dialog
        val positiveButton: Button = saveDialog.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveButton.setOnClickListener {

            val repeatedMeasuresEnabled = preferences.getBoolean(PreferenceKeys.REPEATED_VALUES_PREFERENCE_KEY, false)

            //show a warning if table is selected and repeated measures is enabled
            if (checkTable.isChecked && repeatedMeasuresEnabled) {

                AlertDialog.Builder(context, R.style.AppAlertDialog)
                    .setTitle(R.string.export_util_repeated_measures_table_warning_title)
                    .setMessage(R.string.export_util_repeated_measures_table_warning_message)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        onOkExportClicked()
                    }
                    .show()

            } else onOkExportClicked()
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

    // Launches a new coroutine for each export task
    private fun startExportTasks() {
        processedFieldCount = 0
        filesToExport.clear()
        showProgressDialog()
        ioScope.launch {
            for (fieldId in fieldIds) {
                val result = withContext(Dispatchers.IO) { exportData(fieldId) }
                withContext(Dispatchers.Main) {
                    handleExportResult(result)
                }
            }
        }
    }

    private suspend fun exportData(fieldId: Int): ExportResult {
        return try {
            Log.d(TAG, "Export task started for fieldId: $fieldId")
            val bundleChecked = preferences.getBoolean(GeneralKeys.DIALOG_EXPORT_BUNDLE_CHECKED, false)
            val fo = database.getFieldObject(fieldId)
            var fieldFileString = exportFileString
            if (multipleFields) { fieldFileString = "${timeStamp.format(Calendar.getInstance().time)}_${fo.name}" }

            if (checkDbBool) {
                val columns = ArrayList<String>().apply {
                    addAll(database.getAllObservationUnitAttributeNames(fieldId))
                }

                try {
                    val cursorAndColumnsPair = when {
                        onlyUnique?.isChecked == true -> {
                            database.getExportDBDataShort(columns.toTypedArray(), fo.uniqueId, exportTrait, fieldId) to arrayListOf(fo.uniqueId)
                        }
                        allColumns?.isChecked == true -> {
                            database.getExportDBData(columns.toTypedArray(), exportTrait, fieldId) to columns
                        }
                        else -> null
                    }

                    cursorAndColumnsPair?.let { (cursor, columnsForExport) ->
                        cursor.use { cur ->
                            if (cur.count > 0) {
                                createExportFile(cur, "database", fieldFileString, columnsForExport)
                            } else {
                                ExportResult.NoData
                            }
                        }
                    } ?: Log.d(TAG, "No database export condition matched.")
                } catch (e: Exception) {
                    Log.e(TAG, "Operation failed: ${e.message}", e)
                }
            }

            if (checkTableBool) {
                val columns = ArrayList<String>().apply {
                    if (onlyUnique?.isChecked == true) add(fo.uniqueId)
                    if (allColumns?.isChecked == true) addAll(database.getAllObservationUnitAttributeNames(fieldId))
                }
                Log.d(TAG, "Columns are: " + columns.joinToString())

                val exportDataMethod: () -> Cursor = when {
                    onlyUnique?.isChecked == true -> {
                        { database.getExportTableDataShort(fieldId, fo.uniqueId, exportTrait) }
                    }
                    allColumns?.isChecked == true -> {
                        { database.getExportTableData(fieldId, exportTrait) }
                    }
                    else -> throw IllegalStateException("Neither onlyUnique nor allColumns checkboxes are checked.")
                }

                exportDataMethod().use { cursor ->
                    try {
                        if (cursor.count > 0) {
                            createExportFile(cursor, "table", fieldFileString, columns)
                        } else {
                            ExportResult.NoData
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Operation failed: ${e.message}", e)
                    }
                }
            }

            spectralFileExporter.exportSpectralFile(fieldId)

            if (bundleChecked) {

                handleBundledFiles(fieldId)
            }

            database.updateExportDate(fieldId)
            Log.d(TAG, "Export finished successfully for field ${fo.name}")
            ExportResult.Success("Export successful for field ${fo.name}")
        } catch (e: Exception) {
            val fo = database.getFieldObject(fieldId)
            Log.e(TAG, "Export failed for field ${fo.name}: ${e.message}", e)
            ExportResult.Failure(e)
        }
    }

    private fun createExportFile(cursor: Cursor, fileType: String, fileString: String, columns: ArrayList<String>) {
        val fileName = "${fileString}_$fileType.csv"
        val exportDir = BaseDocumentTreeUtil.getDirectory(context, R.string.dir_field_export)

        val deviceName = preferences.getString(GeneralKeys.DEVICE_NAME, Build.MODEL) ?: Build.MODEL

        exportDir?.let { dir ->
            if (dir.exists()) {
                val file = dir.createFile("text/csv", fileName)
                file?.let { docFile ->
                    context.contentResolver.openOutputStream(docFile.uri)?.use { stream ->
                        val outputStream =
                            context.contentResolver.openOutputStream(docFile.uri) ?: return
                        outputStream.use { stream ->
                            val fw = OutputStreamWriter(stream)
                            val csvWriter = CSVWriter(fw, cursor)
                            when (fileType) {
                                "database" -> csvWriter.writeDatabaseFormat(columns, deviceName)
                                "table" -> {
                                    val newColumns = columns.toTypedArray()
                                    val labels = exportTrait.map { it.alias }

                                    // Log the arguments
                                    val newColumnsStr = newColumns.joinToString(", ")
                                    val labelsStr = labels.joinToString(", ")
                                    val columnsSizeStr = columns.size.toString()
                                    Log.d(TAG, "Calling writeTableFormat with arguments: newColumns=[$newColumnsStr], columnsSize=[$columnsSizeStr], labels=[$labelsStr]")

                                    csvWriter.writeTableFormat(
                                        newColumns.plus(labels),
                                        columns.size,
                                        exportTrait
                                    )
                                }
                            }
                        }
                        filesToExport.add(docFile)
                    }
                    Log.d(TAG, "Export file created: ${docFile.uri}")
                } ?: run {
                    Log.e(TAG, "Failed to create export file: $fileName")
                }
            }
        }
    }

    private fun handleBundledFiles(fieldId: Int) {
        val fieldObject = database.getFieldObject(fieldId)
        fieldObject?.let {
            val studyName = it.name
            val mediaDir = BaseDocumentTreeUtil.getFile(context, R.string.dir_plot_data, studyName)
            mediaDir?.let { dir ->
                if (dir.exists() && dir.isDirectory) {
                    bundleMediaDirectories(studyName, dir)
                } else {
                    Log.e(TAG, "Media directory is invalid or does not exist: ${dir.uri}")
                }
            }
        }
    }

    /**
     * There might be some media directories under plot_data/studyName/
     * that might be from non-existing traits anymore or might just be some randomly created directories.
     * Simply doing filesToExport.add(dir) will add all the directories under plot_data/studyName/ to the zip file
     *
     * Creates a temporary structure of studyName/ where only the media directories for
     *  - existing trait when allTraits is selected
     *  - existing and active traits when activeTraits is selected
     * will be added
     */
    private fun bundleMediaDirectories(studyName: String, mediaDir: DocumentFile) {
        // sanitize the trait names, this will be compared against already sanitized traitDirectories
        val traitList = exportTrait.map { trait -> FileUtil.sanitizeFileName(trait.name) }
        val exportDir = BaseDocumentTreeUtil.getDirectory(context, R.string.dir_field_export)
        val tempDirName = "temp_export_${timeStamp.format(Calendar.getInstance().time)}"
        tempDirectory = exportDir?.createDirectory(tempDirName)

        try {
            tempDirectory?.let { tmpDir ->
                // create study name directory
                val studyDir = tmpDir.createDirectory(studyName)

                studyDir?.let { studyDirectory ->
                    mediaDir.listFiles().forEach { traitDir ->
                        val traitDirName = traitDir.name ?: ""

                        // copy only the trait directories present in traitList
                        if (traitDir.isDirectory && traitList.contains(traitDirName)) {
                            Log.d(TAG, "Copying trait directory: $traitDirName")
                            val newDir = studyDirectory.createDirectory(traitDirName)
                            newDir?.let {
                                traitDir.listFiles().forEach { file ->
                                    if (file.isFile) copyFileToDirectory(file, it, file.name ?: "")
                                }
                            }
                        }
                    }

                    filesToExport.add(studyDirectory)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling bundled files: ${e.message}", e)
        }
    }

    private fun createZipFile(files: List<DocumentFile>, fileName: String): DocumentFile? {
        val exportDir = BaseDocumentTreeUtil.getDirectory(context, R.string.dir_field_export)
        val zipFileName = "${fileName}.zip"
        return exportDir?.let { dir ->
            if (dir.exists()) {
                val zipFile = dir.createFile("application/zip", zipFileName)
                zipFile?.let { zf ->
                    val outputStream = BaseDocumentTreeUtil.getFileOutputStream(context, R.string.dir_field_export, zipFileName)
                    outputStream?.let { os ->
                        ZipUtil.Companion.zip(context, files.toTypedArray(), os)
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
                        val zipFile = createZipFile(filesToExport, exportFileString)
                        // Archive the individual files after zipping
                        filesToExport.forEach { documentFile ->
                            if (!documentFile.isDirectory) {
                                val fileName = documentFile.name ?: return@forEach
                                archiveFile(documentFile, fileName)
                            }
                        }
                        zipFile
                    } else {
                        if (preferences.getBoolean(GeneralKeys.EXPORT_OVERWRITE, false)) {

                            if (filesToExport.isNotEmpty()) {
                                archivePreviousExport(filesToExport.first())
                            }
                        }
                        filesToExport.firstOrNull()
                    }

                    progressDialog?.dismiss()
                    finalFile?.let { shareFile(it) }
                    CitationDialog(context).show()
                }
            }
            is ExportResult.Failure -> {
                progressDialog?.dismiss()
                Toast.makeText(context, context.getString(R.string.export_error_general), Toast.LENGTH_SHORT).show()
            }
            ExportResult.NoData -> {
                progressDialog?.dismiss()
                Toast.makeText(context, context.getString(R.string.export_error_data_missing), Toast.LENGTH_SHORT).show()
            }
        }

        // delete the temp dir that was previously created
        tempDirectory?.delete()
    }

    private fun archivePreviousExport(newFile: DocumentFile) {
        val exportDir = BaseDocumentTreeUtil.getDirectory(context, R.string.dir_field_export)
        val newFileName = newFile.name ?: return
        Log.d(TAG, "Exported file is $newFileName and overwrite checkbox was checked. Looking for previous exports to archive.")

        // Exclude timestamp when matching previous exports by filename
        val truncatedNewFileName = newFileName.substringAfter("_")

        exportDir?.takeIf { it.exists() }?.listFiles()?.forEach { file ->
            val fileName = file.name ?: return@forEach

            // Apply the same truncation logic to each file name in the directory
            val truncatedFileName = fileName.substringAfter("_")

            // Check if the truncated file name matches and the file name is not exactly the new file name
            if (fileName != newFileName && truncatedFileName == truncatedNewFileName) {
                val oldDoc = BaseDocumentTreeUtil.getFile(context, R.string.dir_field_export, fileName)

                if (oldDoc != null) {
                    Log.d(TAG, "Archiving previous version: $fileName")
                    archiveFile(oldDoc, fileName)
                } else {
                    Log.e(TAG, "Failed to retrieve DocumentFiles for overwriting/archiving: $fileName")
                }
            } else {
                if (fileName == newFileName) {
                    Log.d(TAG, "Skipping $fileName, this is our current export version.")
                } else {
                    Log.d(TAG, "Skipping $fileName, it's a different field or format.")
                }
            }
        }
        Log.d(TAG, "Archive process completed for previous exports.")
    }


    private fun archiveFile(file: DocumentFile, newFileName: String) {
        val archiveDir = BaseDocumentTreeUtil.getDirectory(context, R.string.dir_archive)
        if (archiveDir != null && archiveDir.exists()) {
            val archivedFile = copyFileToDirectory(file, archiveDir, newFileName)
            if (archivedFile != null) {
                Log.d(TAG, "File archived: ${archivedFile.uri}")
                file.delete() // Delete the original file after archiving
            } else {
                Log.e(TAG, "Failed to archive file: ${file.uri}")
            }
        } else {
            Log.e(TAG, "Archive directory not found or does not exist.")
        }
    }

    private fun copyFileToDirectory(file: DocumentFile, directory: DocumentFile, newFileName: String): DocumentFile? {
        try {
            val mimeType = file.type ?: "application/octet-stream"
            val newFile = directory.createFile(mimeType, newFileName)
            newFile?.let {
                context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                    context.contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                return newFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying file: ${e.message}")
        }
        return null
    }

    /**
     * Scan file to update file list and share exported file
     */
    private fun shareFile(docFile: DocumentFile) {
        if (preferences.getBoolean(PreferenceKeys.ENABLE_SHARE, true)) {
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
}