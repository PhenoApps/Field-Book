package com.fieldbook.tracker.activities

import android.app.AlertDialog
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.FieldAdapter
import com.fieldbook.tracker.objects.FieldObject
import dagger.hilt.android.AndroidEntryPoint
import java.util.ArrayList

@AndroidEntryPoint
class FieldArchivedActivity : BaseFieldActivity() {

    companion object {
        const val TAG = "FieldArchived"
    }

    override fun getLayoutResourceId(): Int = R.layout.activity_field_archived

    override fun getToolbarId(): Int = R.id.field_archived_toolbar

    override fun getRecyclerViewId(): Int = R.id.field_archived_rv

    override fun getSearchBarId(): Int = R.id.act_fields_archived_sb

    override fun getScreenTitle(): String = getString(R.string.archived_fields)

    override fun getMenuResourceId(): Int = R.menu.menu_archived_fields

    override fun isArchivedMode(): Boolean = true

    override fun initializeAdapter() {
        mAdapter = FieldAdapter(this, this, fieldGroupController, true)
        mAdapter.setOnFieldActionListener(object : FieldAdapter.OnFieldActionListener {
            override fun onFieldDetailSelected(fieldId: Int) {
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

            override fun onFieldSetActive(fieldId: Int) {
                showUnarchiveDialog(fieldId)
            }
        })
        recyclerView.adapter = mAdapter
    }

    override fun setupViews() {
        // Do nothing specific to set up for archived activity
    }

    override fun loadFields(): ArrayList<FieldObject> {
        val allFields = db.getAllFieldObjects()
        val archivedFields = ArrayList<FieldObject>()

        allFields.forEach {
            if (it.archived) {
                archivedFields.add(it)
            }
        }

        return archivedFields
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.menu_unarchive_fields -> {
                for (fieldId in mAdapter.selectedItems) {
                    db.setIsArchived(fieldId, false)
                }
                queryAndLoadFields()
                mAdapter.exitSelectionMode()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun updateMenuItemsForSelectionMode(menu: Menu) {
        // call super to handle common items
        super.updateMenuItemsForSelectionMode(menu)

        val unarchiveFieldsItem = menu.findItem(R.id.menu_unarchive_fields)
        toggleMenuItem(unarchiveFieldsItem, mAdapter.selectedItemCount > 0)
    }

    private fun showUnarchiveDialog(fieldId: Int) {
        AlertDialog.Builder(this, R.style.AppAlertDialog)
            .setTitle(getString(R.string.dialog_unarchive_field_title))
            .setMessage(getString(R.string.dialog_unarchive_field_message))
            .setPositiveButton(getString(R.string.dialog_yes)) { d, _ ->
                fieldId.let { db.setIsArchived(it, false) }
                setActiveField(fieldId)

                finish() // go back to field editor
            }
            .setNegativeButton(getString(R.string.dialog_no)) { d, _ ->
                d.dismiss()
            }
            .show()
    }
}