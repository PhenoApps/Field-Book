package com.fieldbook.tracker.adapters

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
        theme.resolveAttribute(R.attr.fb_color_accent, inactiveTraitColor, true)

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

    private val defaultMaxSizePx: Int by lazy {
        val context = traitBoxView.context
        val defaultMaxSizeDp = context.resources.getDimension(R.dimen.fb_trait_status_bar_icon_default_max_size)
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            defaultMaxSizeDp,
            context.resources.displayMetrics
        ).toInt() // in px
    }

    // calculate item size and update the view
    fun calculateAndSetItemSize(viewHolder: ViewHolder) {
        val recyclerView = traitBoxView.getRecyclerView() ?: return
        recyclerView.post {
            val itemCount = itemCount

            val parentWidth = recyclerView.width

            val availableWidth = parentWidth - recyclerView.paddingLeft - recyclerView.paddingRight
            val calculatedSize = availableWidth / itemCount

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
            notifyItemChanged(currentSelection)
            notifyItemChanged(newSelection)
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
            notifyItemChanged(currentSelection)
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = currentList.size

    class DiffCallback : DiffUtil.ItemCallback<TraitBoxItemModel>() {

        override fun areItemsTheSame(oldItem: TraitBoxItemModel, newItem: TraitBoxItemModel): Boolean {
            return oldItem.trait == newItem.trait
        }

        override fun areContentsTheSame(oldItem: TraitBoxItemModel, newItem: TraitBoxItemModel): Boolean {
            return oldItem.hasObservation == newItem.hasObservation
        }
    }
}