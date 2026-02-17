package com.fieldbook.tracker.traits.formats.parameters

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckedTextView
import android.widget.ListView
import android.widget.Button
import android.widget.TextView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.devices.multispeq.MultispeqParameter
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.ValidationResult

/**
 * Trait-creator parameter card that shows the current parameter selection summary.
 *
 * The selection is stored as a JSON array of [MultispeqParameter] names in
 * [TraitObject.categories]. During collection, [MultispeqTraitLayout] reads
 * this field to assemble the dynamic RIDES 2.1 sub-protocol set and to know
 * which values to display after measurement.
 *
 * Editing is done via the Options chip on the Trait Details page, which calls
 * [showDialog] directly. The card itself (shown in the trait creator) is read-only
 * and displays only a summary line.
 */
class MultispeqParametersParameter : BaseFormatParameter(
    nameStringResourceId = R.string.multispeq_parameters_card_title,
    defaultLayoutId = R.layout.list_item_trait_parameter_multispeq_parameters,
    parameter = Parameters.MULTISPEQ_PARAMETERS,
) {
    override fun createViewHolder(parent: ViewGroup): BaseFormatParameter.ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_trait_parameter_multispeq_parameters, parent, false)
        return ViewHolder(v)
    }

    class ViewHolder(itemView: View) : BaseFormatParameter.ViewHolder(itemView) {

        private val summaryText: TextView =
            itemView.findViewById(R.id.parameter_multispeq_summary)
        private val editButton: Button =
            itemView.findViewById(R.id.parameter_multispeq_edit_btn)

        private var selected: MutableSet<MultispeqParameter> = mutableSetOf()

        override fun bind(parameter: BaseFormatParameter, initialTraitObject: TraitObject?) {
            super.bind(parameter, initialTraitObject)
            editButton.setOnClickListener {
                showDialog(itemView.context, MultispeqParameter.toJsonString(selected)) { newJson ->
                    selected = MultispeqParameter.fromJsonString(newJson).toMutableSet()
                    updateSummary()
                }
            }
        }

        override fun merge(traitObject: TraitObject) = traitObject.apply {
            categories = MultispeqParameter.toJsonString(selected)
        }

        override fun load(traitObject: TraitObject?): Boolean {
            val json = traitObject?.categories
            selected = if (json.isNullOrBlank()) {
                mutableSetOf()
            } else {
                MultispeqParameter.fromJsonString(json).toMutableSet()
            }
            updateSummary()
            return true
        }

        override fun validate(
            traitRepo: TraitRepository,
            initialTraitObject: TraitObject?
        ) = ValidationResult()

        private fun updateSummary() {
            val total = MultispeqParameter.values().size
            val count = selected.size
            summaryText.text = if (count == 0) {
                itemView.context.getString(R.string.multispeq_parameters_none_selected)
            } else {
                itemView.context.getString(R.string.multispeq_parameters_summary, count, total)
            }
        }
    }

    companion object {

        /**
         * Shows the grouped parameter selection dialog.
         *
         * Group headers are non-interactive bold labels. Environment parameters are
         * automatically selected and disabled whenever any non-Environment parameter
         * is checked (since [no_leaf_baseline] always runs and provides environment data).
         *
         * @param ctx      Android context for building the dialog.
         * @param currentJson  Current JSON string from [TraitObject.categories].
         * @param onConfirm  Called with the updated JSON string when the user taps Save.
         */
        fun showDialog(ctx: Context, currentJson: String?, onConfirm: (String) -> Unit) {
            val selected = MultispeqParameter.fromJsonString(currentJson).toMutableSet()

            data class ListItem(val param: MultispeqParameter?, val groupLabel: String?)

            val items = mutableListOf<ListItem>()
            for (group in MultispeqParameter.Group.values()) {
                items.add(ListItem(null, group.label))
                MultispeqParameter.values()
                    .filter { it.group == group }
                    .forEach { items.add(ListItem(it, null)) }
            }

            fun isEnvForced() =
                selected.any { it.group != MultispeqParameter.Group.ENVIRONMENT }

            val listView = ListView(ctx)

            val adapter = object : BaseAdapter() {
                private val TYPE_HEADER = 0
                private val TYPE_PARAM = 1

                override fun getCount() = items.size
                override fun getItem(pos: Int) = items[pos]
                override fun getItemId(pos: Int) = pos.toLong()
                override fun getViewTypeCount() = 2
                override fun getItemViewType(pos: Int) =
                    if (items[pos].param == null) TYPE_HEADER else TYPE_PARAM

                override fun isEnabled(pos: Int): Boolean {
                    val item = items[pos]
                    if (item.param == null) return false
                    return !(item.param.group == MultispeqParameter.Group.ENVIRONMENT && isEnvForced())
                }

                override fun areAllItemsEnabled() = false

                override fun getView(pos: Int, convertView: View?, parent: ViewGroup?): View {
                    val item = items[pos]
                    val density = ctx.resources.displayMetrics.density
                    return if (item.param == null) {
                        val tv = (convertView as? TextView) ?: TextView(ctx)
                        tv.text = item.groupLabel
                        tv.setTypeface(null, Typeface.BOLD)
                        tv.setTextColor(ctx.getColor(android.R.color.darker_gray))
                        val hPad = (16 * density).toInt()
                        tv.setPadding(hPad, (10 * density).toInt(), hPad, (4 * density).toInt())
                        tv
                    } else {
                        val ctv = (convertView as? CheckedTextView) ?: CheckedTextView(ctx).also {
                            val ta = ctx.theme.obtainStyledAttributes(
                                intArrayOf(android.R.attr.listChoiceIndicatorMultiple)
                            )
                            it.checkMarkDrawable = ta.getDrawable(0)
                            ta.recycle()
                        }
                        val label = if (item.param.unit.isNotEmpty())
                            "${item.param.displayName} (${item.param.unit})"
                        else item.param.displayName
                        ctv.text = label
                        val envForced = isEnvForced()
                        val isEnv = item.param.group == MultispeqParameter.Group.ENVIRONMENT
                        ctv.isChecked = isEnv && envForced || selected.contains(item.param)
                        ctv.isEnabled = !(isEnv && envForced)
                        val hPad = (16 * density).toInt()
                        val vPad = (10 * density).toInt()
                        ctv.setPadding(hPad, vPad, hPad, vPad)
                        ctv
                    }
                }
            }

            listView.adapter = adapter

            listView.setOnItemClickListener { _, view, pos, _ ->
                val item = items[pos]
                val param = item.param ?: return@setOnItemClickListener
                // Ignore taps on forced-disabled environment params
                if (param.group == MultispeqParameter.Group.ENVIRONMENT && isEnvForced()) return@setOnItemClickListener
                val ctv = view as? CheckedTextView ?: return@setOnItemClickListener
                val nowChecked = !ctv.isChecked
                ctv.isChecked = nowChecked
                if (nowChecked) {
                    selected.add(param)
                    // Non-environment selection forces all environment params
                    if (param.group != MultispeqParameter.Group.ENVIRONMENT) {
                        selected.addAll(
                            MultispeqParameter.values()
                                .filter { it.group == MultispeqParameter.Group.ENVIRONMENT }
                        )
                        adapter.notifyDataSetChanged()
                    }
                } else {
                    selected.remove(param)
                    // Unchecking a non-env param may release the env lock
                    adapter.notifyDataSetChanged()
                }
            }

            AlertDialog.Builder(ctx, R.style.AppAlertDialog)
                .setTitle(R.string.multispeq_parameters_card_title)
                .setView(listView)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    onConfirm(MultispeqParameter.toJsonString(selected))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}
