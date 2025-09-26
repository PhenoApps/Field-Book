package com.fieldbook.tracker.activities

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Rect
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.brapi.BrapiActivity
import com.fieldbook.tracker.activities.brapi.io.BrapiFilterCache
import com.fieldbook.tracker.activities.brapi.io.filter.filterer.BrapiStudyFilterActivity
import com.fieldbook.tracker.adapters.FieldAdapter
import com.fieldbook.tracker.async.ImportRunnableTask
import com.fieldbook.tracker.brapi.BrapiInfoDialogFragment
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.models.ObservationUnitModel
import com.fieldbook.tracker.dialogs.FieldSortDialogFragment
import com.fieldbook.tracker.dialogs.ListAddDialog
import com.fieldbook.tracker.dialogs.ListSortDialog
import com.fieldbook.tracker.interfaces.FieldSortController
import com.fieldbook.tracker.location.GPSTracker
import com.fieldbook.tracker.objects.FieldFileObject
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.utilities.SnackbarUtils
import com.fieldbook.tracker.utilities.TapTargetUtil
import com.fieldbook.tracker.utilities.Utils
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.phenoapps.utils.BaseDocumentTreeUtil
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import java.io.IOException
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.NoSuchElementException
import java.util.StringJoiner
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import androidx.core.content.edit
import androidx.core.net.toUri

@AndroidEntryPoint
class FieldEditorActivity : BaseFieldActivity(), FieldSortController {

    companion object {
        private const val TAG = "FieldEditor"
        private const val PERMISSIONS_REQUEST_STORAGE = 998
    }

    private lateinit var fieldFile: FieldFileObject.FieldFileBase
    private lateinit var unique: Spinner
    private lateinit var mGpsTracker: GPSTracker

    private val importRunnable = Runnable {
        ImportRunnableTask(
            this@FieldEditorActivity,
            fieldFile,
            unique.selectedItemPosition,
            unique.selectedItem.toString()
        ).execute(0)
    }

    private val fileExplorerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val chosenFile = result.data?.getStringExtra(FileExploreActivity.EXTRA_RESULT_KEY)
                chosenFile?.let { showFieldFileDialog(it, null) }
            }
        }

    private val cloudFileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data?.data != null) {
                val contentDescriber = result.data?.data ?: return@registerForActivityResult
                val chosenFile = getFileName(contentDescriber) ?: return@registerForActivityResult

                val extension = FieldFileObject.getExtension(chosenFile)
                if (extension != "csv" && extension != "xls" && extension != "xlsx") {
                    Utils.makeToast(this, getString(R.string.import_error_format_field))
                    return@registerForActivityResult
                }

                try { // copy the file to dir_field_import directory
                    val importDir = R.string.dir_field_import
                    var file = BaseDocumentTreeUtil.getDirectory(this, importDir)?.findFile(chosenFile)

                    if (file == null || !file.exists()) { // create the file
                        file = BaseDocumentTreeUtil.getDirectory(this, importDir)?.createFile("*/*", chosenFile)
                    }

                    contentResolver.openInputStream(contentDescriber)?.use { input ->
                        BaseDocumentTreeUtil.getFileOutputStream(
                            this,
                            importDir,
                            chosenFile
                        )?.use { output -> // copy file contents
                            val buffer = ByteArray(1024)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                            }
                        } ?: throw IOException()
                    } ?: throw IOException()

                } catch (e: IOException) {
                    Log.e(TAG, "Error while copying the file to field_import: ${e.message}", e)
                }
                showFieldFileDialog(contentDescriber.toString(), true)
            }
        }

    private val brapiImportLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val fieldId = result.data?.getIntExtra("fieldId", -1) ?: -1
                if (fieldId != -1) {
                    fieldSwitcher.switchField(fieldId)
                    val dialogFragment = BrapiInfoDialogFragment().newInstance(resources.getString(R.string.brapi_info_message))
                    dialogFragment.show(this.supportFragmentManager, "brapiInfoDialogFragment")
                }
            }
        }

    private val fieldCreatorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val fieldId = result.data?.getIntExtra("fieldId", -1) ?: -1
                if (fieldId != -1) {
                    fieldSwitcher.switchField(fieldId)
                    queryAndLoadFields()
                    startFieldDetailFragment(fieldId)
                }
            }
        }

    override fun getLayoutResourceId(): Int = R.layout.activity_fields

    override fun getToolbarId(): Int = R.id.field_toolbar

    override fun getRecyclerViewId(): Int = R.id.fieldRecyclerView

    override fun getSearchBarId(): Int = R.id.act_fields_sb

    override fun getScreenTitle(): String = getString(R.string.settings_fields)

    override fun getMenuResourceId(): Int = R.menu.menu_fields

    override fun isArchivedMode(): Boolean = false

    // override fun onCreateOptionsMenu(menu: Menu): Boolean {
    //     if (menu.javaClass.simpleName == "MenuBuilder") {
    //         try {
    //             val m = menu.javaClass.getDeclaredMethod("setOptionalIconsVisible", Boolean::class.java)
    //             m.isAccessible = true
    //             m.invoke(menu, true)
    //         } catch (e: NoSuchMethodException) {
    //             Log.e(TAG, "onMenuOpened", e)
    //         } catch (e: Exception) {
    //             throw RuntimeException(e)
    //         }
    //     }
    //     return super.onCreateOptionsMenu(menu)
    // }

    override fun onResume() {
        super.onResume()

        if (systemMenu != null) {
            systemMenu!!.findItem(R.id.help).isVisible = mPrefs.getBoolean(PreferenceKeys.TIPS, false)
        }

        mGpsTracker = GPSTracker(this)
    }

    override fun initializeAdapter() {
        mAdapter = FieldAdapter(this, this, fieldGroupController, false)
        mAdapter.setOnFieldActionListener(object : FieldAdapter.OnFieldActionListener {
            override fun onFieldDetailSelected(fieldId: Int) {
                startFieldDetailFragment(fieldId)
            }

            override fun onFieldSetActive(fieldId: Int) {
                setActiveField(fieldId)
            }
        })
        recyclerView.adapter = mAdapter
    }

    override fun setupViews() {
        val fab = findViewById<FloatingActionButton>(R.id.newField)
        fab.setOnClickListener { handleImportAction() }
        fab.setOnLongClickListener {
            showFileDialog()
            true
        }
    }

    override fun loadFields(): ArrayList<FieldObject> = db.allFieldObjects

    override fun updateMenuItemsForSelectionMode(menu: Menu) {
        // call super to handle common items
        super.updateMenuItemsForSelectionMode(menu)

        val groupToggleItem = menu.findItem(R.id.toggle_group_visibility)
        val helpItem = menu.findItem(R.id.help)
        val nearestPlotItem = menu.findItem(R.id.action_select_plot_by_distance)
        val sortFieldsItem = menu.findItem(R.id.sortFields)
        val groupFieldsItem = menu.findItem(R.id.menu_group_fields)
        val archiveFieldsItem = menu.findItem(R.id.menu_archive_fields)

        val collapseGroupsItem = menu.findItem(R.id.collapseGroups)
        val expandGroupsItem = menu.findItem(R.id.expandGroups)

        val standardMenuItems = arrayOf(helpItem, nearestPlotItem, sortFieldsItem, groupFieldsItem)

        val isGroupingPossible = areFieldsGrouped()
        var isGroupingEnabled = mPrefs.getBoolean(GeneralKeys.FIELD_GROUPING_ENABLED, false)
        val userHasToggledGrouping =
            mPrefs.getBoolean(GeneralKeys.USER_TOGGLED_FIELD_GROUPING, false)

        if (!isGroupingPossible) { // if grouping is not possible, force disable grouping state
            mPrefs.edit {
                putBoolean(GeneralKeys.FIELD_GROUPING_ENABLED, false)
                putBoolean(GeneralKeys.USER_TOGGLED_FIELD_GROUPING, false) // set user toggle to false since forced
            }
        } else if (!isGroupingEnabled && !userHasToggledGrouping) { // grouping was disabled AND user did not toggle it, enable grouping
            mPrefs.edit { putBoolean(GeneralKeys.FIELD_GROUPING_ENABLED, true) }
            // if user had toggled grouping to false using the menu item
            // then not including !userHasToggledGrouping in the condition would set the grouping to true
        }

        isGroupingEnabled = mPrefs.getBoolean(GeneralKeys.FIELD_GROUPING_ENABLED, false)

        groupToggleItem.setIcon(if (isGroupingEnabled) R.drawable.ic_ungroup else R.drawable.ic_existing_group)

        mAdapter.resetFieldsList(fieldList)

        if (mAdapter.selectedItemCount > 0) { // in selection mode
            standardMenuItems.forEach { toggleMenuItem(it, false) }

            toggleMenuItem(groupToggleItem, false)
            toggleMenuItem(collapseGroupsItem, false)
            toggleMenuItem(expandGroupsItem, false)

            toggleMenuItem(groupFieldsItem, true)
            toggleMenuItem(archiveFieldsItem, true)
        } else { // not in selection mode
            toggleMenuItem(helpItem, mPrefs.getBoolean(PreferenceKeys.TIPS, false))
            toggleMenuItem(nearestPlotItem, true)
            toggleMenuItem(sortFieldsItem, true)

            // show these items only if grouping is possible or enabled
            toggleMenuItem(groupToggleItem, isGroupingPossible)
            toggleMenuItem(collapseGroupsItem, isGroupingEnabled)
            toggleMenuItem(expandGroupsItem, isGroupingEnabled)

            toggleMenuItem(groupFieldsItem, false)
            toggleMenuItem(archiveFieldsItem, false)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                CollectActivity.reloadData = true
                finish()
                true
            }

            R.id.help -> {
                showHelpTutorial()
                true
            }

            R.id.action_select_plot_by_distance -> {
                if (mGpsTracker.canGetLocation()) {
                    selectPlotByDistance()
                } else {
                    Toast.makeText(
                        this,
                        R.string.activity_field_editor_no_location_yet,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                true
            }

            R.id.sortFields -> {
                showFieldsSortDialog()
                true
            }

            R.id.toggle_group_visibility -> {
                val groupingEnabled = mPrefs.getBoolean(GeneralKeys.FIELD_GROUPING_ENABLED, false)
                mPrefs.edit {
                    putBoolean(GeneralKeys.FIELD_GROUPING_ENABLED, !groupingEnabled)
                    putBoolean(GeneralKeys.USER_TOGGLED_FIELD_GROUPING, true)
                }

                queryAndLoadFields()

                Handler(Looper.getMainLooper()).postDelayed(
                    { recyclerView.scrollToPosition(0) },
                    100
                )
                true
            }

            R.id.collapseGroups -> {
                mAdapter.changeStateOfAllGroups(false)
                true
            }

            R.id.expandGroups -> {
                mAdapter.changeStateOfAllGroups(true)
                true
            }

            R.id.menu_group_fields -> {
                showGroupAssignmentDialog(mAdapter.selectedItems)
                mAdapter.exitSelectionMode()
                invalidateOptionsMenu()
                true
            }

            R.id.menu_archive_fields -> {
                archiveSelectedFields(mAdapter.selectedItems)
                true
            }

            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    private fun showHelpTutorial() {
        val sequence = TapTargetSequence(this)
            .targets(
                fieldsTapTargetMenu(
                    R.id.newField,
                    getString(R.string.tutorial_fields_add_title),
                    getString(R.string.tutorial_fields_add_description),
                    60
                ),
                fieldsTapTargetMenu(
                    R.id.newField,
                    getString(R.string.tutorial_fields_add_title),
                    getString(R.string.tutorial_fields_file_description),
                    60
                )
            )

        if (fieldExists()) {
            sequence.target(
                fieldsTapTargetRect(
                    fieldsListItemLocation(0),
                    getString(R.string.tutorial_fields_select_title),
                    getString(R.string.tutorial_fields_select_description)
                )
            )
            sequence.target(
                fieldsTapTargetRect(
                    fieldsListItemLocation(0),
                    getString(R.string.tutorial_fields_delete_title),
                    getString(R.string.tutorial_fields_delete_description)
                )
            )
        }

        sequence.start()
    }

    private fun fieldsListItemLocation(item: Int): Rect {
        val v = recyclerView.getChildAt(item)
        val location = IntArray(2)
        v.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + v.width / 5, location[1] + v.height)
    }

    private fun fieldsTapTargetRect(item: Rect, title: String, desc: String): TapTarget {
        return TapTargetUtil.getTapTargetSettingsRect(this, item, title, desc)
    }

    private fun fieldsTapTargetMenu(
        id: Int,
        title: String,
        desc: String,
        targetRadius: Int
    ): TapTarget {
        return TapTargetUtil.getTapTargetSettingsView(
            this,
            findViewById(id),
            title,
            desc,
            targetRadius
        )
    }

    // TODO
    private fun fieldExists(): Boolean {
        return false
    }

    private fun handleImportAction() {
        val importer = mPrefs.getString("IMPORT_SOURCE_DEFAULT", "ask")
        when (importer) {
            "ask" -> showFileDialog()
            "local" -> loadLocal()
            "brapi" -> loadBrAPI()
            "cloud" -> loadCloud()
            else -> showFileDialog()
        }
    }

    private fun showFieldsSortDialog() {
        val sortOptions = LinkedHashMap<String, String>()
        val defaultSortOrder = "date_import"
        val currentSortOrder =
            mPrefs.getString(GeneralKeys.FIELDS_LIST_SORT_ORDER, defaultSortOrder)

        sortOptions[getString(R.string.fields_sort_by_name)] = "study_alias"
        sortOptions[getString(R.string.fields_sort_by_import_format)] = "import_format"
        sortOptions[getString(R.string.fields_sort_by_import_date)] = "date_import"
        sortOptions[getString(R.string.fields_sort_by_edit_date)] = "date_edit"
        sortOptions[getString(R.string.fields_sort_by_sync_date)] = "date_sync"
        sortOptions[getString(R.string.fields_sort_by_export_date)] = "date_export"

        val dialog =
            ListSortDialog(this, sortOptions, currentSortOrder, defaultSortOrder) { criteria ->
                Log.d(TAG, "Updating fields list sort order to : $criteria")
                mPrefs.edit { putString(GeneralKeys.FIELDS_LIST_SORT_ORDER, criteria) }
                queryAndLoadFields()
            }
        dialog.show()
    }

    private fun areFieldsGrouped(): Boolean {
        val allStudyGroups = db.allStudyGroups

        val hasArchivedFields = fieldList.any { it.archived }
        val hasGroups = allStudyGroups?.isNotEmpty() == true
        return hasArchivedFields || hasGroups
    }

    private fun showFileDialog() {
        val importArray = arrayOfNulls<String>(
            if (mPrefs.getBoolean(
                    PreferenceKeys.BRAPI_ENABLED,
                    false
                )
            ) 4 else 3
        )
        importArray[0] = getString(R.string.import_source_local)
        importArray[1] = getString(R.string.import_source_cloud)
        importArray[2] = getString(R.string.fields_new_create_field)

        if (mPrefs.getBoolean(PreferenceKeys.BRAPI_ENABLED, false)) {
            val displayName = mPrefs.getString(
                PreferenceKeys.BRAPI_DISPLAY_NAME,
                getString(R.string.brapi_edit_display_name_default)
            )
            importArray[3] = displayName
        }

        val icons = IntArray(importArray.size)
        icons[0] = R.drawable.ic_file_generic
        icons[1] = R.drawable.ic_file_cloud
        icons[2] = R.drawable.ic_field
        if (importArray.size > 3) {
            icons[3] = R.drawable.ic_adv_brapi
        }

        val onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            when (position) {
                0 -> if (checkDirectory()) loadLocalPermission()
                1 -> if (checkDirectory()) loadCloud()
                2 -> {
                    fieldCreatorLauncher.launch(FieldCreatorActivity.getIntent(this))
                }

                3 -> loadBrAPI()
            }
        }

        val dialog = ListAddDialog(
            this,
            getString(R.string.fields_new_dialog_title),
            importArray.filterNotNull().toTypedArray(),
            icons,
            onItemClickListener
        )
        dialog.show(supportFragmentManager, "ListAddDialog")
    }

    @AfterPermissionGranted(PERMISSIONS_REQUEST_STORAGE)
    fun loadLocalPermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val perms = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (EasyPermissions.hasPermissions(this, *perms)) {
                loadLocal()
            } else {
                // Do not have permissions, request them now
                EasyPermissions.requestPermissions(
                    this, getString(R.string.permission_rationale_storage_import),
                    PERMISSIONS_REQUEST_STORAGE, *perms
                )
            }
        } else loadLocal()
    }

    fun loadLocal() {
        try {
            val importDir = BaseDocumentTreeUtil.getDirectory(this, R.string.dir_field_import)
            if (importDir != null && importDir.exists()) {
                fileExplorerLauncher.launch(FileExploreActivity.getIntent(this).apply {
                    putExtra("path", importDir.uri.toString())
                    putExtra("include", arrayOf("csv", "xls", "xlsx"))
                    putExtra("title", getString(R.string.fields_new_dialog_title))
                })
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadCloud() {
        try {
            cloudFileLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadBrAPI() {
        if (Utils.isConnected(this)) {
            if (mPrefs.getBoolean(PreferenceKeys.EXPERIMENTAL_NEW_BRAPI_UI, true)) {
                BrapiFilterCache.checkClearCache(this)
                brapiImportLauncher.launch(BrapiStudyFilterActivity.getIntent(this))
            } else {
                brapiImportLauncher.launch(BrapiActivity.getIntent(this))
            }
        } else {
            Toast.makeText(this, R.string.opening_brapi_no_network_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkDirectory(): Boolean {
        if (BaseDocumentTreeUtil.getRoot(this) != null
            && BaseDocumentTreeUtil.isEnabled(this)
            && BaseDocumentTreeUtil.getDirectory(this, R.string.dir_field_import) != null
        ) {
            return true
        } else {
            Toast.makeText(this, R.string.error_storage_directory, Toast.LENGTH_LONG).show()
            return false
        }
    }

    /**
     * Programmatically selects the closest field to the user's location.
     * Finds all observation units with geo-coordinate data, sorts the list and finds the first item.
     */
    private fun selectPlotByDistance() {
        if (mGpsTracker.canGetLocation()) {
            // get current coordinate of the user
            val thisLocation = mGpsTracker.location

            val units = db.allObservationUnits
            val coordinates = ArrayList<ObservationUnitModel>()

            // find all observation units with a coordinate
            for (model in units) {
                val latlng = model.geo_coordinates
                if (latlng != null && latlng.isNotEmpty()) {
                    val study = db.getFieldObject(model.study_id)
                    if (study != null && !study.archived) { // do not add archived field coordinates
                        coordinates.add(model)
                    }
                }
            }

            // sort the coordinates based on the distance from the user
            coordinates.sortWith(Comparator { a, b ->
                val distanceA = distanceTo(thisLocation, a.getLocation())
                val distanceB = distanceTo(thisLocation, b.getLocation())
                distanceA.compareTo(distanceB)
            })

            try {
                val closest = coordinates.stream().findFirst()

                if (closest.isPresent) {
                    val model = closest.get()
                    val studyId = model.study_id
                    val study = db.getFieldObject(studyId)
                    val studyName = study.alias

                    if (studyId == mPrefs.getInt(GeneralKeys.SELECTED_FIELD_ID, -1)) {
                        SnackbarUtils.showNavigateSnack(
                            layoutInflater,
                            findViewById(R.id.main_content),
                            getString(R.string.activity_field_editor_switch_field_same),
                            null, 8000, null, null
                        )
                    } else {
                        SnackbarUtils.showNavigateSnack(
                            layoutInflater,
                            findViewById(R.id.main_content),
                            getString(R.string.activity_field_editor_switch_field, studyName),
                            null, 8000, null
                        ) {
                            fieldSwitcher.switchField(studyId)
                            queryAndLoadFields()
                        }
                    }
                }
            } catch (_: NoSuchElementException) {
                Toast.makeText(
                    this,
                    R.string.activity_field_editor_no_field_found,
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            Toast.makeText(this, R.string.activity_field_editor_no_location_yet, Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun distanceTo(thisLocation: Location, targetLocation: Location?): Double {
        return targetLocation?.let { thisLocation.distanceTo(it).toDouble() } ?: Double.MAX_VALUE
    }

    private fun showFieldFileDialog(chosenFile: String, isCloud: Boolean?) {
        try {
            val docUri = chosenFile.toUri()
            val importDoc = DocumentFile.fromSingleUri(this, docUri) ?: return

            var cloudName = if (isCloud != null && isCloud) {
                getFileName(docUri)
            } else {
                if (!importDoc.exists()) return
                null
            }

            contentResolver.openInputStream(docUri)?.use { inputStream ->
                fieldFile = FieldFileObject.create(this, docUri, inputStream, cloudName)

                val fieldFileName = fieldFile.stem
                if (isCloud != null && isCloud && cloudName != null) {
                    val index = cloudName.lastIndexOf(".")
                    if (index > -1) {
                        cloudName = cloudName.substring(0, index)
                    }
                    fieldFile.name = cloudName
                }

                mPrefs.edit { putString(GeneralKeys.FIELD_FILE, fieldFileName) }

                if (fieldFile.isOther()) {
                    Utils.makeToast(this, getString(R.string.import_error_unsupported))
                }

                loadFile(fieldFile)
            } ?: throw IOException()
        } catch (e: Exception) {
            Utils.makeToast(this, getString(R.string.act_field_editor_load_file_failed))
            e.printStackTrace()
        }
    }

    fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index > -1) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = cut?.let { result.substring(it + 1) }
            }
        }
        return result
    }

    private fun loadFile(fieldFile: FieldFileObject.FieldFileBase) {
        if (fieldFile.openFailed) {
            Utils.makeToast(this, getString(R.string.act_field_editor_file_open_failed))
            return
        }

        val importColumns = fieldFile.columns ?: run {
            // in some cases getColumns is returning null, so print an error message to the user
            Utils.makeToast(this, getString(R.string.act_field_editor_failed_to_read_columns))
            return
        }

        // only reserved word for now is id which is used in many queries
        // other sqlite keywords are sanitized with a tick mark to make them an identifier
        val reservedNames = arrayOf("id")
        val list = listOf(*reservedNames)

        // replace specials and emptys and add them to the actual columns list to be displayed
        val actualColumns = ArrayList<String>()

        // define flag to let the user know characters were replaced at the end of the loop
        var hasSpecialCharacters = false
        for (columnName in importColumns) {
            if (list.contains(columnName.lowercase(Locale.ROOT))) {
                Utils.makeToast(this, "${getString(R.string.import_error_column_name)} \"$columnName\"")
                return
            }

            // replace the special characters, only add to the actual list if it is not empty
            if (DataHelper.hasSpecialChars(columnName)) {
                hasSpecialCharacters = true
                val replaced = DataHelper.replaceSpecialChars(columnName)
                if (replaced.isNotEmpty() && !actualColumns.contains(replaced)) {
                    actualColumns.add(replaced)
                }
            } else if (columnName.isNotEmpty()) { // handle normal column
                actualColumns.add(columnName)
            }
        }

        if (actualColumns.isNotEmpty()) {
            if (hasSpecialCharacters) {
                Utils.makeToast(this, getString(R.string.import_error_columns_replaced))
            }
            importDialog(actualColumns.toTypedArray())
        } else {
            Toast.makeText(
                this, R.string.act_field_editor_no_suitable_columns_error,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun importDialog(columns: Array<String>) {
        val inflater = this.layoutInflater
        val layout = inflater.inflate(R.layout.dialog_field_file_import, null)

        unique = layout.findViewById(R.id.uniqueSpin)
        setSpinner(unique, columns)

        AlertDialog.Builder(this, R.style.AppAlertDialog)
            .setTitle(R.string.fields_new_dialog_title)
            .setCancelable(true)
            .setView(layout)
            .setPositiveButton(getString(R.string.dialog_import)) { _, _ ->
                Handler().post(importRunnable)
            }
            .show()
    }

    // Helper function to set spinner adapter and listener
    private fun setSpinner(spinner: Spinner, data: Array<String>) {
        val itemsAdapter = ArrayAdapter(this, R.layout.custom_spinner_layout, data)
        spinner.adapter = itemsAdapter
        val spinnerPosition = itemsAdapter.getPosition(
            mPrefs.getString(
                GeneralKeys.UNIQUE_NAME,
                itemsAdapter.getItem(0)
            )
        )
        spinner.setSelection(spinnerPosition)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun showSortDialog(field: FieldObject?) {
        val order = field?.sortColumnsStringArray
        val sortOrderList = ArrayList<String>()

        if (order != null) {
            val orderList = order.split(",")
            if (orderList.isNotEmpty() && orderList.first().isNotEmpty()) {
                sortOrderList.addAll(orderList)
            }
        }

        // initialize: initial items are the current sort order, selectable items are the obs. unit attributes.
        val dialogFragment = field?.let { field ->
            FieldSortDialogFragment().newInstance(
                this,
                field,
                sortOrderList.toTypedArray(),
                db.getAllObservationUnitAttributeNames(field.studyId)
            )
        }

        dialogFragment?.show(this.supportFragmentManager, "FieldSortDialogFragment")
    }

    override fun submitSortList(field: FieldObject?, attributes: Array<String?>?) {
        if (field == null) return

        val joiner = StringJoiner(",")
        attributes?.filterNotNull()?.forEach { joiner.add(it) }

        val sortOrder = joiner.toString()

        try {
            db.updateStudySort(sortOrder, field.studyId)

            field.sortColumnsStringArray = sortOrder

            if (mPrefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0) == field.studyId) {
                fieldSwitcher.switchField(field)
                CollectActivity.reloadData = true
            }

            Toast.makeText(this, R.string.sort_dialog_saved, Toast.LENGTH_LONG).apply {
                setGravity(Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL, 0, 0)
                show()
            }

            // Refresh the fragment's data
            val fragment =
                supportFragmentManager.findFragmentByTag("FieldDetailFragmentTag") as? FieldDetailFragment
            fragment?.loadFieldDetails()

        } catch (e: Exception) {
            Log.e(TAG, "Error updating sorting", e)

            AlertDialog.Builder(this, R.style.AppAlertDialog)
                .setTitle(R.string.dialog_save_error_title)
                .setPositiveButton(R.string.dialog_ok) { _, _ ->
                    Log.d("FieldAdapter", "Sort save error dialog dismissed")
                }
                .setMessage(R.string.sort_dialog_error_saving)
                .create()
                .show()
        }

        queryAndLoadFields()
    }

    private fun showGroupAssignmentDialog(fieldIds: List<Int>) {
        db.deleteUnusedStudyGroups()

        val allStudyGroups = db.allStudyGroups
        val hasStudyGroups = allStudyGroups != null && allStudyGroups.isNotEmpty()

        // Check if any of the selected fields are in a group
        var anyFieldsInGroup = false

        for (fieldId in fieldIds) {
            val field = fieldList.find { it.studyId == fieldId }

            if (field != null) {
                val groupName = db.getStudyGroupNameById(field.groupId)
                if (groupName != null && groupName.isNotEmpty()) {
                    anyFieldsInGroup = true
                    break
                }
            }
        }

        val optionStrings = ArrayList<String>()
        val optionIcon = ArrayList<Int>()

        // "existing group" option
        if (hasStudyGroups) {
            optionStrings.add(getString(R.string.group_existing))
            optionIcon.add(R.drawable.ic_existing_group)
        }

        // "new group" option
        optionStrings.add(getString(R.string.group_new))
        optionIcon.add(R.drawable.ic_new_group)

        // "remove from group" option
        if (anyFieldsInGroup) {
            optionStrings.add(getString(R.string.group_remove))
            optionIcon.add(R.drawable.ic_ungroup)
        }

        val options = optionStrings.toTypedArray()
        val icons = optionIcon.toIntArray()

        val onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selectedOption = options[position]

            when (selectedOption) {
                getString(R.string.group_existing) -> showExistingGroupsDialog(fieldIds)
                getString(R.string.group_new) -> showNewGroupDialog(fieldIds)
                getString(R.string.group_remove) -> {
                    for (fieldId in fieldIds) {
                        db.updateStudyGroup(fieldId, null)
                    }
                    queryAndLoadFields()
                    mAdapter.exitSelectionMode()
                    db.deleteUnusedStudyGroups()
                }
            }
        }

        val dialog = ListAddDialog(
            this,
            getString(R.string.dialog_group_options),
            options,
            icons,
            onItemClickListener
        )
        dialog.show(supportFragmentManager, "ListAddDialog")
    }

    private fun showExistingGroupsDialog(fieldIds: List<Int>) {
        val existingGroups = db.allStudyGroups

        AlertDialog.Builder(this, R.style.AppAlertDialog)
            .setTitle(R.string.group_existing)
            .setItems(existingGroups.map { it.groupName }.toTypedArray()) { _, which ->
                val groupId = existingGroups[which].id

                for (fieldId in fieldIds) {
                    db.updateStudyGroup(fieldId, groupId)
                }
                mAdapter.exitSelectionMode()
                queryAndLoadFields()
                db.deleteUnusedStudyGroups()
            }
            .setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showNewGroupDialog(fieldIds: List<Int>) {
        val inflater = this.layoutInflater
        val layout = inflater.inflate(R.layout.dialog_group_name, null)
        val groupName = layout.findViewById<EditText>(R.id.groupName)

        groupName.clearFocus()

        val dialog = AlertDialog.Builder(this, R.style.AppAlertDialog)
            .setTitle(R.string.create_new_group)
            .setView(layout)
            .setPositiveButton(getString(R.string.dialog_ok), null)
            .setNegativeButton(getString(R.string.dialog_cancel)) { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val groupNameStr = groupName.text.toString().trim()
                if (groupNameStr.isNotEmpty()) { // add selected fields to group
                    for (fieldId in fieldIds) {
                        val groupId = db.createOrGetStudyGroup(groupNameStr)
                        db.updateStudyGroup(fieldId, groupId)
                    }
                    queryAndLoadFields()
                    mAdapter.exitSelectionMode()
                    dialog.dismiss()
                } else {
                    groupName.error = getString(R.string.dialog_group_name_warning)
                }
            }
        }

        dialog.show()
    }

    private fun archiveSelectedFields(fieldIds: List<Int>) {
        val activeFieldId = mPrefs.getInt(GeneralKeys.SELECTED_FIELD_ID, -1)
        val activeFieldInArchiveList = fieldIds.contains(activeFieldId)

        if (!activeFieldInArchiveList) { // active field isn't selected, archive all
            archiveFields(fieldIds)
            return
        }

        val builder = AlertDialog.Builder(this, R.style.AppAlertDialog)
            .setTitle(R.string.archive_active_field_title)
            .setMessage(R.string.archive_active_field_message)
            .setPositiveButton(R.string.dialog_yes) { _, _ ->
                // archive fields and deselect active field
                archiveFields(fieldIds)
                resetActiveField(fieldIds)
            }
            .setNegativeButton(R.string.dialog_cancel) { dialog, _ ->
                dialog.dismiss()
                mAdapter.exitSelectionMode()
                invalidateOptionsMenu()
            }

        val nonActiveFields = fieldIds.filter { it != activeFieldId }
        if (nonActiveFields.isNotEmpty()) {
            builder.setNeutralButton(R.string.archive_others) { _, _ ->
                // archive only the non-active fields
                archiveFields(nonActiveFields)
            }
        }

        val dialog = builder.create()
        dialog.setOnShowListener {
            val params = dialog.window?.attributes
            params?.width = LinearLayout.LayoutParams.MATCH_PARENT
            dialog.window?.attributes = params
        }

        dialog.show()
    }

    private fun archiveFields(fieldIds: List<Int>) {
        for (fieldId in fieldIds) {
            db.setIsArchived(fieldId, true)
        }
        queryAndLoadFields()
        mAdapter.exitSelectionMode()
        invalidateOptionsMenu()
    }
}