package com.fieldbook.tracker.adapters

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R

import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry

class FieldDetailAdapter(private var items: MutableList<FieldDetailItem>) : RecyclerView.Adapter<FieldDetailAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val traitNameTextView: TextView = view.findViewById(R.id.traitNameTextView)
        val traitCountTextView: TextView = view.findViewById(R.id.traitCountTextView)
        val traitIconImageView: ImageView = view.findViewById(R.id.traitIconImageView)
        val histogramChart: BarChart = view.findViewById(R.id.histogramChart)
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

        if (item.observations != null && item.observations!!.isNotEmpty() && item.observations!!.all { it is Number }) {
            holder.histogramChart.visibility = View.VISIBLE
            val entries = item.observations!!.mapIndexed { index, value ->
                BarEntry(index.toFloat(), (value as Number).toFloat())
            }
            val dataSet = BarDataSet(entries, "Observations")
            dataSet.color = ContextCompat.getColor(holder.itemView.context, R.color.main_primary) // Use main_primary for bar color
            dataSet.valueTextColor = Color.WHITE // Value text color

            val barData = BarData(dataSet)
            holder.histogramChart.data = barData

            // Customize X axis
            val xAxis: XAxis = holder.histogramChart.xAxis
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.setDrawAxisLine(false)
            xAxis.textColor = Color.BLACK

            // Customize Y axis
            val leftAxis: YAxis = holder.histogramChart.axisLeft
            leftAxis.setDrawGridLines(false)
            leftAxis.setDrawAxisLine(false)
            leftAxis.textColor = Color.BLACK
            holder.histogramChart.axisRight.isEnabled = false

            // Customize legend
            val legend = holder.histogramChart.legend
            legend.isEnabled = false

            // Customize description
            val description = Description()
            description.text = ""
            holder.histogramChart.description = description

            holder.histogramChart.setNoDataText("No data available")
            holder.histogramChart.setNoDataTextColor(Color.BLACK)
            holder.histogramChart.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.light_gray)) // Light gray background

            holder.histogramChart.invalidate() // Refresh chart
        } else {
            holder.histogramChart.visibility = View.GONE
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<FieldDetailItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}

data class FieldDetailItem(
    val title: String,
    val subtitle: String,
    val icon: Drawable?,
    val observations: List<Any>? = null // Add observations list
)


