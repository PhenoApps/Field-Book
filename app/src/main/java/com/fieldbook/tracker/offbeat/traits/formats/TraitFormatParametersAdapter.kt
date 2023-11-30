package com.fieldbook.tracker.offbeat.traits.formats

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.offbeat.traits.formats.parameters.BaseFormatParameter

/**
 * Recycler View Adapter used to populate new Trait dialog. Each item is a obs. var attribute.
 *
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 */
class TraitFormatParametersAdapter(
    private val controller: TraitFormatAdapterController,
    private val format: Formats
) :
    ListAdapter<BaseFormatParameter, BaseFormatParameter.ViewHolder>(DiffCallback()) {

    var initialTraitObject: TraitObject? = null

    val holders = ArrayList<BaseFormatParameter.ViewHolder>()

    interface TraitFormatAdapterController

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseFormatParameter.ViewHolder {
        val holder = format.getTraitFormatDefinition().parameters.find { it.viewType == viewType }
            ?.createViewHolder(parent) ?: super.createViewHolder(parent, viewType)
        holders.add(holder)
        return holder
    }

    override fun getItemViewType(position: Int) = currentList[position].viewType

    override fun onBindViewHolder(holder: BaseFormatParameter.ViewHolder, position: Int) {

        with(currentList[position]) {
            holder.itemView.tag = this
            holder.bind(this, initialTraitObject)
        }
    }

    override fun getItemCount(): Int {
        return currentList.size
    }

    class DiffCallback : DiffUtil.ItemCallback<BaseFormatParameter>() {

        override fun areItemsTheSame(
            oldItem: BaseFormatParameter,
            newItem: BaseFormatParameter
        ): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(
            oldItem: BaseFormatParameter,
            newItem: BaseFormatParameter
        ): Boolean {
            return oldItem == newItem
        }
    }
}