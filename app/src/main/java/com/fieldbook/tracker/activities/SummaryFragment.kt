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
                    traits.map { AttributeAdapter.AttributeModel(it.name, trait = it) }
                val models = attributeModels + traitAttributeModels

                //if preference keys don't exist yet, create them and populate them with all attributes and traits
                if (!PreferenceManager.getDefaultSharedPreferences(requireContext())
                        .contains("${GeneralKeys.SUMMARY_FILTER_ATTRIBUTES}.${(activity as CollectActivity).studyId}")
                ) {
                    setPersistedFilter(collector, models)

                }

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

        val data = database.convertDatabaseToTable(
            models.filter { it.trait == null }.map { it.label }.toTypedArray(),
            ArrayList(models.filter { it.trait != null }.map { it.trait }), obsUnit
        )

        val pairList = arrayListOf<AttributeAdapter.AttributeModel>()

        (recyclerView?.adapter as? SummaryAdapter)?.let { adapter ->

            data.moveToFirst()

            try {

                models.filter { it in filter }
                    .forEach { model ->

                        val index = data.getColumnIndex(model.label)

                        var value: String? = null

                        if (index > -1) {

                            value = data.getString(index)

                            try {

                                activity?.let { act ->

                                    value?.let { v ->

                                        //read the preferences, default to displaying values instead of labels
                                        val labelValPref: String =
                                            PreferenceManager.getDefaultSharedPreferences(act)
                                                .getString(
                                                    PreferenceKeys.LABELVAL_CUSTOMIZE,
                                                    "value"
                                                )
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

            adapter.submitList(pairList)

            adapter.notifyDataSetChanged()
        }
    }

    private fun getPersistedFilter(ctx: Context): List<AttributeAdapter.AttributeModel> {

        val models = arrayListOf<AttributeAdapter.AttributeModel>()

        val attributes = PreferenceManager.getDefaultSharedPreferences(ctx)
            .getStringSet(
                "${GeneralKeys.SUMMARY_FILTER_ATTRIBUTES}.${(ctx as CollectActivity).studyId}",
                null
            ) ?: setOf()

        models.addAll(attributes.map {
            AttributeAdapter.AttributeModel(it)
        })

        val traitIds = PreferenceManager.getDefaultSharedPreferences(ctx)
            .getStringSet(
                "${GeneralKeys.SUMMARY_FILTER_TRAITS}.${ctx.studyId}",
                null
            ) ?: setOf()

        models.addAll(traitIds.mapNotNull { traitId ->
            ctx.database.getTraitById(traitId)?.let { trait ->
                AttributeAdapter.AttributeModel(trait.name, trait = trait)
            }
        })

        return models
    }


    private fun setPersistedFilter(ctx: Context, filter: List<AttributeAdapter.AttributeModel>) {

        val attributes = filter.filter { it.trait == null }.map { it.label }.toSet()
        val traits = filter.filter { it.trait != null }.map { it.trait!!.id }.toSet()

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

            var filter: Set<AttributeAdapter.AttributeModel> =
                getPersistedFilter(ctx).toMutableSet()

            val models = ArrayList<AttributeAdapter.AttributeModel>()
            models.addAll(attributes.map { AttributeAdapter.AttributeModel(it) })
            models.addAll(traits.map { AttributeAdapter.AttributeModel(it.name, trait = it) })

            //initialize which attributes are checked, if no filter is saved then check all
            val checked = models.map {
                it in filter
            }.toBooleanArray()


            filterDialog =
                AlertDialog.Builder(activity, R.style.AppAlertDialog)
                    .setTitle(R.string.fragment_summary_filter_title)
                    .setMultiChoiceItems(
                        models.map { it.label }.toTypedArray(),
                        checked
                    ) { _, which, isChecked ->
                        val item = models[which]
                        filter = (if (isChecked) {
                            filter.plus(item)
                        } else {
                            filter.minus(item)
                        })
                    }.setPositiveButton(android.R.string.ok) { dialog, _ ->
                        setPersistedFilter(ctx, filter.toList())
                        dialog.dismiss()
                        val attributeModels = attributes.map { AttributeAdapter.AttributeModel(it) }
                        val traitAttributeModels =
                            traits.map { AttributeAdapter.AttributeModel(it.name, trait = it) }
                        val models = attributeModels + traitAttributeModels
                        loadData(collector, models)
                    }.setNegativeButton(android.R.string.cancel) { dialog, _ ->
                        dialog.dismiss()
                    }.setNeutralButton(R.string.dialog_fragment_summary_neutral_button) { _, _ -> }
                    .create()

            if (isAdded && filterDialog?.isShowing != true) {

                filterDialog?.show()

                filterDialog?.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { _ ->

                    val list = (filterDialog as AlertDialog).listView

                    list.choiceMode = ListView.CHOICE_MODE_MULTIPLE

                    val toggle = (filter?.size ?: 0) < models.size

                    filter = if (toggle) {

                        filter.plus(models)

                    } else setOf()

                    models.forEachIndexed { index, _ ->

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
    //TODO necessary to switch Name usage?
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