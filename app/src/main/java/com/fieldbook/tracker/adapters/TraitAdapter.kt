package com.fieldbook.tracker.adapters

import android.util.Log
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
import com.fieldbook.tracker.activities.TraitEditorActivity
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.offbeat.traits.formats.Formats


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
        fun onMenuItemClicked(v: View, trait: TraitObject)
        fun onDragComplete(list: List<TraitObject>)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.list_item_trait_trait_name)
        val formatImageView = view.findViewById<ImageView>(R.id.traitType)
        val visibleCheckBox = view.findViewById<CheckBox>(R.id.visible)
        val dragSortImageView = view.findViewById<ImageView>(R.id.dragSort)
        val menuImageView = view.findViewById<ImageView>(R.id.popupMenu)

        init {

            dragSortImageView.setOnTouchListener { v, event ->

                if (event.action == MotionEvent.ACTION_DOWN) {

                    sorter.onDrag(this)
                }

                v.performClick()

            }

            visibleCheckBox.setOnCheckedChangeListener { _, _ ->

                val trait = view.tag as TraitObject

                if (visibleCheckBox.isChecked()) {
                    sorter.getDatabase().updateTraitVisibility(trait.getId(), true);
                } else {
                    sorter.getDatabase().updateTraitVisibility(trait.getId(), false);
                }
            }

            menuImageView.setOnClickListener { v ->

                val trait = view.tag as TraitObject

                sorter.onMenuItemClicked(v, trait)

            }
        }
    }

//    fun moveItem(from: Int, to: Int) {
//        if (from == to) return // No need to move if the positions are the same
//
//        val list = currentList.toMutableList()
//
//        // Move the item
//        val movedItem = list.removeAt(from)
//        list.add(to, movedItem)
//
//        Log.d("TraitAdapter", "Moving item '${movedItem.name}' from position $from to position $to")
//
//
//        notifyItemMoved(from, to)
//    }

    fun moveItem(from: Int, to: Int) {
        if (from == to) return

        val list = currentList.toMutableList()

        // Ensure we're moving the initially selected item
        val movedItem = list[from]
        list.removeAt(from)
        list.add(to, movedItem)

        Log.d("TraitAdapter", "Moving item '${movedItem.name}' from position $from to position $to")

        submitList(list) // Ensure the list is updated correctly
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
            viewHolder.nameTextView.text = this.name

            val icon = Formats.entries
                .find { it.getDatabaseName() == this.format }?.getIcon()

            viewHolder.formatImageView.setBackgroundResource(icon ?: R.drawable.ic_reorder)

            // Check or uncheck the list items
            viewHolder.visibleCheckBox.isChecked = this.visible
//            val visible = sorter.getDatabase().traitVisibility[this.name]
//            viewHolder.visibleCheckBox.isChecked = visible == null || visible == "true"
        }
    }

    fun onDragComplete() {
        val list = currentList.toMutableList()
        // Log the list order before saving to the database
        Log.d("TraitAdapter", "onDragComplete - Updated list: ${list.map { it.name to it.id }}")

        sorter.onDragComplete(list) // Pass the updated list to the activity

        // Update positions in the database
        for (i in list.indices) {
            Log.d("TraitAdapter", "Updating trait '${list[i].name}' to position $i")
            sorter.getDatabase().updateTraitPosition(list[i].id, i)
        }

        // Submit the updated list to reflect changes in the UI
        submitList(list)
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