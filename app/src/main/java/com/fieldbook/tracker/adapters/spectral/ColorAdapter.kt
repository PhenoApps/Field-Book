package com.fieldbook.tracker.adapters.spectral

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import androidx.core.graphics.toColorInt
import com.fieldbook.tracker.activities.CollectActivity

/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 */

class ColorAdapter(private val context: Context, private val listener: Listener) : ListAdapter<String, ColorAdapter.ViewHolder>(DiffCallback()) {

    interface Listener {
        fun onColorDeleted(position: Int, onDelete: (() -> Unit)? = null)
        fun onColorLongClicked(position: Int)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_color, parent, false)

        return ViewHolder(v as ConstraintLayout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        with(currentList[position]) {
            holder.itemView.tag = this
            if (this == "-1") {
                holder.hexCodeText.text = (listener as? Context)?.getString(R.string.loading) ?: ""
                holder.progressBar.visibility = View.VISIBLE
                holder.imageView.visibility = View.VISIBLE
                holder.closeButton.visibility = View.GONE
                holder.colorView.setBackgroundColor(Color.BLACK)
                holder.colorView.setOnLongClickListener {
                    false
                }
            } else {
                holder.progressBar.visibility = View.GONE
                holder.imageView.visibility = View.GONE
                holder.closeButton.visibility = View.VISIBLE
                holder.colorView.setBackgroundColor(this.toColorInt())
                holder.hexCodeText.text = this
                holder.closeButton.setOnClickListener {
                    listener.onColorDeleted(position)
                }
                holder.colorView.setOnLongClickListener {
                    listener.onColorLongClicked(position)
                    true
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return currentList.size
    }

    class ViewHolder(v: ConstraintLayout) : RecyclerView.ViewHolder(v) {
        var colorView: View = v.findViewById(R.id.color_preview)
        var hexCodeText: TextView = v.findViewById(R.id.color_hex_text)
        var closeButton: ImageButton = v.findViewById(R.id.close_btn)
        var progressBar: ProgressBar = v.findViewById(R.id.progress_bar)
        var imageView: ImageView = v.findViewById(R.id.image_view)
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