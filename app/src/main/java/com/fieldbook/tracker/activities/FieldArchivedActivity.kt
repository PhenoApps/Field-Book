package com.fieldbook.tracker.activities

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ActionMode
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
import com.fieldbook.tracker.utilities.ExportUtil
import com.fieldbook.tracker.utilities.FieldSwitchImpl
import com.fieldbook.tracker.views.SearchBar
import dagger.hilt.android.AndroidEntryPoint
import java.util.ArrayList
import javax.inject.Inject

@AndroidEntryPoint
class FieldArchivedActivity : ThemedActivity(), FieldAdapterController, FieldAdapter.AdapterCallback {

    private val TAG = "FieldArchived"

    private var fieldList: ArrayList<FieldObject> = ArrayList()
    lateinit var mAdapter: FieldAdapter
    private var actionMode: ActionMode? = null
    private var customTitleView: TextView? = null
    lateinit var exportUtil: ExportUtil
    private lateinit var searchBar: SearchBar

    @Inject
    lateinit var db: DataHelper

    @Inject
    lateinit var fieldSwitcher: FieldSwitchImpl

    @Inject
    lateinit var mPrefs: SharedPreferences

    lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_field_archived)
        val toolbar = findViewById<Toolbar>(R.id.field_archived_toolbar)
        setSupportActionBar(toolbar)
        exportUtil = ExportUtil(this, db)

        supportActionBar?.apply {
            title = getString(R.string.archived_fields)
            themedContext
            setDisplayHomeAsUpEnabled(true)
            setHomeButtonEnabled(true)
        }

        recyclerView = findViewById(R.id.field_archived_rv)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        // Initialize adapter
        mAdapter = FieldAdapter(this, fieldSwitcher, this, true)
        mAdapter.setOnFieldSelectedListener { fieldId ->
            val fragment = FieldDetailFragment()
            val args = Bundle()
            args.putInt("fieldId", fieldId)
            fragment.arguments = args

            // Disable touch events on the RecyclerView
            recyclerView.isEnabled = false

            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, fragment, "FieldDetailFragmentTag")
                .addToBackStack(null)
                .commit()
        }
        recyclerView.adapter = mAdapter

        searchBar = findViewById(R.id.act_fields_archived_sb)

        queryAndLoadArchivedFields()
    }

    override fun onResume() {
        super.onResume()

        queryAndLoadArchivedFields()
    }

    // Implementations of methods from FieldAdapter.AdapterCallback
    override fun onItemSelected(selectedCount: Int) {
        if (selectedCount == 0 && actionMode != null) {
            actionMode?.finish()
        } else if (selectedCount > 0 && actionMode == null) {
            actionMode = startSupportActionMode(actionModeCallback)
        }
        actionMode?.let {
            customTitleView?.text = getString(R.string.selected_count, selectedCount)
        }
    }

    override fun onItemClear() {
        actionMode?.finish()
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val inflater = mode.menuInflater
            inflater.inflate(R.menu.cab_archived_menu, menu)

            // Create and style the custom title view
            customTitleView = TextView(this@FieldArchivedActivity).apply {
                setTextColor(Color.BLACK)
                textSize = 18f
            }

            val layoutParams = ActionBar.LayoutParams(
                ActionBar.LayoutParams.WRAP_CONTENT,
                ActionBar.LayoutParams.WRAP_CONTENT
            )
            customTitleView?.layoutParams = layoutParams

            mode.customView = customTitleView

            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.menu_select_all -> {
                    mAdapter.selectAll()
                    val selectedCount = mAdapter.selectedItemCount
                    customTitleView?.text = getString(R.string.selected_count, selectedCount)
                    true
                }
                R.id.menu_export -> {
                    exportUtil.exportMultipleFields(mAdapter.selectedItems)
                    mAdapter.exitSelectionMode()
                    mode.finish()
                    true
                }
                R.id.menu_unarchive_fields -> {
                    for (fieldId in mAdapter.selectedItems) {
                        db.updateFieldGroup(fieldId, null)
                    }
                    queryAndLoadArchivedFields()
                    mAdapter.exitSelectionMode()
                    true
                }
                R.id.menu_delete -> {
                    showDeleteConfirmationDialog(mAdapter.selectedItems, false)
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            mAdapter.exitSelectionMode()
            actionMode = null
        }
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

    private fun getFieldNames(fieldIds: List<Int>): String {
        val fieldNames = fieldIds.flatMap { id ->
            fieldList.filter { it.exp_id == id }
                .map { "<b>${it.exp_alias}</b>" }
        }

        return android.text.TextUtils.join(", ", fieldNames)
    }

    private fun deleteFields(fieldIds: List<Int>) {
        for (fieldId in fieldIds) {
            db.deleteField(fieldId)
        }

        // Check if the active field is among those deleted in order to reset related shared preferences
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

        queryAndLoadArchivedFields()
        mAdapter.exitSelectionMode()
        actionMode?.finish()
        actionMode = null
    }

    private fun queryAndLoadArchivedFields() {
        try {
            val allFields = db.getAllFieldObjects()

            val archivedVal = getString(R.string.group_archived_value)
            fieldList.clear()

            for (field in allFields) {
                if (archivedVal == field.groupName) {
                    fieldList.add(field)
                }
            }

            mAdapter.submitFieldList(ArrayList(fieldList))

            if (fieldList.isEmpty()) {
                // return to FieldEditorActivity
                finish()
                return
            }

            Handler(Looper.getMainLooper()).postDelayed({ setupSearchBar() }, 100)

        } catch (e: Exception) {
            Log.e(TAG, "Error updating archived fields list", e)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
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

    private fun setupSearchBar() {
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

    override fun queryAndLoadFields() {
        queryAndLoadArchivedFields()
    }

    override fun getDatabase(): DataHelper = db

    override fun getPreferences(): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)

    override fun getFieldSwitcher(): FieldSwitcher = fieldSwitcher
}