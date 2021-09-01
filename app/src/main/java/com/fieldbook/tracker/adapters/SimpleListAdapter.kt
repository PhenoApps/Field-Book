package com.fieldbook.tracker.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.cropontology.tables.Variable
import com.fieldbook.tracker.databinding.SimpleListItemBinding
import com.fieldbook.tracker.interfaces.OnOntologyVariableClicked

/**
 * Simple class to use with recycler views to submit strings
 * Data supplied should be a list of pairs where the first item is a Long id and the second item is a string value
 * s.a listOf(1L to "Chaney", 2L to "Trevor")
 *
 * Uses an onclick interface to return the pair that was clicked
 */
class SimpleListAdapter(private val listener: OnOntologyVariableClicked)
    : ListAdapter<Variable, SimpleListAdapter.ViewHolder>(

    //diffutil is simply string equality of the row id or the content value
    object : DiffUtil.ItemCallback<Variable>() {
        override fun areItemsTheSame(old: Variable, new: Variable) = old.ontologyDbId == new.ontologyDbId
        override fun areContentsTheSame(old: Variable, new: Variable) = old.ontologyDbId == new.ontologyDbId
    }) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        return ViewHolder(

            DataBindingUtil.inflate(
                    LayoutInflater.from(parent.context),
                    R.layout.simple_list_item, parent, false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        getItem(position).let { item ->

            with(holder) {

                bind(item)
            }
        }
    }

    inner class ViewHolder(private val binding: SimpleListItemBinding)
        : RecyclerView.ViewHolder(binding.root) {

        fun bind(v: Variable) {

            binding.name = v.name

            binding.description = v.toString()

            with(binding) {
                listItemSimpleNameTv.setOnClickListener {
                    listItemSimpleDescriptionTv.visibility =
                        if(listItemSimpleDescriptionTv.visibility == View.VISIBLE) View.GONE
                        else View.VISIBLE

                    this@SimpleListAdapter.listener.onItemClicked(v)
                }
            }
        }
    }
}