package com.fieldbook.tracker.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R

class DiseaseRatingAdapter(
    private val onSeverityClick: (String) -> Unit
) : RecyclerView.Adapter<DiseaseRatingAdapter.ViewHolder>() {

    private var severities: List<String> = emptyList()

    fun submitList(list: List<String>) {
        severities = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.trait_category_button, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val value = severities[position]
        holder.button.text = value
        holder.button.minWidth = holder.itemView.resources.getDimensionPixelSize(R.dimen.button_width_disease)

        holder.button.setOnClickListener {
            onSeverityClick(value)
        }
    }

    override fun getItemCount(): Int = severities.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val button: Button = itemView.findViewById(R.id.multicatButton)
    }
}
