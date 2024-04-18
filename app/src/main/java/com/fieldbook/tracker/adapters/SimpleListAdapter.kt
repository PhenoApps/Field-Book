package com.fieldbook.tracker.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.brapi.hackathon.cropontology.tables.BrapiStudy
import com.fieldbook.tracker.databinding.SimpleListItemBinding
import com.fieldbook.tracker.interfaces.OnOntologyVariableClicked

/**
 * Simple class to use with recycler views to submit strings
 * Data supplied should be a list of pairs where the first item is a Long id and the second item is a string value
 * s.a listOf(1L to "Chaney", 2L to "Trevor")
 *
 * Uses an onclick interface to return the pair that was clicked
 */
class SimpleListAdapter(private val context: Context)
    : ListAdapter<BrapiStudy, SimpleListAdapter.ViewHolder>(

    //diffutil is simply string equality of the row id or the content value
    object : DiffUtil.ItemCallback<BrapiStudy>() {
        override fun areItemsTheSame(old: BrapiStudy, new: BrapiStudy) = old.studyDbId == new.studyDbId
        override fun areContentsTheSame(old: BrapiStudy, new: BrapiStudy) = old.studyDbId == new.studyDbId
    }) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        val v = LayoutInflater.from(context)
            .inflate(R.layout.brapi_import_field_list_item, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        with (currentList[position]) {
            holder.id.text = this.studyDbId
            holder.name.text = this.studyName
            holder.location.text = this.locationName
//            holder.desc.text = this.description
        }
    }

    override fun getItemCount() = currentList.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val id = view.findViewById<TextView>(R.id.field_id_tv)
        val name = view.findViewById<TextView>(R.id.field_name_tv)
        val location = view.findViewById<TextView>(R.id.field_location_tv)
//        val desc = view.findViewById<TextView>(R.id.field_description_tv)
    }
}