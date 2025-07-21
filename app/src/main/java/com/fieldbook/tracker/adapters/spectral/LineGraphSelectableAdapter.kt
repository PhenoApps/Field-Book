package com.fieldbook.tracker.adapters.spectral

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R


/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 */

class LineGraphSelectableAdapter(private val listener: Listener? = null) : ListAdapter<LineGraphSelectableAdapter.LineColorData, LineGraphSelectableAdapter.ViewHolder>(DiffCallback()) {

    data class LineColorData(val id: Int, val color: Int, val timestamp: String)

    interface Listener {
        fun onItemSelected(position: Int, onSelect: (() -> Unit)? = null)
        fun onItemLongClick(position: Int, onSelect: (() -> Unit)? = null)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_graph_line_selector, parent, false)
        return ViewHolder(v as ConstraintLayout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        with(currentList[position]) {
            holder.itemView.tag = this
            //val (day, time) = timestamp.split(" ")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                holder.colorView.tooltipText = timestamp
            }
            holder.colorView.setOnLongClickListener {
                listener?.onItemLongClick(position)
                //Toast.makeText(holder.itemView.context, timestamp, Toast.LENGTH_SHORT).show()
                true
            }
            holder.colorView.setBackgroundColor(color)
            holder.colorView.setOnClickListener {
                listener?.onItemSelected(position)
            }
            holder.textView.text = (position + 1).toString()
        }
    }

    override fun getItemCount(): Int {
        return currentList.size
    }

    class ViewHolder(v: ConstraintLayout) : RecyclerView.ViewHolder(v) {
        var colorView: View = v.findViewById(R.id.line_selector_color)
        var textView: TextView = v.findViewById(R.id.line_selector_text)
    }

    class DiffCallback : DiffUtil.ItemCallback<LineColorData>() {

        override fun areItemsTheSame(oldItem: LineColorData, newItem: LineColorData): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: LineColorData, newItem: LineColorData): Boolean {
            return oldItem.color == newItem.color && oldItem.timestamp == newItem.timestamp
        }
    }
}