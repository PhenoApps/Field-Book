package com.fieldbook.tracker.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R

/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 */
class SummaryAdapter(private val controller: SummaryController) :
    ListAdapter<SummaryAdapter.SummaryListModel, SummaryAdapter.ViewHolder>(DiffCallback()) {

    data class SummaryListModel(val key: String, val value: String, val isTrait: Boolean)

    interface SummaryController {
        fun onAttributeClicked(attribute: String)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val keyTextView: TextView = view.findViewById(R.id.list_item_summary_key_tv)
        val valueTextView: TextView = view.findViewById(R.id.list_item_summary_value_tv)
        val navButton: ImageView = view.findViewById(R.id.list_item_summary_ib)

        init {
            navButton.setOnClickListener {
                controller.onAttributeClicked(keyTextView.text.toString())
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
            viewHolder.keyTextView.text = key
            viewHolder.valueTextView.text = value
            viewHolder.navButton.visibility = if (isTrait) View.VISIBLE else View.INVISIBLE
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = currentList.size

    class DiffCallback : DiffUtil.ItemCallback<SummaryListModel>() {

        override fun areItemsTheSame(
            oldItem: SummaryListModel, newItem: SummaryListModel
        ): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(
            oldItem: SummaryListModel, newItem: SummaryListModel
        ): Boolean {
            return oldItem == newItem
        }
    }
}