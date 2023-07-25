package com.fieldbook.tracker.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.InfoBarModel

/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 *
 * Infobar adapter handles data within the infobar recycler view on the collect screen.
 * The infobars are a user preference, and can be set to display any of the plot attributes or traits,
 * each list item has a prefix and value, where the value represents the attr's value for the current plot.
 * e.g:
 * prefix: value
 * col: 1,
 * row: 2,
 * height: 21
 * They are displayed in the top left corner of the collect screen
 */
class InfoBarAdapter(private val context: Context) :
    ListAdapter<InfoBarModel, InfoBarAdapter.ViewHolder>(DiffCallback()) {

    interface InfoBarController {
        fun onInfoBarClicked(position: Int)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_infobar, parent, false)
        return ViewHolder(v as ConstraintLayout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        with (currentList[position]) {
            setViewHolderText(holder, prefix, value)
        }

        holder.prefixTextView.setOnClickListener {

            (context as InfoBarController).onInfoBarClicked(position)

        }

        holder.valueTextView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> holder.valueTextView.maxLines = 5
                MotionEvent.ACTION_UP -> holder.valueTextView.maxLines = 1
            }
            true
        }
    }

    private fun setViewHolderText(holder: ViewHolder, label: String?, value: String) {
        holder.prefixTextView.text = "$label: "
        holder.valueTextView.text = value
    }

    class ViewHolder(v: ConstraintLayout) : RecyclerView.ViewHolder(v) {

        var prefixTextView: TextView
        var valueTextView: TextView

        init {
            prefixTextView = v.findViewById(R.id.list_item_infobar_prefix)
            valueTextView = v.findViewById(R.id.list_item_infobar_value)
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = currentList.size

    class DiffCallback : DiffUtil.ItemCallback<InfoBarModel>() {

        override fun areItemsTheSame(oldItem: InfoBarModel, newItem: InfoBarModel): Boolean {
            return oldItem.prefix == newItem.prefix && oldItem.value == newItem.value
        }

        override fun areContentsTheSame(oldItem: InfoBarModel, newItem: InfoBarModel): Boolean {
            return oldItem.prefix == newItem.prefix && oldItem.value == newItem.value
        }
    }
}
