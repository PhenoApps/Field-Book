package com.fieldbook.tracker.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject

/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 */
class AttributeAdapter(private val controller: AttributeAdapterController, private val selected: AttributeModel?) :
    ListAdapter<AttributeAdapter.AttributeModel, AttributeAdapter.ViewHolder>(DiffCallback()) {

    interface AttributeAdapterController {
        fun onAttributeClicked(model: AttributeModel, position: Int)
    }

    data class AttributeModel(
        val label: String,
        val value: String? = null,
        val trait: TraitObject? = null
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_attribute, parent, false)
        return ViewHolder(v as ConstraintLayout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        holder.itemView.setOnClickListener {
            controller.onAttributeClicked(holder.itemView.tag as AttributeModel, position)
        }

        with (currentList[position]) {
            holder.itemView.tag = this
            setViewHolderText(holder, this.label)

            holder.attributeTv.setBackgroundResource(
                if (selected == this) R.drawable.table_cell_selected else R.drawable.cell)

        }
    }

    override fun getItemCount(): Int {
        return currentList.size
    }

    private fun setViewHolderText(holder: ViewHolder, label: String?) {
        holder.attributeTv.text = "$label"
    }


    class ViewHolder(v: ConstraintLayout) : RecyclerView.ViewHolder(v) {

        var attributeTv: TextView = v.findViewById(R.id.list_item_attribute_tv)

    }

    class DiffCallback : DiffUtil.ItemCallback<AttributeModel>() {

        override fun areItemsTheSame(oldItem: AttributeModel, newItem: AttributeModel): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: AttributeModel, newItem: AttributeModel): Boolean {
            return oldItem == newItem
        }
    }
}