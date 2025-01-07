package com.fieldbook.tracker.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.adapters.AttributeAdapter
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.google.android.material.tabs.TabLayout

/**
 * A tab layout with tabs: attributes, traits, and other.
 * Each tab will load data into a recycler view that lets user choose infobar prefixes.
 */

open class AttributeChooserDialog(
    private val showTraits: Boolean = true,
    private val showOther: Boolean = true
) : DialogFragment(), AttributeAdapter.AttributeAdapterController {
    companion object {
        const val TAG = "AttributeChooserDialog"
    }
    interface OnAttributeSelectedListener {
        fun onAttributeSelected(label: String)
    }

    private lateinit var tabLayout: TabLayout
    private lateinit var recyclerView: RecyclerView
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
        recyclerView.layoutManager = LinearLayoutManager(requireActivity())
        recyclerView.adapter = AttributeAdapter(this, null)

        //toggle view of traits/other based on class param
        tabLayout.getTabAt(1)?.view?.visibility = if (showTraits) TabLayout.VISIBLE else TabLayout.GONE
        tabLayout.getTabAt(2)?.view?.visibility = if (showOther) TabLayout.VISIBLE else TabLayout.GONE

        val dialog = AlertDialog.Builder(requireActivity(), R.style.AppAlertDialog)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setCancelable(true)
            .create()

        // Call loadData after dialog is shown
        dialog.setOnShowListener {
            loadData()
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

        //setup ui
        try {
            setupTabLayout()
        } catch (e: Exception) {
            Log.d(TAG, "Error occurred when setting up attribute tab layout.")
            e.printStackTrace()
        }
    }

    /**
     * data automatically changes when tab is selected,
     * select first tab programmatically to load initial data
     * save the selected tab as preference
     */
    private fun setupTabLayout() {
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
        onAttributeSelectedListener?.onAttributeSelected(label) ?: run {
            Log.w(TAG, "No OnAttributeSelectedListener set for AttributeChooserDialog.")
        }
        dismiss()
    }

}
