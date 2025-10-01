package com.fieldbook.tracker.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R

/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 *
 * A generic list adapter implementation for a list of items with checkboxes.
 * Each supplied item must have a unique key, and a displayable label.
 */

class CheckboxListAdapter(
    private val listener: Listener,
) :
    ListAdapter<CheckboxListAdapter.Model, CheckboxListAdapter.ViewHolder>(DiffCallback()) {

    fun interface Listener {
        fun onCheckChanged(checked: Boolean, position: Int)
    }

    data class Model(
        var checked: Boolean,
        val id: String,
        val label: String,
        val subLabel: String,
        var iconResId: Int? = null,
        var searchableTexts: List<String> = emptyList()
    ) {
        override fun equals(other: Any?): Boolean {
            return id == (other as? Model)?.id
        }

        override fun hashCode(): Int {
            var result = checked.hashCode()
            result = 31 * result + id.hashCode()
            result = 31 * result + label.hashCode()
            result = 31 * result + subLabel.hashCode()
            result = 31 * result + (iconResId ?: 0)
            return result
        }
    }

    var selected = mutableListOf<Model>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_label_checkbox, parent, false)
        return ViewHolder(v as CardView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        with(currentList[position]) {

            holder.itemView.tag = this

            holder.textView.text = label

            holder.subTitleView.text = subLabel

            holder.checkBox.isChecked = checked

            holder.imageView.visibility = View.GONE

            this.iconResId?.let { resId ->
                holder.imageView.visibility = View.VISIBLE
                holder.imageView.setImageResource(resId)
            }
        }
    }

    override fun getItemCount(): Int {
        return currentList.size
    }

    inner class ViewHolder(v: CardView) : RecyclerView.ViewHolder(v) {

        var imageView: ImageView = v.findViewById(R.id.list_item_brapi_filter_iv)
        var textView: TextView = v.findViewById(R.id.list_item_brapi_filter_tv)
        var checkBox: CheckBox = v.findViewById(R.id.list_item_brapi_filter_cb)
        var subTitleView: TextView = v.findViewById(R.id.list_item_brapi_filter_subtitle_tv)
        var card: CardView = v.findViewById(R.id.list_item_brapi_filter_cv)

        init {

            //get model from tag and call listener when checkbox is clicked
            checkBox.setOnCheckedChangeListener { _, isChecked ->

                val model = itemView.tag as Model

                model.checked = isChecked

                if (isChecked) {
                    if (model !in selected) {
                        selected.add(model)
                    }
                } else {
                    selected.remove(model)
                }

                listener.onCheckChanged(isChecked, bindingAdapterPosition)

            }

            card.setOnClickListener {
                checkBox.toggle()
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Model>() {

        override fun areItemsTheSame(oldItem: Model, newItem: Model): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Model, newItem: Model): Boolean {
            return oldItem.checked == newItem.checked && oldItem.iconResId == newItem.iconResId
        }
    }
}