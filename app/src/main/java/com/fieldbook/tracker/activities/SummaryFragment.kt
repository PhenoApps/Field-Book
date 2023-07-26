package com.fieldbook.tracker.activities

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ListView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.SummaryAdapter
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import com.google.gson.JsonParseException
import dagger.hilt.android.AndroidEntryPoint
import org.phenoapps.utils.SoftKeyboardUtil
import javax.inject.Inject

@AndroidEntryPoint
class SummaryFragment : Fragment(), SummaryAdapter.SummaryController {

    private var recyclerView: RecyclerView? = null
    private var nextButton: ImageView? = null
    private var prevButton: ImageView? = null
    private var toolbar: Toolbar? = null
    private var filterDialog: AlertDialog? = null
    private var listener: SummaryOpenListener? = null

    @Inject
    lateinit var database: DataHelper

    fun interface SummaryOpenListener {
        fun onSummaryDestroy()
    }

    fun setListener(listener: SummaryOpenListener) {

        this.listener = listener

    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.onSummaryDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_summary, container, false)

        toolbar = view.findViewById(R.id.toolbar)

        recyclerView = view.findViewById(R.id.fragment_summary_rv)

        prevButton = view.findViewById(R.id.fragment_summary_prev_btn)

        nextButton = view.findViewById(R.id.fragment_summary_next_btn)

        recyclerView?.adapter = SummaryAdapter(this)

        setup()

        return view

    }

    override fun onResume() {
        super.onResume()
        view?.let { v ->
            SoftKeyboardUtil.closeKeyboard(activity, v, 0L)
        }
    }

    private fun setupToolbar(
        attributes: Array<String>, traits: Array<String>, collector: CollectActivity
    ) {

        toolbar?.inflateMenu(R.menu.menu_fragment_summary)

        toolbar?.setTitle(R.string.fragment_summary_toolbar_title)

        toolbar?.setNavigationIcon(R.drawable.arrow_left)

        toolbar?.setNavigationOnClickListener {

            parentFragmentManager.popBackStack()
        }

        toolbar?.setOnMenuItemClickListener { item ->

            when (item.itemId) {
                android.R.id.home -> {
                    parentFragmentManager.popBackStack()
                }
                R.id.menu_fragment_summary_filter -> {
                    showFilterDialog(collector, attributes, traits)
                }
            }

            true
        }
    }

    private fun setup() {

        with(activity as? CollectActivity) {

            this?.let { collector ->

                val studyId = collector.studyId

                val attributes = database.getAllObservationUnitAttributeNames(studyId.toInt())

                val traits = database.visibleTrait

                loadData(collector, attributes, traits)

                setupToolbar(attributes, traits, collector)

                prevButton?.setOnClickListener {

                    collector.getRangeBox().clickLeft()

                    loadData(collector, attributes, traits)
                }

                nextButton?.setOnClickListener {

                    collector.getRangeBox().clickRight()

                    loadData(collector, attributes, traits)
                }
            }
        }
    }

    private fun loadData(
        collector: CollectActivity, attributes: Array<String>, traits: Array<String>
    ) {

        val filter = getPersistedFilter(collector)

        val obsUnit = collector.observationUnit

        val data = database.convertDatabaseToTable(attributes, traits, obsUnit)

        val pairList = arrayListOf<SummaryAdapter.SummaryListModel>()

        (recyclerView?.adapter as? SummaryAdapter)?.let { adapter ->

            data.moveToFirst()

            try {

                (attributes + traits).filter { if (filter == null) true else it in filter }
                    .forEach { key ->

                        val index = data.getColumnIndex(key)

                        var value: String? = null

                        if (index > -1) {

                            value = data.getString(index)

                            try {

                                activity?.let { act ->

                                    value?.let { v ->

                                        //read the preferences, default to displaying values instead of labels
                                        val labelValPref: String =
                                            PreferenceManager.getDefaultSharedPreferences(act)
                                                .getString(GeneralKeys.LABELVAL_CUSTOMIZE, "value")
                                                ?: "value"

                                        value = CategoryJsonUtil.flattenMultiCategoryValue(
                                            CategoryJsonUtil.decode(v), labelValPref == "value"
                                        )

                                    }
                                }

                            } catch (ignore: JsonParseException) {
                            }

                        }

                        pairList.add(
                            SummaryAdapter.SummaryListModel(
                                key,
                                value ?: "",
                                key in traits
                            )
                        )

                    }

            } catch (e: Exception) {

                e.printStackTrace()

            }

            adapter.submitList(pairList)

            adapter.notifyDataSetChanged()
        }
    }

    private fun getPersistedFilter(ctx: Context): Set<String>? =
        PreferenceManager.getDefaultSharedPreferences(ctx)
            .getStringSet(
                "${GeneralKeys.SUMMARY_FILTER_ATTRIBUTES}.${(ctx as CollectActivity).studyId}",
                null
            )

    private fun setPersistedFilter(ctx: Context, filter: Set<String>?) {
        PreferenceManager.getDefaultSharedPreferences(ctx).edit()
            .putStringSet(
                "${GeneralKeys.SUMMARY_FILTER_ATTRIBUTES}.${(ctx as CollectActivity).studyId}",
                filter ?: setOf()
            ).commit()
    }

    private fun showFilterDialog(
        collector: CollectActivity, attributes: Array<String>, traits: Array<String>
    ) {

        activity?.let { ctx ->

            var filter: Set<String>? = getPersistedFilter(ctx)

            val keys = attributes + traits

            //initialize which attributes are checked, if no filter is saved then check all
            val checked = keys.map {
                if (filter == null) true
                else it in filter!!
            }.toBooleanArray()

            //initialize filter in case this is the first load
            if (filter == null) {
                filter = setOf()
                filter = filter.plus(keys)
            }

            filterDialog =
                AlertDialog.Builder(activity, R.style.AppAlertDialog).setTitle(R.string.fragment_summary_filter_title)
                    .setMultiChoiceItems(keys, checked) { _, which, isChecked ->
                        val item = keys[which]
                        filter = if (isChecked) {
                            filter?.plus(item)
                        } else {
                            filter?.minus(item)
                        }
                    }.setPositiveButton(android.R.string.ok) { dialog, _ ->
                        setPersistedFilter(ctx, filter)
                        dialog.dismiss()
                        loadData(collector, attributes, traits)
                    }.setNegativeButton(android.R.string.cancel) { dialog, _ ->
                        dialog.dismiss()
                    }.setNeutralButton(R.string.dialog_fragment_summary_neutral_button) { _, _ -> }
                    .create()

            if (isAdded && filterDialog?.isShowing != true) {

                filterDialog?.show()

                filterDialog?.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { _ ->

                    val list = (filterDialog as AlertDialog).listView

                    list.choiceMode = ListView.CHOICE_MODE_MULTIPLE

                    val toggle = (filter?.size ?: 0) < keys.size

                    filter = if (toggle) {

                        filter?.plus(keys)

                    } else setOf()

                    keys.forEachIndexed { index, _ ->

                        list.setItemChecked(index, toggle)

                        checked[index] = toggle

                    }
                }
            }
        }
    }

    /**
     * Navigate to the clicked trait
     */
    override fun onAttributeClicked(attribute: String) {

        with(activity as? CollectActivity) {

            this?.let { collector ->

                if (attribute in database.visibleTrait) {

                    collector.navigateToTrait(attribute)

                    parentFragmentManager.popBackStack()

                }
            }
        }
    }
}