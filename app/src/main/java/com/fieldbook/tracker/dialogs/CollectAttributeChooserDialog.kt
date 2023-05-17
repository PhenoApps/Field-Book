package com.fieldbook.tracker.dialogs

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope

class CollectAttributeChooserDialog(private val activity: CollectActivity,
                                    private val onSelected: (String, String) -> Unit,
                                    private val infoBarPosition: Int):
    Dialog(activity, R.style.AppAlertDialog), CoroutineScope by MainScope() {

    companion object {
        const val TAG = "AttDialog"
    }

    private val helper by lazy { DataHelper(context) }

    private lateinit var tabLayout: TabLayout
    private lateinit var listView: ListView
    private lateinit var cancelButton: Button

    private var attributes = arrayOf<String>()
    private var traits = arrayOf<TraitObject>()
    private var other = arrayOf<TraitObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        setContentView(R.layout.dialog_collect_att_chooser)

        setTitle(R.string.dialog_collect_att_chooser_title)

        tabLayout = findViewById(R.id.dialog_collect_att_chooser_tl)
        listView = findViewById(R.id.dialog_collect_att_chooser_lv)
        cancelButton = findViewById(R.id.dialog_collect_att_chooser_cancel_btn)

        //setCancelable(false)

        setCanceledOnTouchOutside(true)

        cancelButton.setOnClickListener {
            this.cancel()
        }

        try {
            attributes = helper.getAllObservationUnitAttributeNames(activity.studyId.toInt())
            traits = helper.allTraitObjects.toTypedArray()
            other = traits.filter { !it.visible }.toTypedArray()
            traits = traits.filter { it.visible }.toTypedArray()
        } catch (e: Exception) {
            Log.d(TAG, "Error occurred when querying for attributes in Collect Activity.")
            e.printStackTrace()
        }

        try {
            setupTabLayout()
        } catch (e: Exception) {
            Log.d(TAG, "Error occurred when setting up attribute tab layout.")
            e.printStackTrace()
        }
    }

    private fun setupTabLayout() {

        tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {

            override fun onTabSelected(tab: TabLayout.Tab?) {

                tab?.let { t ->

                    loadTab(t.text.toString())
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        //manually select the first tab
        loadTab(activity.getString(R.string.dialog_att_chooser_attributes))
    }

    private fun loadTab(label: String) {

        val plotId = activity.getRangeBox().getPlotID() ?: ""
        val attributesLabel = activity.getString(R.string.dialog_att_chooser_attributes)
        val traitsLabel = activity.getString(R.string.dialog_att_chooser_traits)

        //get values to display based on cached arrays
        val infoBarLabels = when (label) {
            attributesLabel -> attributes
            traitsLabel -> traits.filter { it.visible }.map { it.trait }.toTypedArray()
            else -> other.map { it.trait }.toTypedArray()
        }

        //create adapter of labels s.a : plot/column/block or height/picture/notes depending on what tab is selected
        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, infoBarLabels)

        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->

            val infoLabel = when (label) {
                attributesLabel -> attributes[position]
                traitsLabel -> traits[position].trait
                else -> other[position].trait
            }

            activity.getPreferences().edit().putString("DROP$infoBarPosition", infoLabel).apply()

            //get the value correlating to the chosen label
            val value = queryForLabelValue(plotId, infoLabel, label == attributesLabel)

            onSelected(infoLabel, value)

            dismiss()

        }
    }

    public fun queryForLabelValue(plotId: String, label: String, isAttribute: Boolean): String {

        return if (isAttribute) {

            val values = helper.getDropDownRange(label, plotId)

            if (values == null || values.isEmpty()) {

                activity.getString(R.string.main_infobar_data_missing)

            } else {

                values[0]
            }

        } else {

            helper.getUserDetail(plotId)[label] ?: ""
        }
    }
}
