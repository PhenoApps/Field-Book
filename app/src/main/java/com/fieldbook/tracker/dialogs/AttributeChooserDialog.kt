package com.fieldbook.tracker.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.activities.FieldEditorActivity
import com.fieldbook.tracker.adapters.AttributeAdapter
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.BackgroundUiTask
import com.google.android.material.tabs.TabLayout

/**
 * A tab layout with tabs: attributes, traits, and other.
 * Each tab will load data into a recycler view that lets user choose infobar prefixes.
 */

open class AttributeChooserDialog(
    private val showTraits: Boolean = true,
    private val showOther: Boolean = true,
    private val uniqueOnly: Boolean = false,
    private val showApplyAllCheckbox: Boolean = false
) : DialogFragment(), AttributeAdapter.AttributeAdapterController {

    companion object {
        const val TAG = "AttributeChooserDialog"
    }

    interface OnAttributeSelectedListener {
        fun onAttributeSelected(label: String)
    }

    private lateinit var tabLayout: TabLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar

    private var applyAllCheckbox: CheckBox? = null
    private var lastApplyToAllState: Boolean = false
    fun getApplyToAllState(): Boolean = lastApplyToAllState

    private var attributes = arrayOf<String>()
    private var traits = arrayOf<TraitObject>()
    private var other = arrayOf<TraitObject>()
    private var onAttributeSelectedListener: OnAttributeSelectedListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_collect_att_chooser, null)

        // Initialize UI elements
        tabLayout = view.findViewById(R.id.dialog_collect_att_chooser_tl)
        recyclerView = view.findViewById(R.id.dialog_collect_att_chooser_lv)
        progressBar = view.findViewById(R.id.dialog_collect_att_chooser_pb)

        recyclerView.layoutManager = LinearLayoutManager(requireActivity())
        recyclerView.adapter = AttributeAdapter(this, null)

        //toggle view of traits/other based on class param
        tabLayout.getTabAt(1)?.view?.visibility = if (showTraits) TabLayout.VISIBLE else TabLayout.GONE
        tabLayout.getTabAt(2)?.view?.visibility = if (showOther) TabLayout.VISIBLE else TabLayout.GONE

        // Add checkbox if requested - add it directly to the root LinearLayout
        if (showApplyAllCheckbox) {
            val checkBox = CheckBox(requireContext()).apply {
                text = getString(R.string.apply_to_all_fields)
                setPadding(50, 20, 50, 20)
            }
            
            // The view is already a LinearLayout, so we can just add the checkbox to it
            (view as? LinearLayout)?.addView(checkBox)
            
            applyAllCheckbox = checkBox
        }

        val dialog = AlertDialog.Builder(requireActivity(), R.style.AppAlertDialog)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setCancelable(true)
            .create()

        // Call loadData after dialog is shown
        dialog.setOnShowListener {
            toggleProgressVisibility(true)
            BackgroundUiTask.execute(
                backgroundBlock = {
                    if (uniqueOnly) {
                        val prefs = PreferenceManager.getDefaultSharedPreferences(requireActivity())
                        val activeFieldId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, -1)
                        val activity = requireActivity() as FieldEditorActivity
                        attributes = activity.getDatabase().getPossibleUniqueAttributes(activeFieldId)?.toTypedArray() ?: emptyArray()
                    } else {
                        loadData()
                    }
                },
                uiBlock = {
                    toggleProgressVisibility(false)
                    if (uniqueOnly) {
                        loadTab(getString(R.string.dialog_att_chooser_attributes))
                    } else {
                        setupTabLayout()
                    }
                },
                onCanceled = ::setupTabLayout
            )
        }

        return dialog

    }

    fun setOnAttributeSelectedListener(listener: OnAttributeSelectedListener) {
        onAttributeSelectedListener = listener
    }

    private fun loadData() {

        //query database for attributes/traits to use
        try {
            val activity = requireActivity() as CollectActivity
            attributes = activity.getDatabase().getAllObservationUnitAttributeNames(activity.studyId.toInt())
            val attributesList = attributes.toMutableList()
            attributesList.add(0, getString(R.string.field_name_attribute))
            attributes = attributesList.toTypedArray()
            traits = activity.getDatabase().allTraitObjects.toTypedArray()
            other = traits.filter { !it.visible }.toTypedArray()
            traits = traits.filter { it.visible }.toTypedArray()
        } catch (e: Exception) {
            Log.d(TAG, "Error occurred when querying for attributes in Collect Activity.")
            e.printStackTrace()
        }
    }

    private fun toggleProgressVisibility(show: Boolean) {
        progressBar.visibility = if (show) ProgressBar.VISIBLE else ProgressBar.GONE
        tabLayout.visibility = if (show) TabLayout.GONE else TabLayout.VISIBLE
        recyclerView.visibility = if (show) RecyclerView.GONE else RecyclerView.VISIBLE
    }

    /**
     * data automatically changes when tab is selected,
     * select first tab programmatically to load initial data
     * save the selected tab as preference
     */
    private fun setupTabLayout() {

        try {

            toggleProgressVisibility(false)

            tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {

                override fun onTabSelected(tab: TabLayout.Tab?) {
                    tab?.let { t ->
                        loadTab(t.text.toString())
                        PreferenceManager.getDefaultSharedPreferences(requireActivity())
                            .edit().putInt(GeneralKeys.ATTR_CHOOSER_DIALOG_TAB, t.position).apply()
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}

                override fun onTabReselected(tab: TabLayout.Tab?) {
                    tab?.let { t -> loadTab(t.text.toString()) }
                }

            })

            //manually select the first tab based on preferences
            val tabIndex = PreferenceManager.getDefaultSharedPreferences(requireActivity()).getInt(GeneralKeys.ATTR_CHOOSER_DIALOG_TAB, 0)

            tabLayout.selectTab(tabLayout.getTabAt(tabIndex))

        } catch (e: Exception) {

            Log.d(TAG, "Error occurred when querying for attributes in Collect Activity.")

            e.printStackTrace()
        }
    }

    /** Placeholder method; overridden in infoBar class where selection is needed. */
    protected open fun getSelected(): String? = null

    /** Handles loading data into the recycler view adapter. */
    private fun loadTab(label: String) {
        val attributesLabel = getString(R.string.dialog_att_chooser_attributes)
        val traitsLabel = getString(R.string.dialog_att_chooser_traits)

        // Get values to display based on cached arrays
        val optionLabels = when (label) {
            attributesLabel -> attributes
            traitsLabel -> traits.filter { it.visible }.map { it.name }.toTypedArray()
            else -> other.map { it.name }.toTypedArray()
        }

        // Initialize the adapter with the optionLabels. Highlight 'selected' if defined.
        val selected = getSelected()
        recyclerView.adapter = AttributeAdapter(this, selected).apply {
            submitList(optionLabels.toList())
        }
    }

    override fun onAttributeClicked(label: String, position: Int) {
        lastApplyToAllState = applyAllCheckbox?.isChecked ?: false
        onAttributeSelectedListener?.onAttributeSelected(label) ?: run {
            Log.w(TAG, "No OnAttributeSelectedListener set for AttributeChooserDialog.")
        }
        dismiss()
    }

}
