package com.fieldbook.tracker.adapters

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.offbeat.traits.formats.Formats

/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 */
class TraitFormatAdapter(private val context: Context) :
    ListAdapter<Formats, TraitFormatAdapter.ViewHolder>(DiffCallback()) {

    var selectedFormat: Formats? = null

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconBtn: ImageButton = view.findViewById(R.id.list_item_traits_grid_ib)
        val nameTv: TextView = view.findViewById(R.id.list_item_traits_grid_tv)

        fun setBackgroundToggle(format: Formats) {

            iconBtn.setBackgroundColor(if (format == selectedFormat) Color.GRAY else Color.TRANSPARENT)

        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.list_item_traits_grid, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        with(currentList[position]) {
            viewHolder.iconBtn.setImageResource(getIcon())
            viewHolder.nameTv.text = getName(context)

            viewHolder.setBackgroundToggle(this)

            viewHolder.iconBtn.setOnClickListener {

                selectedFormat = if (this == selectedFormat) null else this

                viewHolder.setBackgroundToggle(this)

                notifyItemRangeChanged(0, currentList.size)

            }
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = currentList.size

    class DiffCallback : DiffUtil.ItemCallback<Formats>() {

        override fun areItemsTheSame(
            oldItem: Formats, newItem: Formats
        ): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(
            oldItem: Formats, newItem: Formats
        ): Boolean {
            return oldItem == newItem
        }
    }
}