package com.fieldbook.tracker.adapters

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
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
class SortAdapter(private val sorter: Sorter):
        ListAdapter<String, SortAdapter.ViewHolder>(DiffCallback()) {

    interface Sorter {
       fun onDeleteItem(attribute: String)
       fun onDrag(item: ViewHolder)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val valueTextView: TextView = view.findViewById(R.id.list_item_field_sort_value_tv)
        private val deleteButton: ImageButton = view.findViewById(R.id.list_item_field_sort_delete_btn)
        private val dragButton: ImageView = view.findViewById(R.id.list_item_field_sort_drag_iv)
        init {

            deleteButton.setOnClickListener {
                val value = valueTextView.tag as String
                sorter.onDeleteItem(value)
            }

            dragButton.setOnTouchListener { v, event ->

                if (event.action == MotionEvent.ACTION_DOWN) {

                    sorter.onDrag(this)
                }

                v.performClick()
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

        list[to] = list[from].also { list[from] = list[to] }

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