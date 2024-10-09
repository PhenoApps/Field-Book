package com.fieldbook.tracker.adapters

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
        var imageView: ImageView = view.findViewById(R.id.traitCircle)
    }

    private var currentSelection: Int = -1

    private var itemsCount = 0

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.trait_circle, viewGroup, false)


        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        calculateAndSetItemSize(viewHolder)

        with(currentList[position]) {
            if (hasObservation) {
                viewHolder.imageView.setImageResource(R.drawable.circle_filled)
            } else {
                viewHolder.imageView.setImageResource(R.drawable.circle_outline)
            }

            if (position == currentSelection) {
                viewHolder.imageView.setColorFilter(
                    ContextCompat.getColor(
                        viewHolder.imageView.context,
                        R.color.main_trait_percent_start_color
                    )
                )
            } else {
                viewHolder.imageView.setColorFilter(
                    ContextCompat.getColor(
                        viewHolder.imageView.context,
                        R.color.main_trait_boolean_false_color
                    )
                )
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
        val itemCount = itemCount

        val parentWidth = traitBoxView.getRecyclerView()?.width ?: 0

        val availableWidth = parentWidth - (traitBoxView.getRecyclerView()?.paddingLeft ?: 0) - (traitBoxView.getRecyclerView()?.paddingRight ?: 0)
        val calculatedSize = availableWidth / itemCount

        val defaultMaxSizeDp = 25 // in dp
        val defaultMaxSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            defaultMaxSizeDp.toFloat(),
            traitBoxView.context.resources.displayMetrics
        ).toInt() // in px

        val itemSize = minOf(calculatedSize, defaultMaxSizePx)

        viewHolder.imageView.layoutParams = viewHolder.imageView.layoutParams.apply {
            width = itemSize
            height = itemSize
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