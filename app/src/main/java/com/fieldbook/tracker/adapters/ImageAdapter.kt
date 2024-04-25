package com.fieldbook.tracker.adapters

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.phenoapps.androidlibrary.R


/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 */
class ImageAdapter(private val listener: ImageItemHandler) :
        ListAdapter<ImageAdapter.Model, ImageAdapter.ViewHolder>(DiffCallback()) {

    data class Model(var uri: String, var bmp: Bitmap, var brapiSynced: Boolean)

    interface ImageItemHandler {

        fun onItemClicked(model: Model)

        fun onItemDeleted(model: Model)

    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.list_item_image_iv)
        private val closeButton: ImageButton = view.findViewById(R.id.list_item_image_close_btn)
        init {
            // Define click listener for the ViewHolder's View.
            view.setOnClickListener {
                listener.onItemClicked(view.tag as Model)
            }

            closeButton.setOnClickListener {
                listener.onItemDeleted(view.tag as Model)
            }
        }
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.list_item_image, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        with(currentList[position]) {
            viewHolder.itemView.tag = this
            viewHolder.imageView.setImageBitmap(bmp)
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = currentList.size

    class DiffCallback : DiffUtil.ItemCallback<Model>() {

        override fun areItemsTheSame(oldItem: Model, newItem: Model): Boolean {
            return oldItem == newItem && oldItem == oldItem
        }

        override fun areContentsTheSame(oldItem: Model, newItem: Model): Boolean {
            return oldItem == newItem && oldItem == oldItem
        }
    }
}