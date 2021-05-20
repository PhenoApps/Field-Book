package com.fieldbook.tracker.adapters

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.DataGridActivity
import com.fieldbook.tracker.databinding.ListItemHeaderBinding

typealias Data = DataGridActivity.BlockData
typealias Header = DataGridActivity.HeaderData
typealias Cell = DataGridActivity.CellData
typealias Empty = DataGridActivity.EmptyCell

class HeaderAdapter(val context: Context) : ListAdapter<Data, HeaderAdapter.ViewHolder>(HeaderDiffCallback()) {

    private class HeaderDiffCallback : DiffUtil.ItemCallback<Data>() {

        override fun areItemsTheSame(oldItem: Data, newItem: Data): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Data, newItem: Data): Boolean {

            return oldItem.hashCode() == newItem.hashCode()
        }
    }

    override fun onCreateViewHolder(vg: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
                DataBindingUtil.inflate(
                        LayoutInflater.from(vg.context),
                        R.layout.list_item_header, vg, false
                )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        getItem(position).let { data ->

            with(holder) {

                val nameText = itemView.findViewById<TextView>(R.id.nameTextView)
                val progressBar = itemView.findViewById<ProgressBar>(R.id.progressBar)
                val emptyView = itemView.findViewById<View>(R.id.emptyView)

                when (data) {

                    is DataGridActivity.CellData -> {
                        progressBar.visibility = View.GONE
                        nameText.visibility = View.VISIBLE
                        emptyView.visibility = View.GONE
                    }

                    is DataGridActivity.HeaderData -> {
                        progressBar.visibility = View.GONE
                        emptyView.visibility = View.GONE
                        nameText.visibility = View.VISIBLE
                    }

                    is DataGridActivity.EmptyCell -> {
                        progressBar.visibility = View.GONE
                        nameText.visibility = View.GONE
                        emptyView.visibility = View.VISIBLE

                    }
                }

                bind(data)
            }
        }
    }

    inner class ViewHolder(
            private val binding: ListItemHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(data: Data) {

            with(binding) {

                when (data) {

                    is DataGridActivity.HeaderData -> {

                        name = data.name

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            binding.nameTextView.tooltipText = data.name
                        }

                    }
                    is DataGridActivity.CellData -> {

                        name = data.value

//                        progressBar.visibility = View.VISIBLE
//
//                        nameTextView.visibility = View.VISIBLE
//
//                        emptyView.visibility = View.GONE
//
//                        nameTextView.text = data.value
//                        current = data.current
//
//                        goal = data.max

//                        val pd = progressBar.progressDrawable.mutate()
//
//                        pd.setColorFilter(when {
//
//                            data.current >= data.max -> Color.RED
//
//                            data.current >= data.min -> Color.GREEN
//
//                            data.current < data.min && data.current > 0 -> Color.YELLOW
//
//                            else -> Color.GRAY
//
//                        }, PorterDuff.Mode.SRC_IN)
//
//                        progressBar.progressDrawable = pd
//
////                        progressBar.indeterminateTintList = ColorStateList.valueOf(
////
////                            when {
////
////                                data.current >= data.max -> Color.RED
////
////                                data.current >= data.min -> Color.GREEN
////
////                                else -> Color.YELLOW
////                            })

//                        progressBar.progressDrawable.setColorFilter(when {
//
//                            data.current >= data.max -> Color.RED
//
//                            data.current >= data.min -> Color.GREEN
//
//                            data.current < data.min && data.current > 0 -> Color.YELLOW
//
//                            else -> Color.GRAY
//
//                        }, PorterDuff.Mode.SRC_IN)


                        onClick = data.onClick

                    }
                }
            }
        }
    }
}

