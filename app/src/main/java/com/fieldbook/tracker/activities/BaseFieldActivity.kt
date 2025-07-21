package com.fieldbook.tracker.activities

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.FieldAdapter
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.interfaces.FieldAdapterController
import com.fieldbook.tracker.interfaces.FieldSwitcher
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.FieldGroupControllerImpl
import com.fieldbook.tracker.utilities.FieldSwitchImpl
import com.fieldbook.tracker.utilities.export.ExportUtil
import com.fieldbook.tracker.views.SearchBar
import dagger.hilt.android.AndroidEntryPoint
import java.util.ArrayList
import javax.inject.Inject

/**
 * Base activity for FieldEditorActivity and FieldArchivedActivity
 * Provides common functionalities like listing, selection, deletion, and search features.
 */
@AndroidEntryPoint
abstract class BaseFieldActivity : ThemedActivity(), FieldAdapterController, FieldAdapter.AdapterCallback {

    companion object {
        const val TAG = "BaseFieldActivity"
    }

    protected lateinit var recyclerView: RecyclerView
    protected lateinit var mAdapter: FieldAdapter
    protected lateinit var searchBar: SearchBar
    protected var systemMenu: Menu? = null

    protected var fieldList: ArrayList<FieldObject> = ArrayList()

    @Inject
    lateinit var db: DataHelper

    @Inject
    lateinit var fieldSwitcher: FieldSwitchImpl

    @Inject
    lateinit var fieldGroupController: FieldGroupControllerImpl

    @Inject
    lateinit var mPrefs: SharedPreferences

    @Inject
    lateinit var exportUtil: ExportUtil

    protected abstract fun getLayoutResourceId(): Int
    protected abstract fun getToolbarId(): Int
    protected abstract fun getRecyclerViewId(): Int
    protected abstract fun getSearchBarId(): Int
    protected abstract fun getScreenTitle(): String
    protected abstract fun getMenuResourceId(): Int
    protected abstract fun isArchivedMode(): Boolean
    protected abstract fun initializeAdapter()
    protected abstract fun setupViews()
    protected abstract fun loadFields(): ArrayList<FieldObject>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(getLayoutResourceId())

        val toolbar = findViewById<Toolbar>(getToolbarId())
        setSupportActionBar(toolbar)

        supportActionBar?.apply {
            title = getScreenTitle()
            themedContext
            setDisplayHomeAsUpEnabled(true)
            setHomeButtonEnabled(true)
        }

        recyclerView = findViewById(getRecyclerViewId())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        initializeAdapter()

        setupViews()

        searchBar = findViewById(getSearchBarId())

        queryAndLoadFields()
    }

    override fun onResume() {
        super.onResume()
        queryAndLoadFields()
    }

    override fun onItemSelected(selectedCount: Int) {
        if (mAdapter.selectedItemCount > 0) {
            supportActionBar?.title = getString(R.string.selected_count, selectedCount)
        } else {
            mAdapter.exitSelectionMode()
            supportActionBar?.title = getScreenTitle()
        }
        invalidateOptionsMenu()
    }

    override fun onItemClear() {
        // Reset the title when exiting selection mode
        supportActionBar?.title = getScreenTitle()
        invalidateOptionsMenu()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(getMenuResourceId(), menu)
        systemMenu = menu
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_select_all -> {
                mAdapter.selectAll()
                invalidateOptionsMenu()
                true
            }
            R.id.menu_export -> {
                exportUtil.exportMultipleFields(mAdapter.selectedItems)
                mAdapter.exitSelectionMode()
                invalidateOptionsMenu()
                true
            }
            R.id.menu_delete -> {
                showDeleteConfirmationDialog(mAdapter.selectedItems, false)
                mAdapter.exitSelectionMode()
                invalidateOptionsMenu()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        updateMenuItemsForSelectionMode(menu)
        return true
    }

    // these are the common menu items in both activities
    protected open fun updateMenuItemsForSelectionMode(menu: Menu) {
        val exportItem = menu.findItem(R.id.menu_export)
        val selectAllItem = menu.findItem(R.id.menu_select_all)
        val deleteFieldsItem = menu.findItem(R.id.menu_delete)

        val selectionMenuItems = arrayOf(
            exportItem,
            selectAllItem,
            deleteFieldsItem
        )

        val isInSelectionMode = mAdapter.selectedItemCount > 0
        for (item in selectionMenuItems) {
            toggleMenuItem(item, isInSelectionMode)
        }
    }

    protected fun toggleMenuItem(item: MenuItem?, enabled: Boolean) {
        item?.isEnabled = enabled
        item?.isVisible = enabled
    }

    fun showDeleteConfirmationDialog(fieldIds: List<Int>, isFromDetailFragment: Boolean) {
        val fieldNames = getFieldNames(fieldIds)
        val message = resources.getQuantityString(R.plurals.fields_delete_confirmation, fieldIds.size, fieldNames)
        val formattedMessage = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            android.text.Html.fromHtml(message, android.text.Html.FROM_HTML_MODE_LEGACY)
        } else {
            android.text.Html.fromHtml(message)
        }

        AlertDialog.Builder(this, R.style.AppAlertDialog)
            .setTitle(getString(R.string.fields_delete_study))
            .setMessage(formattedMessage)
            .setPositiveButton(getString(R.string.dialog_yes)) { _, _ ->
                deleteFields(fieldIds)
                if (isFromDetailFragment) { supportFragmentManager.popBackStack() }
            }
            .setNegativeButton(getString(R.string.dialog_no)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    protected fun getFieldNames(fieldIds: List<Int>): String {
        val fieldNames = fieldIds.flatMap { id ->
            fieldList.filter { it.studyId == id }
                .map { "<b>${it.alias}</b>" }
        }

        return android.text.TextUtils.join(", ", fieldNames)
    }

    protected fun deleteFields(fieldIds: List<Int>) {
        for (fieldId in fieldIds) {
            db.deleteField(fieldId)
        }

        resetActiveField(fieldIds)

        queryAndLoadFields()
        mAdapter.exitSelectionMode()
    }

    protected fun resetActiveField(fieldIds: List<Int>) {
        // Check if the active field is among those deleted/archived in order to reset related shared preferences
        val activeFieldId = mPrefs.getInt(GeneralKeys.SELECTED_FIELD_ID, -1)
        if (fieldIds.contains(activeFieldId)) {
            mPrefs.edit().apply {
                remove(GeneralKeys.FIELD_FILE)
                remove(GeneralKeys.FIELD_ALIAS)
                remove(GeneralKeys.FIELD_OBS_LEVEL)
                putInt(GeneralKeys.SELECTED_FIELD_ID, -1)
                remove(GeneralKeys.UNIQUE_NAME)
                remove(GeneralKeys.PRIMARY_NAME)
                remove(GeneralKeys.SECONDARY_NAME)
                putBoolean(GeneralKeys.IMPORT_FIELD_FINISHED, false)
                remove(GeneralKeys.LAST_PLOT)
                apply()
            }
            CollectActivity.reloadData = true
        }
    }

    override fun queryAndLoadFields() {
        try {
            fieldList = loadFields()
            mAdapter.resetFieldsList(ArrayList(fieldList))

            invalidateOptionsMenu() // invokes onPrepareOptionsMenu

            if (isArchivedMode() && fieldList.isEmpty()) { // if in archive activity and no fields, finish
                finish()
                return
            }

            Handler(Looper.getMainLooper()).postDelayed({ setupSearchBar() }, 100)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating fields list", e)
        }
    }

    protected fun setupSearchBar() {
        if (recyclerView.canScrollVertically(1) || recyclerView.canScrollVertically(-1)) {
            searchBar.visibility = View.VISIBLE

            searchBar.editText.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                    // Do nothing
                }

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    mAdapter.setTextFilter(s.toString())
                }

                override fun afterTextChanged(s: android.text.Editable) {
                    // Do nothing
                }
            })
        } else {
            searchBar.visibility = View.GONE
        }
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            // Return to Fields screen if pressed in detail fragment
            mAdapter.notifyDataSetChanged()
            supportFragmentManager.popBackStack()
            recyclerView.isEnabled = true // Re-enable touch events
        } else {
            super.onBackPressed()
        }
    }

    fun setActiveField(studyId: Int) {
        // get current field id and compare the input, only switch if they are different
        val currentFieldId = mPrefs.getInt(GeneralKeys.SELECTED_FIELD_ID, -1)

        if (currentFieldId == studyId) return

        fieldSwitcher.switchField(studyId)
        CollectActivity.reloadData = true

        mAdapter.resetFieldsList(fieldList) // reset to update active icon indication

        // Check if this is a BrAPI field and show BrAPI info dialog if so
        // if (field.getImport_format() == ImportFormat.BRAPI) {
        //     val brapiInfo = BrapiInfoDialog(this, getResources().getString(R.string.brapi_info_message));
        //     brapiInfo.show();
        // }
    }

    override fun getDatabase(): DataHelper = db

    override fun getPreferences(): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

    override fun getFieldSwitcher(): FieldSwitcher = fieldSwitcher
}