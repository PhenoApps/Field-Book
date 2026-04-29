package com.fieldbook.tracker.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R

/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 */
class SummaryAdapter(private val controller: SummaryController) :
    ListAdapter<AttributeAdapter.AttributeModel, SummaryAdapter.ViewHolder>(AttributeAdapter.DiffCallback()) {

    interface SummaryController {
        fun onAttributeClicked(attribute: AttributeAdapter.AttributeModel)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val keyTextView: TextView = view.findViewById(R.id.list_item_summary_key_tv)
        val valueTextView: TextView = view.findViewById(R.id.list_item_summary_value_tv)
        val navButton: AppCompatImageView = view.findViewById(R.id.list_item_summary_ib)

        init {
            view.setOnClickListener {
                controller.onAttributeClicked(view.tag as AttributeAdapter.AttributeModel)
            }
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.list_item_summary, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        with(currentList[position]) {
            viewHolder.itemView.tag = this
            viewHolder.keyTextView.text = label
            viewHolder.valueTextView.text = value
            viewHolder.navButton.visibility = if (trait != null) View.VISIBLE else View.INVISIBLE
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = currentList.size

}