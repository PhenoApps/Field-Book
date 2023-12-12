package com.fieldbook.tracker.utilities

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.AsyncTask
import android.os.Build
import android.os.Handler
import android.os.Looper
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


/**
 * Checks preconditions before collect and export.
 */

class ExportUtil @Inject constructor(@ActivityContext private val context: Context, private val database: DataHelper) {

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
    private var newRange: ArrayList<String> = arrayListOf()
    private var exportTrait: ArrayList<TraitObject> = arrayListOf()
    private var checkDbBool = false
    private var checkExcelBool = false
    private var exportFileString = ""
    private lateinit var fFile: String


    private val mHandler = Handler(Looper.getMainLooper())
//    val exportData = Runnable {
//        ExportDataTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, 0)
//    }



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

    private fun exportLocal(fieldIds: List<Int>) {
        val layout = LayoutInflater.from(context).inflate(R.layout.dialog_export, null)

        val fileNameLabel: TextView = layout.findViewById(R.id.fileNameLabel)
        val bundleInfoMessage: TextView = layout.findViewById(R.id.bundleInfo)
        val exportFile: EditText = layout.findViewById(R.id.fileName)
        val checkDB: CheckBox = layout.findViewById(R.id.formatDB)
        val checkExcel: CheckBox = layout.findViewById(R.id.formatExcel)
        onlyUnique = layout.findViewById(R.id.onlyUnique)
        allColumns = layout.findViewById(R.id.allColumns)
        activeTraits = layout.findViewById(R.id.activeTraits)
        allTraits = layout.findViewById(R.id.allTraits)
        val checkOverwrite: CheckBox = layout.findViewById(R.id.overwrite)
        val checkBundle: CheckBox = layout.findViewById(R.id.dialog_export_bundle_data_cb)

        checkBundle.setChecked(ep.getBoolean(GeneralKeys.DIALOG_EXPORT_BUNDLE_CHECKED, false))
        checkOverwrite.setChecked(ep.getBoolean(GeneralKeys.EXPORT_OVERWRITE, false))
        checkDB.setChecked(ep.getBoolean(GeneralKeys.EXPORT_FORMAT_DATABASE, false))
        checkExcel.setChecked(ep.getBoolean(GeneralKeys.EXPORT_FORMAT_TABLE, false))

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

            if (!checkDB.isChecked && !checkExcel.isChecked) {
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
                putBoolean(GeneralKeys.EXPORT_FORMAT_TABLE, checkExcel.isChecked)
                putBoolean(GeneralKeys.EXPORT_FORMAT_DATABASE, checkDB.isChecked)
                putBoolean(GeneralKeys.EXPORT_OVERWRITE, checkOverwrite.isChecked)
                putBoolean(GeneralKeys.DIALOG_EXPORT_BUNDLE_CHECKED, checkBundle.isChecked)
                apply()
            }

            BaseDocumentTreeUtil.getDirectory(context as Activity, R.string.dir_field_export)

            if (isOnlyUniqueChecked) {
                newRange.add(ep.getString(GeneralKeys.UNIQUE_NAME, "") ?: "")
            }

            if (isAllColumnsChecked) {
                val columns = database.getRangeColumns()
                newRange.addAll(columns)
            }

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
            checkExcelBool = checkExcel.isChecked

            exportFileString = if (ep.getBoolean(GeneralKeys.EXPORT_OVERWRITE, false)) {
                getOverwriteFile(exportFile.text.toString())
            } else {
                exportFile.text.toString()
            }

            for (int fieldId : fieldIds) {
                tasksQueue.add(new ExportDataTask(fieldId));
            }

            saveDialog.dismiss();
                startNextTask(); // Start the first task
            });

//            saveDialog.dismiss()
//
//            // Start an export task for each fieldId
//            val exportTasks = fieldIds.map { fieldId ->
//                ExportDataTask(fieldId).apply {
//                    executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
//                }
//            }
//
//            mHandler.post {
//                bundleExportedFiles(exportTasks)
//            }
        }
    }

    private val tasksQueue: Queue<ExportDataTask> = LinkedList()

    private fun startNextTask() {
        if (tasksQueue.isNotEmpty()) {
            val nextTask = tasksQueue.poll()
            nextTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        } else {
            // All fields are exported, bundle the files
            bundleExportedFiles()
        }
    }

    private fun bundleExportedFiles(exportTasks: List<ExportDataTask>) {
        val allFiles = exportTasks.mapNotNull { it.exportedFile }.toTypedArray()
        val zipFileName = "${exportFileString}.zip"
        val exportDir = BaseDocumentTreeUtil.getDirectory(context, R.string.dir_field_export)

        exportDir?.let {
            if (it.exists()) {
                val zipFile = it.createFile("*/*", zipFileName)
                val output = BaseDocumentTreeUtil.getFileOutputStream(context, R.string.dir_field_export, zipFileName)
                output?.let { outStream ->
                    ZipUtil.zip(context, allFiles, outStream)
                    zipFile?.let { nonNullZipFile ->
                        shareFile(nonNullZipFile)
                    } ?: run {
                        Log.e(TAG, "Failed to create zip file")
                    }
                }
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

                    if (checkExcelBool && fileName.contains(fFile) && fileName.contains("table")) {
                        oldDoc.renameTo(newName)
                    }
                }
            }
        }

        return filename
    }

    inner class ExportDataTask(private val fieldId: Int) : AsyncTask<Void, Void, Int>() {

        private var fail = false
        private var noData = false
        private lateinit var dialog: ProgressDialog
        var exportedFile: DocumentFile? = null

        override fun onPreExecute() {
            super.onPreExecute()
            fail = false

            dialog = ProgressDialog(context)
            dialog.isIndeterminate = true
            dialog.setCancelable(false)
            dialog.setMessage(Html.fromHtml(context.getString(R.string.export_progress)))
            dialog.show()
        }

        override fun doInBackground(vararg params: Void?): Int {
            //flag telling if the user checked the media bundle option
            val bundleChecked = ep.getBoolean(GeneralKeys.DIALOG_EXPORT_BUNDLE_CHECKED, false)
            val isOnlyUniqueChecked = onlyUnique?.isChecked == true
            val isAllColumnsChecked = allColumns?.isChecked == true

            newRange.clear()

            if (isOnlyUniqueChecked) {
                newRange.add(ep.getString(GeneralKeys.UNIQUE_NAME, "") ?: "")
            }

            if (isAllColumnsChecked) {
                val columns = database.getRangeColumns()
                newRange.addAll(columns)
            }

            val newRanges = newRange.toTypedArray()

            // Retrieves the data needed for export
            val exportData = database.getExportDBData(newRanges, exportTrait, fieldId)

            newRanges.forEach {
                Log.i("Field Book : Ranges : ", it)
            }

            exportTrait.forEach {
                Log.i("Field Book : Traits : ", it.name)
            }

            if (exportData.count == 0) {
                noData = true
                return 0
            }

            // Separate files for table and database
            var dbFile: DocumentFile? = null
            var tableFile: DocumentFile? = null

            val traits = database.getAllTraitObjects()

            // Check if export database has been selected
            if (checkDbBool) {
                if (exportData.count > 0) {
                    try {
                        val exportFileName = "${exportFileString}_database.csv"
                        dbFile = BaseDocumentTreeUtil.getFile(context, R.string.dir_field_export, exportFileName)
                        val exportDir = BaseDocumentTreeUtil.getDirectory(context, R.string.dir_field_export)

                        dbFile?.let {
                            if (it.exists()) it.delete()
                        }

                        exportDir?.let {
                            if (it.exists()) dbFile = it.createFile("*/*", exportFileName)
                        }

                        val output = BaseDocumentTreeUtil.getFileOutputStream(context, R.string.dir_field_export, exportFileName)

                        output?.let {
                            val fw = OutputStreamWriter(it)
                            val csvWriter = CSVWriter(fw, exportData)
                            csvWriter.writeDatabaseFormat(newRange)
                        }

                    } catch (e: Exception) {
                        fail = true
                    }
                }
            }

            // Check if export table has been selected
            if (checkExcelBool) {
                if (exportData.count > 0) {
                    try {
                        val tableFileName = "${exportFileString}_table.csv"
                        tableFile = BaseDocumentTreeUtil.getFile(context, R.string.dir_field_export, tableFileName)
                        val exportDir = BaseDocumentTreeUtil.getDirectory(context, R.string.dir_field_export)

                        tableFile?.let {
                            if (it.exists()) it.delete()
                        }

                        exportDir?.let {
                            if (it.exists()) tableFile = it.createFile("*/*", tableFileName)
                        }

                        val output = BaseDocumentTreeUtil.getFileOutputStream(context, R.string.dir_field_export, tableFileName)
                        val fw = OutputStreamWriter(output)

                        val convertedExportData = database.convertDatabaseToTable(newRanges, exportTrait)
                        val csvWriter = CSVWriter(fw, convertedExportData)

                        val labels = ArrayList<String>()
                        for (trait in exportTrait) labels.add(trait.name)
                        csvWriter.writeTableFormat(newRanges.plus(labels), newRanges.size, traits)

                    } catch (e: Exception) {
                        fail = true
                    }
                }
            }

            // If the export did not fail, create a zip file with the exported files
            if (!fail) {
                val zipFileName = "${exportFileString}.zip"

                // If bundle is checked, zip the files with the media dir for the given study
                if (bundleChecked) {
                    val studyId = ep.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)

                    // Study name is the same as the media directory in plot_data
                    val fieldObject = database.getFieldObject(studyId)

                    fieldObject?.let {
                        val studyName = it.exp_name

                        // Create media dir directory from fieldBook/plot_data/studyName path
                        val mediaDir = BaseDocumentTreeUtil.getFile(context, R.string.dir_plot_data, studyName)

                        mediaDir?.let { dir ->
                            if (dir.exists()) {
                                val exportDir = BaseDocumentTreeUtil.getDirectory(context, R.string.dir_field_export)

                                exportDir?.let { expDir ->
                                    if (expDir.exists()) {
                                        val zipFile = expDir.createFile("*/*", zipFileName)

                                        val output = BaseDocumentTreeUtil.getFileOutputStream(context, R.string.dir_field_export, zipFileName)

                                        output?.let { outStream ->
                                            val paths = arrayListOf<DocumentFile>()
                                            dbFile?.let { paths.add(it) }
                                            tableFile?.let { paths.add(it) }
                                            paths.add(dir)

                                            ZipUtil.zip(context, paths.toTypedArray(), outStream)

                                            dbFile?.delete()
                                            tableFile?.delete()

                                            zipFile?.let { nonNullZipFile ->
                                                shareFile(nonNullZipFile)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val exportDir = BaseDocumentTreeUtil.getDirectory(context, R.string.dir_field_export)

                    exportDir?.let {
                        if (it.exists()) {
                            if (checkDbBool && checkExcelBool) {
                                val zipFile = it.createFile("*/*", zipFileName)

                                val output = BaseDocumentTreeUtil.getFileOutputStream(context, R.string.dir_field_export, zipFileName)

                                output?.let { outStream ->
                                    val paths = arrayListOf<DocumentFile>()
                                    dbFile?.let { paths.add(it) }
                                    tableFile?.let { paths.add(it) }

                                    ZipUtil.zip(context, paths.toTypedArray(), outStream)

                                    dbFile?.delete()
                                    tableFile?.delete()

                                    zipFile?.let { nonNullZipFile ->
                                        shareFile(nonNullZipFile)
                                    }
                                }
                            } else if (checkDbBool) {
                                dbFile?.let { nonNullDbFile ->
                                    shareFile(nonNullDbFile)
                                }
                            } else if (checkExcelBool) {
                                tableFile?.let { nonNullTableFile ->
                                    shareFile(nonNullTableFile)
                                }
                            }
                        }
                    }
                }
            }

            if (!fail) {
                // Collect exported files (either dbFile or tableFile)
                exportedFile = if (checkDbBool) dbFile else tableFile
            }

            return 0
        }

        override fun onPostExecute(result: Int?) {
            newRange.clear()

            if (dialog.isShowing) {
                dialog.dismiss()
            }

            if (!fail) {
                showCitationDialog()
                database.updateExpTable(false, false, true, ep.getInt(GeneralKeys.SELECTED_FIELD_ID, 0))
            }

            if (fail) {
                Toast.makeText(context, context.getString(R.string.export_error_general), Toast.LENGTH_SHORT).show()
            }

            if (noData) {
                Toast.makeText(context, context.getString(R.string.export_error_data_missing), Toast.LENGTH_SHORT).show()
            }
        }
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
