package com.fieldbook.tracker.adapters.spectral

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R

/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 */

class ColorAdapter(private val listener: ColorListListener) : ListAdapter<String, ColorAdapter.ViewHolder>(DiffCallback()) {

    interface ColorListListener {
        fun onColorDeleted(position: Int, onDelete: (() -> Unit)? = null)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_color, parent, false)
        return ViewHolder(v as ConstraintLayout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        with(currentList[position]) {
            holder.itemView.tag = this
            if (this == "NA") {
                holder.colorView.setBackgroundResource(R.drawable.main_ic_missing)
            } else {
                holder.colorView.setBackgroundColor(Integer.valueOf(this))
            }
            holder.closeButton.setOnClickListener {
                listener.onColorDeleted(position)
            }
        }
    }

    override fun getItemCount(): Int {
        return currentList.size
    }

    class ViewHolder(v: ConstraintLayout) : RecyclerView.ViewHolder(v) {
        var colorView: View = v.findViewById(R.id.color_preview)
        var closeButton: ImageButton = v.findViewById(R.id.close_btn)
    }

    class DiffCallback : DiffUtil.ItemCallback<String>() {

        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }
}