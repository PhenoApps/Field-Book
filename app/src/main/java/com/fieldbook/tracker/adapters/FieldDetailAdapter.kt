package com.fieldbook.tracker.adapters

import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.charts.BarChartHelper
import com.fieldbook.tracker.charts.HistogramChartHelper
import com.fieldbook.tracker.charts.PieChartHelper
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import java.math.BigDecimal

class FieldDetailAdapter(private var items: MutableList<FieldDetailItem>) : RecyclerView.Adapter<FieldDetailAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val traitNameTextView: TextView = view.findViewById(R.id.traitNameTextView)
        val traitCountTextView: TextView = view.findViewById(R.id.traitCountTextView)
        val traitIconImageView: ImageView = view.findViewById(R.id.traitIconImageView)
        val traitCompletenessChart: PieChart = view.findViewById(R.id.traitCompletenessChart)
        val countChart: BarChart = view.findViewById(R.id.countChart)
        val noChartAvailableTextView: TextView = view.findViewById(R.id.noChartAvailableTextView)
        val collapsibleHeader: LinearLayout = view.findViewById(R.id.collapsible_header)
        val collapsibleContent: LinearLayout = view.findViewById(R.id.collapsible_content)
        val expandCollapseIcon: ImageView = view.findViewById(R.id.expand_collapse_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_field_detail_recycler, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.traitNameTextView.text = item.title
        holder.traitCountTextView.text = item.subtitle
        holder.traitIconImageView.setImageDrawable(item.icon)
        PieChartHelper.setupPieChart(holder.itemView.context, holder.traitCompletenessChart, item.completeness)

        if (item.observations == null || item.observations.isEmpty()) {
            Log.d("FieldDetailAdapter", "No data to chart for ${item.title}")
            noChartAvailableMessage(holder, "No data to chart.")
            return
        }

        val observations = item.observations
        Log.d("FieldDetailAdapter", "Observations for ${item.title}: $observations")

        holder.collapsibleHeader.setOnClickListener {
            toggleCollapse(holder)
        }

        if (observations.distinct().size == 1) {
            Log.d("FieldDetailAdapter", "All observations have the same value for ${item.title}")
            BarChartHelper.setupBarChart(holder.itemView.context, holder.countChart, observations)
            return
        }

        try {
            val numericObservations = observations.map { BigDecimal(it) }
            Log.d("FieldDetailAdapter", "Numeric observations for ${item.title}: $numericObservations")
            Log.d("FieldDetailAdapter", "Setting up histogram for ${item.title}")
            HistogramChartHelper.setupHistogram(holder.itemView.context, holder.countChart, numericObservations)
        } catch (e: NumberFormatException) {
            Log.d("FieldDetailAdapter", "Non-numeric data detected for ${item.title}")
            if (observations.distinct().size <= 10) {
                Log.d("FieldDetailAdapter", "Setting up bar chart for ${item.title}")
                BarChartHelper.setupBarChart(holder.itemView.context, holder.countChart, observations)
            } else {
                Log.d("FieldDetailAdapter", "This data type cannot be charted for ${item.title}")
                noChartAvailableMessage(holder, "This data type cannot be charted.")
            }
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<FieldDetailItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    private fun toggleCollapse(holder: ViewHolder) {
        if (holder.collapsibleContent.visibility == View.GONE) {
            holder.collapsibleContent.visibility = View.VISIBLE
            holder.expandCollapseIcon.setImageResource(R.drawable.ic_chevron_up)
        } else {
            holder.collapsibleContent.visibility = View.GONE
            holder.expandCollapseIcon.setImageResource(R.drawable.ic_chevron_down)
        }
    }

    private fun noChartAvailableMessage(holder: ViewHolder, message: String) {
        holder.countChart.visibility = View.GONE
        holder.collapsibleContent.visibility = View.GONE
        holder.noChartAvailableTextView.visibility = View.VISIBLE
        holder.noChartAvailableTextView.text = message
    }
}

data class FieldDetailItem(
    val title: String,
    val format: String,
    val subtitle: String,
    val icon: Drawable?,
    val observations: List<String>? = null,
    val completeness: Float = 0.0f
)
