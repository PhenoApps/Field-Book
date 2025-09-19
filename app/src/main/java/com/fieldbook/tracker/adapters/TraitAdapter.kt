package com.fieldbook.tracker.adapters

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.Formats

/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 */
class TraitAdapter(private val sorter: TraitSorter):
        ListAdapter<TraitObject, TraitAdapter.ViewHolder>(DiffCallback()) {

    var infoDialogShown: Boolean = false

    interface TraitSorter {
        fun onDrag(item: TraitAdapter.ViewHolder)
        fun getDatabase(): DataHelper
    }

    // Add OnTraitSelectedListener interface
    interface OnTraitSelectedListener {
        fun onTraitSelected(traitId: String)
    }

    // Add a property to store the listener
    private var onTraitSelectedListener: OnTraitSelectedListener? = null

    // Add a method to set the listener
    fun setOnTraitSelectedListener(listener: OnTraitSelectedListener) {
        onTraitSelectedListener = listener
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.list_item_trait_trait_name)
        val formatImageView = view.findViewById<ImageView>(R.id.traitType)
        val visibleCheckBox = view.findViewById<CheckBox>(R.id.visible)
        val dragSortImageView = view.findViewById<ImageView>(R.id.dragSort)

        init {

            dragSortImageView.setOnTouchListener { v, event ->

                if (event.action == MotionEvent.ACTION_DOWN) {

                    sorter.onDrag(this)
                }

                v.performClick()

            }

            visibleCheckBox.setOnCheckedChangeListener { _, _ ->

                val trait = view.tag as TraitObject

                if (visibleCheckBox.isChecked) {
                    sorter.getDatabase().updateTraitVisibility(trait.id, true)
                } else {
                    sorter.getDatabase().updateTraitVisibility(trait.id, false)
                }
            }

        }
    }

    fun moveItem(from: Int, to: Int) {

        val list = currentList.toMutableList()

        list[to] = list[from].also { list[from] = list[to] }

        submitList(list)

        val size = list.size
        for (i in 0 until size) {
            sorter.getDatabase().updateTraitPosition(list[i].id, i + 1)
        }
    }

    fun getTraitItem(position: Int): TraitObject {
        return currentList[position]
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.list_item_trait, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        with (currentList[position]) {
            viewHolder.itemView.tag = this
            viewHolder.nameTextView.text = this.alias

            val icon = Formats.entries
                .find { it.getDatabaseName() == this.format }?.getIcon()

            viewHolder.formatImageView.setBackgroundResource(icon ?: R.drawable.ic_reorder)

            // Check or uncheck the list items
            val visible = sorter.getDatabase().traitVisibility[this.name]
            viewHolder.visibleCheckBox.isChecked = visible == null || visible == "true"
            
            // Add click listener to the item view
            viewHolder.itemView.setOnClickListener {
                onTraitSelectedListener?.onTraitSelected(this.id)
            }
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = currentList.size

    class DiffCallback : DiffUtil.ItemCallback<TraitObject>() {

        override fun areItemsTheSame(oldItem: TraitObject, newItem: TraitObject): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: TraitObject, newItem: TraitObject): Boolean {
            return oldItem == newItem
        }
    }
}