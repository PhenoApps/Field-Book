package com.fieldbook.tracker.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R

/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 */

class AttributeAdapter(private val controller: AttributeAdapterController) :
    ListAdapter<String, AttributeAdapter.ViewHolder>(DiffCallback()) {

    interface AttributeAdapterController {
        fun onAttributeClicked(label: String, position: Int)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_attribute, parent, false)
        return ViewHolder(v as ConstraintLayout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        holder.itemView.setOnClickListener {
            controller.onAttributeClicked(holder.itemView.tag as String, position)
        }

        with (currentList[position]) {
            holder.itemView.tag = this
            setViewHolderText(holder, this)
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

    class DiffCallback : DiffUtil.ItemCallback<String>() {

        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }
}