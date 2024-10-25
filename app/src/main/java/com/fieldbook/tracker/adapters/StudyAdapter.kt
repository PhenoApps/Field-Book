package com.fieldbook.tracker.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
class StudyAdapter(private val studyLoader: StudyLoader) :
    ListAdapter<StudyAdapter.Model, StudyAdapter.ViewHolder>(DiffCallback()) {

    interface StudyLoader {
        fun getObservationVariables(id: String, position: Int): HashSet<BrAPIObservationVariable>?
        fun getObservationUnits(id: String, position: Int): HashSet<BrAPIObservationUnit>?
        fun getGermplasm(id: String, position: Int): HashSet<BrAPIGermplasm>?
        fun getLocation(id: String): String
    }

    data class Model(
        val id: String,
        val title: String,
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_study, parent, false)
        return ViewHolder(v as CardView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        with(currentList[position]) {
            holder.titleTextView.text = title
            holder.traitCountChip.text = studyLoader.getObservationVariables(id, position)?.size?.toString() ?: "0"
            holder.unitCountChip.text = studyLoader.getObservationUnits(id, position)?.size?.toString() ?: ""
            holder.locationChip.text = studyLoader.getLocation(id)

            if (holder.traitCountChip.text.isNotBlank()
                && holder.unitCountChip.text.isNotBlank()
                && holder.locationChip.text.isNotBlank()) {
                holder.progressBar.visibility = View.GONE
            }
        }
    }

    override fun getItemCount(): Int {
        return currentList.size
    }

    inner class ViewHolder(v: CardView) : RecyclerView.ViewHolder(v) {
        var titleTextView: TextView = v.findViewById(R.id.list_item_study_title_tv)
        var unitCountChip: Chip = v.findViewById(R.id.list_item_study_units_chip)
        var traitCountChip: Chip = v.findViewById(R.id.list_item_study_traits_chip)
        var locationChip: Chip = v.findViewById(R.id.list_item_study_location_chip)
        var progressBar: ProgressBar = v.findViewById(R.id.list_item_study_pb)
    }

    class DiffCallback : DiffUtil.ItemCallback<Model>() {

        override fun areItemsTheSame(oldItem: Model, newItem: Model): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Model, newItem: Model): Boolean {
            return oldItem.title == newItem.title
        }
    }
}