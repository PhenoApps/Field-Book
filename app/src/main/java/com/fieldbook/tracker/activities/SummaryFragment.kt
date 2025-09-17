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
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.AttributeAdapter
import com.fieldbook.tracker.adapters.SummaryAdapter
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
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
        attributes: Array<String>, traits: ArrayList<TraitObject>, collector: CollectActivity
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

                val traits = ArrayList(database.allTraitObjects.filter { it.visible })

                val attributeModels = attributes.map { AttributeAdapter.AttributeModel(it) }
                val traitAttributeModels =
                    traits.map { AttributeAdapter.AttributeModel(it.alias, trait = it) }
                val models = attributeModels + traitAttributeModels

                loadData(collector, models)

                setupToolbar(attributes, traits, collector)

                prevButton?.setOnClickListener {

                    collector.getRangeBox().clickLeft()

                    loadData(collector, models)
                }

                nextButton?.setOnClickListener {

                    collector.getRangeBox().clickRight()

                    loadData(collector, models)
                }
            }
        }
    }

    private fun loadData(
        collector: CollectActivity, models: List<AttributeAdapter.AttributeModel>
    ) {

        val filter = getPersistedFilter(collector)

        val obsUnit = collector.observationUnit

        val attributeSet = hashSetOf<AttributeAdapter.AttributeModel>()
        val traitSet = hashSetOf<AttributeAdapter.AttributeModel>()

        if (filter.first == null) {
            //if no filter is set, use all attributes
            attributeSet.addAll(models.filter { it.trait == null }.toSet())
        } else {
            //otherwise add all attributes
            attributeSet.addAll((filter.first ?: setOf()).filter { it.trait == null })
        }

        if (filter.second == null) {
            //if no trait filter is set, use all traits
            traitSet.addAll(models.filter { it.trait != null }.toSet())
        } else {
            //otherwise fill with the filter traits
            traitSet.addAll((filter.second ?: setOf()).filter { it.trait != null })
        }

        if ((attributeSet + traitSet).isEmpty()) {

            (recyclerView?.adapter as? SummaryAdapter)?.let { adapter ->

                adapter.submitList(emptyList<AttributeAdapter.AttributeModel>())

                adapter.notifyItemRangeChanged(0, adapter.itemCount)
            }

            return
        }

        val data = database.convertDatabaseToTable(
            attributeSet.map { it.label }.toTypedArray(),
            ArrayList(traitSet.map { it.trait }), obsUnit
        )

        val filters = attributeSet + traitSet

        val pairList = arrayListOf<AttributeAdapter.AttributeModel>()

        data?.use { cursor ->

            (recyclerView?.adapter as? SummaryAdapter)?.let { adapter ->

                cursor.moveToFirst()

                try {

                    val chosenModels = models.filter {

                        if (it.trait == null) {
                            it in filters
                        } else {
                            it.trait.id in filters.mapNotNull { filterModel -> filterModel.trait?.id }
                        }
                    }

                    chosenModels.forEach { model ->

                        val index = cursor.getColumnIndex(model.label)

                        var value: String? = null

                        if (index > -1) {

                            value = cursor.getString(index)

                            try {

                                activity?.let { act ->

                                    value?.let { v ->

                                        if (model.trait == null) {

                                            //attribute
                                            value = v

                                        } else {

                                            //model.trait.loadAttributeAndValues()

                                            value = database.valueFormatter.processValue(v, model.trait)

                                        }
//                                        //read the preferences, default to displaying values instead of labels
//                                        val labelValPref: String =
//                                            PreferenceManager.getDefaultSharedPreferences(act)
//                                                .getString(
//                                                    PreferenceKeys.LABELVAL_CUSTOMIZE,
//                                                    "value"
//                                                )
//                                                ?: "value"
//
//                                        value = CategoryJsonUtil.flattenMultiCategoryValue(
//                                            CategoryJsonUtil.decode(v), labelValPref == "value"
//                                        )
                                    }
                                }

                            } catch (ignore: JsonParseException) {
                            }

                        }

                        pairList.add(
                            AttributeAdapter.AttributeModel(
                                model.label,
                                value ?: "",
                                model.trait
                            )
                        )
                    }

                } catch (e: Exception) {

                    e.printStackTrace()

                }

                val sortedPairList = pairList.filter { it.trait == null }.sortedBy { it.label } + pairList.filter { it.trait != null }.sortedBy { it.label }

                adapter.submitList(sortedPairList)

                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun getPersistedFilter(ctx: Context): Pair<Set<AttributeAdapter.AttributeModel>?, Set<AttributeAdapter.AttributeModel>?> {

        val attributes = PreferenceManager.getDefaultSharedPreferences(ctx)
            .getStringSet(
                "${GeneralKeys.SUMMARY_FILTER_ATTRIBUTES}.${(ctx as CollectActivity).studyId}",
                null
            )

        val attributeSet = if (attributes == null) null else hashSetOf<AttributeAdapter.AttributeModel>()

        attributeSet?.addAll(attributes?.map {
            AttributeAdapter.AttributeModel(it)
        }?.toTypedArray() ?: emptyArray())

        val traitIds = PreferenceManager.getDefaultSharedPreferences(ctx)
            .getStringSet(
                "${GeneralKeys.SUMMARY_FILTER_TRAITS}.${ctx.studyId}",
                null
            )

        val traitSet = if (traitIds == null) null else hashSetOf<AttributeAdapter.AttributeModel>()
        traitSet?.addAll(traitIds?.mapNotNull { traitId ->
            val trait = ctx.database.getTraitById(traitId)
            AttributeAdapter.AttributeModel(label = trait.alias, trait = trait)
        }?.toTypedArray() ?: emptyArray())

        return attributeSet?.toSet() to traitSet?.toSet()
    }


    private fun setPersistedFilter(ctx: Context, filter: List<AttributeAdapter.AttributeModel>) {

        val attributes = filter.filter { it.trait == null }.map { it.label }.toSet()
        val traits = filter.filter { it.trait != null }.mapNotNull { it.trait?.id }.toSet()

        PreferenceManager.getDefaultSharedPreferences(ctx).edit {
            putStringSet(
                "${GeneralKeys.SUMMARY_FILTER_ATTRIBUTES}.${(ctx as CollectActivity).studyId}",
                attributes
            )
            putStringSet(
                "${GeneralKeys.SUMMARY_FILTER_TRAITS}.${ctx.studyId}",
                traits
            )
        }
    }

    private fun showFilterDialog(
        collector: CollectActivity, attributes: Array<String>, traits: ArrayList<TraitObject>
    ) {

        activity?.let { ctx ->

            val (attributeFilters, traitFilters) = getPersistedFilter(collector)

            val attributeModels = attributes.map { AttributeAdapter.AttributeModel(it) }.sortedBy { it.label }
            val traitAttributeModels =
                traits.map { AttributeAdapter.AttributeModel(it.alias, trait = it) }.sortedBy { it.label }

            val models = (attributeModels + traitAttributeModels).toMutableList()

            //initialize which attributes are checked, if no filter is saved then check all
            val checked = attributeModels.map { model ->
                if (attributeFilters == null) true
                else model in attributeFilters
            }.toBooleanArray() + traitAttributeModels.map {
                if (traitFilters == null) true
                else traitFilters.map { it.trait?.id }.contains(it.trait?.id) == true
            }.toBooleanArray()

            val loadableModels = hashSetOf<AttributeAdapter.AttributeModel>()
            loadableModels.addAll(models.filterIndexed { index, _ -> checked[index] })

            filterDialog =
                AlertDialog.Builder(activity, R.style.AppAlertDialog)
                    .setTitle(R.string.fragment_summary_filter_title)
                    .setMultiChoiceItems(
                        models.map { it.label }.toTypedArray(),
                        checked
                    ) { _, which, isChecked ->
                        val item = models[which]
                        if (isChecked) {
                            loadableModels.add(item)
                        } else {
                            loadableModels.remove(item)
                        }
                    }.setPositiveButton(android.R.string.ok) { dialog, _ ->
                        setPersistedFilter(ctx, loadableModels.toList())
                        dialog.dismiss()
                        loadData(collector, loadableModels.filter { it.trait == null } + loadableModels.filter { it.trait != null })
                    }.setNegativeButton(android.R.string.cancel) { dialog, _ ->
                        dialog.dismiss()
                    }.setNeutralButton(R.string.dialog_fragment_summary_neutral_button) { _, _ -> }
                    .create()

            if (isAdded && filterDialog?.isShowing != true) {

                filterDialog?.show()

                filterDialog?.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { _ ->

                    val list = (filterDialog as AlertDialog).listView

                    list.choiceMode = ListView.CHOICE_MODE_MULTIPLE

                    val toggle = loadableModels.size < models.size

                    models.forEachIndexed { index, _ ->

                        list.setItemChecked(index, toggle)

                        checked[index] = toggle

                        if (toggle) {
                            val item = models[index]
                            loadableModels.add(item)
                        } else {
                            loadableModels.remove(models[index])
                        }
                    }
                }
            }
        }
    }

    /**
     * Navigate to the clicked trait
     */
    override fun onAttributeClicked(attribute: AttributeAdapter.AttributeModel) {

        with(activity as? CollectActivity) {

            this?.let { collector ->

                attribute.trait?.id?.let { traitId ->

                    collector.navigateToTrait(traitId)

                    parentFragmentManager.popBackStack()

                }
            }
        }
    }
}