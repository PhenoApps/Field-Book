package com.fieldbook.tracker.adapters

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R

/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 */
class RequiredSetupAdapter :
    ListAdapter<RequiredSetupAdapter.RequiredSetupModel, RequiredSetupAdapter.ViewHolder>(
        DiffCallback()
    ) {

    data class RequiredSetupModel(
        val setupTitle: String,
        val setupSummary: String,
        val setupIcon: Drawable,
        val callback: () -> Unit,
        val isSet: (() -> Boolean),
        val invalidateMessage: String
    )

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val setupItem: LinearLayout = itemView.findViewById(R.id.setup_item)
        val setupTitle: TextView = itemView.findViewById(R.id.setup_title)
        val setupSummary: TextView = itemView.findViewById(R.id.setup_summary)
        val setupIcon: ImageView = itemView.findViewById(R.id.setup_icon)
        val setupStatus: ImageView = itemView.findViewById(R.id.setup_status)
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.`app_intro_required_setup_item.xml`, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val item = currentList[position]
        viewHolder.setupTitle.text = item.setupTitle
        viewHolder.setupSummary.text = item.setupSummary

        viewHolder.setupIcon.setImageDrawable(item.setupIcon)

        // if the set up was previously done
        checkSetupStatus(viewHolder, item)

        viewHolder.setupItem.setOnClickListener {
            item.callback.invoke()
            checkSetupStatus(viewHolder, item)
        }
    }

    private fun checkSetupStatus(viewHolder: ViewHolder, item: RequiredSetupModel) {
        if (item.isSet()) {
            viewHolder.setupStatus.setImageDrawable(ContextCompat.getDrawable(viewHolder.itemView.context, R.drawable.ic_about_up_to_date))
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = currentList.size
//
//    fun isSetUpDone() {
//        for ()
//    }



    class DiffCallback : DiffUtil.ItemCallback<RequiredSetupModel>() {

        override fun areItemsTheSame(
            oldItem: RequiredSetupModel, newItem: RequiredSetupModel
        ): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(
            oldItem: RequiredSetupModel, newItem: RequiredSetupModel
        ): Boolean {
            return oldItem == newItem
        }
    }
}