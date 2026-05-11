package com.fieldbook.tracker.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.widget.CheckBox
import android.widget.ProgressBar
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.adapters.AttributeAdapter
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.BackgroundUiTask
import com.google.android.material.tabs.TabLayout

/**
 * A tab layout with tabs: attributes, traits, and other.
 * Each tab will load data into a recycler view that lets user choose infobar prefixes.
 *
 * Added 'showSystemAttributes' that will toggle the adding of the field name attribute to the list of attributes (and any future system attributes).
 */

open class AttributeChooserDialog(
    private val showTraits: Boolean = true,
    private val showOther: Boolean = true,
    private val showSystemAttributes: Boolean = true
) : DialogFragment(), AttributeAdapter.AttributeAdapterController {

    companion object {
        const val TAG = "AttributeChooserDialog"

        private const val ARG_TITLE = "title"

        /** Factory method to create a new dialog with an infoBarPosition argument. */
        fun newInstance(titleResId: Int): AttributeChooserDialog {
            val args = Bundle().apply {
                putInt(ARG_TITLE, titleResId)
            }
            return AttributeChooserDialog().apply {
                arguments = args
            }
        }
    }

    interface OnAttributeSelectedListener {
        fun onAttributeSelected(model: AttributeAdapter.AttributeModel)
    }

    protected lateinit var tabLayout: TabLayout
    protected lateinit var recyclerView: RecyclerView
    protected lateinit var progressBar: ProgressBar
    protected lateinit var applyAllCheckbox: CheckBox

    protected var attributes = arrayOf<String>()
    protected var visibleTraits = arrayOf<TraitObject>()
    protected var nonVisibleTraits = arrayOf<TraitObject>()
    protected var attributeSelectedListener: OnAttributeSelectedListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_collect_att_chooser, null)

        var titleResId = arguments?.getInt(ARG_TITLE) ?: R.string.dialog_att_chooser_title_default
        if (titleResId == 0) titleResId = R.string.dialog_att_chooser_title_default

        val dialogTitle = context?.getString(titleResId)

        // Initialize UI elements
        tabLayout = view.findViewById(R.id.dialog_collect_att_chooser_tl)
        recyclerView = view.findViewById(R.id.dialog_collect_att_chooser_lv)
        progressBar = view.findViewById(R.id.dialog_collect_att_chooser_pb)
        applyAllCheckbox = view.findViewById(R.id.dialog_collect_att_chooser_checkbox)

        recyclerView.layoutManager = LinearLayoutManager(requireActivity())
        recyclerView.adapter = AttributeAdapter(this, null)

        //toggle view of traits/other based on class param
        tabLayout.getTabAt(1)?.view?.visibility =
            if (showTraits) TabLayout.VISIBLE else TabLayout.GONE
        tabLayout.getTabAt(2)?.view?.visibility =
            if (showOther) TabLayout.VISIBLE else TabLayout.GONE

        val builder = AlertDialog.Builder(requireActivity(), R.style.AppAlertDialog)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setCancelable(true)

        dialogTitle?.let { builder.setTitle(it) }

        val dialog = builder.create()

        // Call loadData after dialog is shown
        dialog.setOnShowListener {
            toggleProgressVisibility(true)
            BackgroundUiTask.execute(
                backgroundBlock = ::loadData,
                uiBlock = ::setupTabLayout,
                onCanceled = ::setupTabLayout
            )
        }

        return dialog

    }

    fun setOnAttributeSelectedListener(listener: OnAttributeSelectedListener) {
        attributeSelectedListener = listener
    }

    private fun loadData() {

        //query database for attributes/traits to use
        try {
            val activity = requireActivity() as CollectActivity
            attributes =
                activity.getDatabase().getAllObservationUnitAttributeNames(activity.studyId.toInt())
            val attributesList = attributes.toMutableList()
            if (showSystemAttributes) {
                attributesList.add(0, getString(R.string.field_name_attribute))
            }
            attributes = attributesList.toTypedArray()
            visibleTraits = activity.getDatabase().allTraitObjects.toTypedArray()
            nonVisibleTraits = visibleTraits.filter { !it.visible }.toTypedArray()
            visibleTraits = visibleTraits.filter { it.visible }.toTypedArray()
        } catch (e: Exception) {
            Log.d(TAG, "Error occurred when querying for attributes in Collect Activity.")
            e.printStackTrace()
        }
    }

    protected fun toggleProgressVisibility(show: Boolean) {
        progressBar.visibility = if (show) ProgressBar.VISIBLE else ProgressBar.GONE
        tabLayout.visibility = if (show) TabLayout.GONE else TabLayout.VISIBLE
        recyclerView.visibility = if (show) RecyclerView.GONE else RecyclerView.VISIBLE
    }

    /**
     * data automatically changes when tab is selected,
     * select first tab programmatically to load initial data
     * save the selected tab as preference
     */
    protected fun setupTabLayout() {

        try {

            toggleProgressVisibility(false)

            tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {

                override fun onTabSelected(tab: TabLayout.Tab?) {
                    tab?.let { t ->
                        loadTab(t.text.toString())
                        PreferenceManager.getDefaultSharedPreferences(requireActivity())
                            .edit { putInt(GeneralKeys.ATTR_CHOOSER_DIALOG_TAB, t.position) }
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}

                override fun onTabReselected(tab: TabLayout.Tab?) {
                    tab?.let { t -> loadTab(t.text.toString()) }
                }

            })

            //manually select the first tab based on preferences
            var tabIndex = PreferenceManager.getDefaultSharedPreferences(requireActivity())
                .getInt(GeneralKeys.ATTR_CHOOSER_DIALOG_TAB, 0)

            if (!showTraits && tabIndex == 1) {
                tabIndex = 0
            } else if (!showOther && tabIndex == 2) {
                tabIndex = 0
            }

            tabLayout.selectTab(tabLayout.getTabAt(tabIndex))

        } catch (e: Exception) {

            Log.d(TAG, "Error occurred when querying for attributes in Collect Activity.")

            e.printStackTrace()
        }
    }

    /** Placeholder method; overridden in infoBar class where selection is needed. */
    protected open fun getSelected(): AttributeAdapter.AttributeModel? = null

    /** Handles loading data into the recycler view adapter. */
    protected fun loadTab(label: String) {
        val attributesLabel = getString(R.string.dialog_att_chooser_attributes)
        val traitsLabel = getString(R.string.dialog_att_chooser_traits)

        // Get values to display based on cached arrays
        val optionLabels: List<AttributeAdapter.AttributeModel> = when (label) {

            attributesLabel -> attributes.map { AttributeAdapter.AttributeModel(label = it) }

            traitsLabel -> visibleTraits.map {
                AttributeAdapter.AttributeModel(
                    label = it.alias,
                    trait = it
                )
            }

            else -> nonVisibleTraits.map {
                AttributeAdapter.AttributeModel(
                    label = it.alias,
                    trait = it
                )
            }
        }

        // Initialize the adapter with the optionLabels. Highlight 'selected' if defined.
        val selected = getSelected()
        recyclerView.adapter = AttributeAdapter(this, selected).apply {
            submitList(optionLabels)
        }
    }

    override fun onAttributeClicked(model: AttributeAdapter.AttributeModel, position: Int) {
        attributeSelectedListener?.onAttributeSelected(model) ?: run {
            Log.w(TAG, "No OnAttributeSelectedListener set for AttributeChooserDialog.")
        }
        dismiss()
    }

}