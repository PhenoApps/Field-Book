package com.fieldbook.tracker.dialogs

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.adapters.AttributeAdapter
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope

/**
 * A tab layout with tabs: attributes, traits, and other.
 * Each tab will load data into a recycler view that lets user choose infobar prefixes.
 */
class CollectAttributeChooserDialog(private val activity: CollectActivity):
    Dialog(activity, R.style.AppAlertDialog),
    AttributeAdapter.AttributeAdapterController,
    CoroutineScope by MainScope() {

    companion object {
        const val TAG = "AttDialog"
    }

    private lateinit var tabLayout: TabLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var cancelButton: Button

    private var attributes = arrayOf<String>()
    private var traits = arrayOf<TraitObject>()
    private var other = arrayOf<TraitObject>()

    var infoBarPosition: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //setup dialog ui
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        setContentView(R.layout.dialog_collect_att_chooser)

        setTitle(R.string.dialog_collect_att_chooser_title)

        //initialize ui elements
        tabLayout = findViewById(R.id.dialog_collect_att_chooser_tl)
        recyclerView = findViewById(R.id.dialog_collect_att_chooser_lv)
        cancelButton = findViewById(R.id.dialog_collect_att_chooser_cancel_btn)

        //setCancelable(false)

        setCanceledOnTouchOutside(true)

        cancelButton.setOnClickListener {
            this.cancel()
        }

        setOnShowListener {

            //query database for attributes/traits to use
            try {
                attributes = activity.getDatabase().getAllObservationUnitAttributeNames(activity.studyId.toInt())
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
    }

    /**
     * data automatically changes when tab is selected,
     * select first tab programmatically to load initial data
     * save the selected tab as preference
     */
    private fun setupTabLayout() {

        tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {

            override fun onTabSelected(tab: TabLayout.Tab?) {

                tab?.let { t ->

                    loadTab(t.text.toString())

                    activity.getPreferences()
                        .edit().putInt(GeneralKeys.ATTR_CHOOSER_DIALOG_TAB, t.position)
                        .apply()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {

                tab?.let { t -> loadTab(t.text.toString()) }
            }
        })

        //manually select the first tab based on preferences
        val tabIndex = activity.getPreferences()
            .getInt(GeneralKeys.ATTR_CHOOSER_DIALOG_TAB, 1)

        tabLayout.selectTab(tabLayout.getTabAt(tabIndex))
    }

    /**
     * handles loading data into the recycler view adapter
     */
    private fun loadTab(label: String) {

        val attributesLabel = activity.getString(R.string.dialog_att_chooser_attributes)
        val traitsLabel = activity.getString(R.string.dialog_att_chooser_traits)

        //get values to display based on cached arrays
        val infoBarLabels = when (label) {
            attributesLabel -> attributes
            traitsLabel -> traits.filter { it.visible }.map { it.trait }.toTypedArray()
            else -> other.map { it.trait }.toTypedArray()
        }

        //create adapter of labels s.a : plot/column/block or height/picture/notes depending on what tab is selected
        val adapter = AttributeAdapter(this)

        recyclerView.adapter = adapter

        adapter.submitList(infoBarLabels.toList())
    }

    /**
     * Tab recycler view list item click listener.
     * When user selects a prefix, it is saved in preferences for this infobar position (global)
     * then the dialog is dismissed after refreshing collect
     */
    override fun onAttributeClicked(label: String, position: Int) {

        activity.getPreferences().edit().putString("DROP$infoBarPosition", label).apply()

        activity.refreshInfoBarAdapter()

        dismiss()
    }
}
