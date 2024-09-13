package com.fieldbook.tracker.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R

/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 */
class StudyAdapter :
    ListAdapter<StudyAdapter.Model, StudyAdapter.ViewHolder>(DiffCallback()) {

    data class Model(
        val id: String,
        val title: String,
        val unitCount: Int,
        val traitCount: Int,
        val location: String
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_study, parent, false)
        return ViewHolder(v as CardView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        with(currentList[position]) {
            holder.titleTextView.text = title
            holder.unitCountTextView.text = unitCount.toString()
            holder.traitCountTextView.text = traitCount.toString()
            holder.locationTextView.text = location
        }
    }

    override fun getItemCount(): Int {
        return currentList.size
    }

    inner class ViewHolder(v: CardView) : RecyclerView.ViewHolder(v) {
        var titleTextView: TextView = v.findViewById(R.id.list_item_study_title_tv)
        var unitCountTextView: TextView = v.findViewById(R.id.list_item_study_units_tv)
        var traitCountTextView: TextView = v.findViewById(R.id.list_item_study_traits_tv)
        var locationTextView: TextView = v.findViewById(R.id.list_item_study_location_tv)
    }

    class DiffCallback : DiffUtil.ItemCallback<Model>() {

        override fun areItemsTheSame(oldItem: Model, newItem: Model): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Model, newItem: Model): Boolean {
            return oldItem.title == newItem.title && oldItem.location == newItem.location
                    && oldItem.unitCount == newItem.unitCount && oldItem.traitCount == newItem.traitCount
        }
    }
}