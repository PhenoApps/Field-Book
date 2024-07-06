package com.fieldbook.tracker.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R

/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 */
class OptionalSetupAdapter :
    ListAdapter<OptionalSetupAdapter.OptionalSetupModel, OptionalSetupAdapter.ViewHolder>(
        DiffCallback()
    ) {

    data class OptionalSetupModel(
        val setupTitle: String,
        val setupSummary: String,
        val onSelectCallback: (() -> Unit)?,
        val onUnselectCallback: (() -> Unit)?,
        var isChecked: Boolean = false
    )

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)
        val summary: TextView = itemView.findViewById(R.id.setup_summary)
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.app_intro_optional_setup_item, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val item = currentList[position]
        viewHolder.checkbox.text = item.setupTitle
        viewHolder.checkbox.isChecked = item.isChecked

        // initially the item is unchecked
        item.onUnselectCallback?.invoke()

        viewHolder.checkbox.setOnClickListener {
            // Toggle the checked state of the item
            item.isChecked = !item.isChecked
            viewHolder.checkbox.isChecked = item.isChecked

            if (item.isChecked) {
                Log.d("TAG", "CHECKED: ")
                item.onSelectCallback?.invoke()
            } else {
                Log.d("TAG", "UNCHECKED: ")
                item.onUnselectCallback?.invoke()
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<OptionalSetupModel>() {

        override fun areItemsTheSame(
            oldItem: OptionalSetupModel, newItem: OptionalSetupModel
        ): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(
            oldItem: OptionalSetupModel, newItem: OptionalSetupModel
        ): Boolean {
            return oldItem == newItem
        }
    }
}