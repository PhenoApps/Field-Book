package com.fieldbook.tracker.adapters

import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.views.TraitBoxView

class TraitsStatusAdapter(private val traitBoxView: TraitBoxView) :
    ListAdapter<TraitsStatusAdapter.TraitBoxItemModel, TraitsStatusAdapter.ViewHolder>(
        DiffCallback()
    ) {

    data class TraitBoxItemModel(val trait: String, var hasObservation: Boolean)
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var imageView: ImageView = view.findViewById(R.id.traitStatus)
    }

    private var currentSelection: Int = -1

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.trait_status, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        calculateAndSetItemSize(viewHolder)

        val context = viewHolder.imageView.context
        val theme = context.theme

        val activeTraitColor = TypedValue()
        theme.resolveAttribute(R.attr.fb_color_primary_dark, activeTraitColor, true)
        val inactiveTraitColor = TypedValue()
        theme.resolveAttribute(R.attr.fb_trait_boolean_false_color, inactiveTraitColor, true)

        with(currentList[position]) {
            if (position == currentSelection) {
                if (hasObservation) {
                    viewHolder.imageView.setImageResource(R.drawable.square_rounded_filled)
                } else {
                    viewHolder.imageView.setImageResource(R.drawable.square_rounded_outline)
                }
                viewHolder.imageView.setColorFilter(activeTraitColor.data)
            } else {
                if (hasObservation) {
                    viewHolder.imageView.setImageResource(R.drawable.circle_filled)
                } else {
                    viewHolder.imageView.setImageResource(R.drawable.circle_outline)
                }
                viewHolder.imageView.setColorFilter(inactiveTraitColor.data)
            }

//            viewHolder.imageView.setOnClickListener {
////                setCurrentSelection(position)
//                traitBoxView.setSelection(position)
//
//                traitBoxView.rangeSuppress?.let { traitBoxView.loadLayout(it) }
//            }

        }


    }

    // calculate item size and update the view
    fun calculateAndSetItemSize(viewHolder: ViewHolder) {
        val recyclerView = traitBoxView.getRecyclerView() ?: return
        recyclerView.post {
            val itemCount = itemCount
            val context = viewHolder.imageView.context

            val parentWidth = recyclerView.width

            val availableWidth = parentWidth - recyclerView.paddingLeft - recyclerView.paddingRight
            val calculatedSize = availableWidth / itemCount

            val defaultMaxSizeDp = context.resources.getDimension(R.dimen.fb_trait_status_bar_icon_default_max_size)
            val defaultMaxSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                defaultMaxSizeDp,
                traitBoxView.context.resources.displayMetrics
            ).toInt() // in px

            val itemSize = minOf(calculatedSize, defaultMaxSizePx)

            viewHolder.imageView.layoutParams = viewHolder.imageView.layoutParams.apply {
                width = itemSize
                height = itemSize
            }
        }

    }

    fun setCurrentSelection(newSelection: Int) {
        // Only update if the new selection is different
        if (currentSelection != newSelection) {
            currentSelection = newSelection
            notifyDataSetChanged() // Notify adapter to refresh all items
        }
    }

    // update an item status in the list
    fun updateCurrentTraitStatus(value: Boolean) {
        if (currentSelection in currentList.indices) {
            // Create a new list with the updated item
            val updatedList = currentList.toMutableList().apply {
                this[currentSelection].hasObservation = value
            }
            submitList(updatedList)
            notifyDataSetChanged()
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = currentList.size

    class DiffCallback : DiffUtil.ItemCallback<TraitBoxItemModel>() {

        override fun areItemsTheSame(oldItem: TraitBoxItemModel, newItem: TraitBoxItemModel): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: TraitBoxItemModel, newItem: TraitBoxItemModel): Boolean {
            return oldItem == newItem
        }
    }
}