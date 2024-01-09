package com.fieldbook.tracker.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import android.graphics.drawable.Drawable

class FieldDetailAdapter(private val items: List<FieldDetailItem>) : RecyclerView.Adapter<FieldDetailAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val traitNameTextView: TextView = view.findViewById(R.id.traitNameTextView)
        val traitCountTextView: TextView = view.findViewById(R.id.traitCountTextView)
        val traitIconImageView: ImageView = view.findViewById(R.id.traitIconImageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_field_detail_recycler, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.traitNameTextView.text = item.title
        holder.traitCountTextView.text = item.subtitle
        // Set icon for holder.iconImageView as needed
    }

    override fun getItemCount() = items.size
}

data class FieldDetailItem(val title: String, val subtitle: String, val icon: Drawable?)

