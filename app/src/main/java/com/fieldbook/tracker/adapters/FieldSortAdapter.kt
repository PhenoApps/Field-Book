package com.fieldbook.tracker.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import java.util.*

/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 */
class FieldSortAdapter(private val sorter: FieldSorter):
        ListAdapter<String, FieldSortAdapter.ViewHolder>(DiffCallback()) {

    interface FieldSorter {
       fun onDeleteItem(attribute: String)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val valueTextView: TextView = view.findViewById(R.id.list_item_field_sort_value_tv)
        private val deleteButton: ImageButton = view.findViewById(R.id.list_item_field_sort_delete_btn)

        init {
            deleteButton.setOnClickListener {
                val value = valueTextView.tag as String
                sorter.onDeleteItem(value)
            }
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.list_item_field_sort, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        with (currentList[position]) {
            viewHolder.valueTextView.text = this
            viewHolder.valueTextView.tag = this
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = currentList.size

    fun moveItem(from: Int, to: Int) {
        val list = currentList.toMutableList()
        Collections.swap(list, from, to)
        submitList(list)
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