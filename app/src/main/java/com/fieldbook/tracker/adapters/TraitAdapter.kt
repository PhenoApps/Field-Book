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

/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 */
class TraitAdapter(private val sorter: TraitSorter):
        ListAdapter<TraitObject, TraitAdapter.ViewHolder>(DiffCallback()) {

    public var infoDialogShown: Boolean = false

    interface TraitSorter {
        fun onDrag(item: TraitAdapter.ViewHolder)
        fun getDatabase(): DataHelper
        fun onMenuItemClicked(v: View, trait: TraitObject)
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

                if (visibleCheckBox.isChecked) {
                    sorter.getDatabase().updateTraitVisibility(trait.trait, true)
                } else {
                    sorter.getDatabase().updateTraitVisibility(trait.trait, false)
                }
            }

            menuImageView.setOnClickListener { v ->

                val trait = view.tag as TraitObject

                sorter.onMenuItemClicked(v, trait)

            }
        }
    }

    fun moveItem(from: Int, to: Int) {

        val list = currentList.toMutableList()

        list[to] = list[from].also { list[from] = list[to] }

        submitList(list)

        val size = list.size
        for (i in 0 until size) {
            sorter.getDatabase().updateTraitPosition(list[i].id, i)
        }
    }

    public fun getTraitItem(position: Int): TraitObject {
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
            viewHolder.nameTextView.text = this.trait
            viewHolder.formatImageView.setBackgroundResource(when(this.format) {
                "numeric" -> R.drawable.ic_trait_numeric
                "categorical" -> R.drawable.ic_trait_categorical
                "date" -> R.drawable.ic_trait_date
                "percent" -> R.drawable.ic_trait_percent
                "boolean" -> R.drawable.ic_trait_boolean
                "text" -> R.drawable.ic_trait_text
                "photo" -> R.drawable.ic_trait_camera
                "audio" -> R.drawable.ic_trait_audio
                "counter" -> R.drawable.ic_trait_counter
                "disease rating" -> R.drawable.ic_trait_disease_rating
                "rust rating" -> R.drawable.ic_trait_disease_rating
                "multicat" -> R.drawable.ic_trait_multicat
                "location" -> R.drawable.ic_trait_location
                "barcode" -> R.drawable.ic_trait_barcode
                "zebra label print" -> R.drawable.ic_trait_labelprint
                "gnss" -> R.drawable.ic_trait_gnss
                "usb camera" -> R.drawable.ic_trait_usb
                "gopro" -> R.drawable.ic_trait_gopro
                else -> R.drawable.ic_reorder
            })

            // Check or uncheck the list items
            val visible = sorter.getDatabase().traitVisibility[this.trait]
            viewHolder.visibleCheckBox.isChecked = visible == null || visible == "true"
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = currentList.size

    class DiffCallback : DiffUtil.ItemCallback<TraitObject>() {

        override fun areItemsTheSame(oldItem: TraitObject, newItem: TraitObject): Boolean {
            return oldItem.trait == newItem.trait
        }

        override fun areContentsTheSame(oldItem: TraitObject, newItem: TraitObject): Boolean {
            return oldItem.trait == newItem.trait
        }
    }
}