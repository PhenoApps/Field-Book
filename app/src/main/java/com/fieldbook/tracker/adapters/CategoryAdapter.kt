package com.fieldbook.tracker.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories

/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 */
class CategoryAdapter(private val context: CategoryListItemOnClick):
        ListAdapter<BrAPIScaleValidValuesCategories, CategoryAdapter.ViewHolder>(DiffCallback()) {

    interface CategoryListItemOnClick {
        fun onCategoryClick(label: String)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val labelTextView: TextView = view.findViewById(R.id.list_item_category_label_tv)
        val valueTextView: TextView = view.findViewById(R.id.list_item_category_value_tv)
        val deleteButton: Button = view.findViewById(R.id.list_item_category_delete_btn)

        init {
            deleteButton.setOnClickListener {
                val label = labelTextView.text.toString()
                context.onCategoryClick(label)
            }
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.list_item_category, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        with (currentList[position]) {
            viewHolder.labelTextView.text = label
            viewHolder.valueTextView.text = value
            viewHolder.itemView.tag = label
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = currentList.size

    class DiffCallback : DiffUtil.ItemCallback<BrAPIScaleValidValuesCategories>() {

        override fun areItemsTheSame(oldItem: BrAPIScaleValidValuesCategories, newItem: BrAPIScaleValidValuesCategories): Boolean {
            return oldItem.label == newItem.label
        }

        override fun areContentsTheSame(oldItem: BrAPIScaleValidValuesCategories, newItem: BrAPIScaleValidValuesCategories): Boolean {
            return oldItem.label == newItem.label
        }
    }
}