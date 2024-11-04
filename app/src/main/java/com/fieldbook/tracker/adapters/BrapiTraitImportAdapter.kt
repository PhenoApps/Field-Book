package com.fieldbook.tracker.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.google.android.material.chip.Chip
import org.brapi.v2.model.germ.BrAPIGermplasm
import org.brapi.v2.model.pheno.BrAPIObservationUnit
import org.brapi.v2.model.pheno.BrAPIObservationVariable

/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 */
class BrapiTraitImportAdapter(private val loader: TraitLoader) :
    ListAdapter<CheckboxListAdapter.Model, BrapiTraitImportAdapter.ViewHolder>(CheckboxListAdapter.DiffCallback()) {

    interface TraitLoader {
        fun onItemClicked(id: String)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_brapi_trait_format_selector, parent, false)
        return ViewHolder(v as CardView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        with(currentList[position]) {
            holder.titleView.text = label
            holder.imageView.setImageResource(iconResId ?: R.drawable.ic_trait_text)
            holder.imageView.visibility = View.VISIBLE
            holder.itemView.setOnClickListener {
                loader.onItemClicked(id)
            }
        }
    }

    override fun getItemCount(): Int {
        return currentList.size
    }

    inner class ViewHolder(v: CardView) : RecyclerView.ViewHolder(v) {
        var imageView: ImageView = v.findViewById(R.id.list_item_brapi_trait_format_selector_iv)
        var titleView: TextView = v.findViewById(R.id.list_item_brapi_trait_format_selector_tv)
    }
}