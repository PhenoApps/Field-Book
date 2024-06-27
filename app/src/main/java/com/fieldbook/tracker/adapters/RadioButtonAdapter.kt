package com.fieldbook.tracker.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R

/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 */
class RadioButtonAdapter :
    ListAdapter<RadioButtonAdapter.RadioButtonModel, RadioButtonAdapter.ViewHolder>(
        DiffCallback()
    ) {

    private var selectedPosition = -1

    data class RadioButtonModel(
        val text: String,
        val callback: (() -> Unit)?,
        var isSelected: Boolean = false
    )

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val radioButton: RadioButton = itemView.findViewById(R.id.radio_button)
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.radio_button_item, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val item = currentList[position]
        viewHolder.radioButton.text = item.text
        viewHolder.radioButton.isChecked = item.isSelected

        viewHolder.radioButton.setOnClickListener {
            val currentPosition = viewHolder.bindingAdapterPosition
            // check if the currentPosition is a valid one
            if (currentPosition != -1) {
                // check if the currentPosition is different than the previous position
                if (currentPosition != selectedPosition && selectedPosition != -1) {
                    // unselect the previously selected item
                    currentList[selectedPosition].isSelected = false
                    notifyItemChanged(selectedPosition)
                }
                // select the current position
                item.isSelected = true
                selectedPosition = currentPosition

                item.callback?.invoke()

                notifyItemChanged(currentPosition)
            }
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = currentList.size

    fun getSelectedPosition() = selectedPosition

    class DiffCallback : DiffUtil.ItemCallback<RadioButtonModel>() {

        override fun areItemsTheSame(
            oldItem: RadioButtonModel, newItem: RadioButtonModel
        ): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(
            oldItem: RadioButtonModel, newItem: RadioButtonModel
        ): Boolean {
            return oldItem == newItem
        }
    }
}