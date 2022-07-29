package com.fieldbook.tracker.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R

/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 */
class LanguageTextAdapter(private val listener: OnClickListItem,
                          languageIdMap: Map<String, String>? = null) :
        ListAdapter<Map.Entry<String, String>, LanguageTextAdapter.ViewHolder>(DiffCallback()) {

    interface OnClickListItem {
        fun onItemClicked(obj: Any)
    }

    init {
        languageIdMap?.let { submitList(it.entries.toList()) }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val languageTextView: TextView = view.findViewById(R.id.list_item_language_tv)

        init {
            // Define click listener for the ViewHolder's View.
            view.setOnClickListener {
                listener.onItemClicked(view.tag as Pair<*, *>)
            }
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.list_item_language, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        with(currentList[position]) {
            viewHolder.languageTextView.text = value
            viewHolder.itemView.tag = key to value
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = currentList.size

    class DiffCallback : DiffUtil.ItemCallback<Map.Entry<String, String>>() {

        override fun areItemsTheSame(oldItem: Map.Entry<String, String>, newItem: Map.Entry<String, String>): Boolean {
            return oldItem.key == newItem.key
        }

        override fun areContentsTheSame(oldItem: Map.Entry<String, String>, newItem: Map.Entry<String, String>): Boolean {
            return oldItem.key == newItem.key
        }
    }
}