package com.fieldbook.tracker.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.dialogs.ExportStatusItem

class ExportStatusAdapter : ListAdapter<ExportStatusItem, ExportStatusAdapter.ViewHolder>(ExportStatusDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_brapi_export_status, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val statusIcon: ImageView = itemView.findViewById(R.id.status_icon)
        private val statusText: TextView = itemView.findViewById(R.id.status_text)
        private val statusCount: TextView = itemView.findViewById(R.id.status_count)

        fun bind(item: ExportStatusItem) {
            statusText.text = item.label
            statusCount.text = "${item.completedCount}/${item.totalCount}"
            
            if (item.isComplete && item.totalCount > 0) {
                statusIcon.visibility = View.VISIBLE
                statusIcon.setImageResource(R.drawable.ic_about_up_to_date)
            } else {
                statusIcon.visibility = View.INVISIBLE
            }
        }
    }

    class ExportStatusDiffCallback : DiffUtil.ItemCallback<ExportStatusItem>() {
        override fun areItemsTheSame(oldItem: ExportStatusItem, newItem: ExportStatusItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ExportStatusItem, newItem: ExportStatusItem): Boolean {
            return oldItem == newItem
        }
    }
}