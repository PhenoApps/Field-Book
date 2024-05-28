package com.fieldbook.tracker.adapters

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R

import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import java.math.BigDecimal
import kotlin.math.ceil

class FieldDetailAdapter(private var items: MutableList<FieldDetailItem>) : RecyclerView.Adapter<FieldDetailAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val traitNameTextView: TextView = view.findViewById(R.id.traitNameTextView)
        val traitCountTextView: TextView = view.findViewById(R.id.traitCountTextView)
        val traitIconImageView: ImageView = view.findViewById(R.id.traitIconImageView)
        val traitCompletenessChart: PieChart = view.findViewById(R.id.traitCompletenessChart)
        val histogramChart: BarChart = view.findViewById(R.id.histogramChart)
        val nonNumericMessageTextView: TextView = view.findViewById(R.id.nonNumericMessageTextView)
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

        // Set up trait completeness PieChart
        setupPieChart(holder, item.completeness)

        // Collapsible card mechanism
        holder.collapsibleHeader.setOnClickListener {
            if (holder.collapsibleContent.visibility == View.GONE) {
                holder.collapsibleContent.visibility = View.VISIBLE
                holder.expandCollapseIcon.setImageResource(R.drawable.ic_chevron_up)
            } else {
                holder.collapsibleContent.visibility = View.GONE
                holder.expandCollapseIcon.setImageResource(R.drawable.ic_chevron_down)
            }
        }

        if (item.observations != null && item.observations!!.isNotEmpty() && item.observations!!.all { it is Number }) {
            val observations = item.observations!!.map { BigDecimal(it.toString()) }
            val minValue = observations.minOrNull() ?: BigDecimal.ZERO
            val maxValue = observations.maxOrNull() ?: BigDecimal.ZERO
            val range = maxValue.subtract(minValue)

            if (range.compareTo(BigDecimal.ZERO) == 0) {
                holder.histogramChart.visibility = View.GONE
                holder.nonNumericMessageTextView.visibility = View.VISIBLE
                holder.nonNumericMessageTextView.text = "All observations are the same; histogram is not available."
            } else {
                holder.histogramChart.visibility = View.VISIBLE
                holder.nonNumericMessageTextView.visibility = View.GONE

                // Determine the bin size and bin count
                val distinctValuesCount = observations.distinct().size
                val binCount = minOf(10, distinctValuesCount) // No more than 10 bins
                val binSize = range.divide(BigDecimal(binCount), BigDecimal.ROUND_UP)

                Log.d("FieldDetailAdapter", "Range: $range, Bin Size: $binSize, Bin Count: $binCount")

                // Bin the data
                val binnedObservations = mutableMapOf<Int, Int>()
                for (observation in observations) {
                    val binIndex = observation.subtract(minValue).divide(binSize, BigDecimal.ROUND_DOWN).toInt()
                    binnedObservations[binIndex] = (binnedObservations[binIndex] ?: 0) + 1
                }

                Log.d("FieldDetailAdapter", "Binned Observations: $binnedObservations")

                val entries = binnedObservations.map { (binIndex, count) ->
                    val binStart = minValue.add(binSize.multiply(BigDecimal(binIndex)))
                    val binCenter = binStart.add(binSize.divide(BigDecimal(2)))
                    BarEntry(binCenter.toFloat(), count.toFloat())
                }

                Log.d("FieldDetailAdapter", "Bar Entries: $entries")

                val dataSet = BarDataSet(entries, "Observations")
                val theme = holder.itemView.context.theme
                val fbColorPrimaryValue = TypedValue()
                theme.resolveAttribute(R.attr.fb_color_primary, fbColorPrimaryValue, true)
                val fbTraitButtonBackgroundTintValue = TypedValue()
                theme.resolveAttribute(R.attr.fb_trait_button_background_tint, fbTraitButtonBackgroundTintValue, true)

                dataSet.color = fbColorPrimaryValue.data // Use fb_color_primary for bar color
                holder.histogramChart.setBackgroundColor(fbTraitButtonBackgroundTintValue.data) // Light gray background
                dataSet.valueTextColor = Color.WHITE // Value text color
                dataSet.setDrawValues(false) // Remove labels on individual bars

                // Add black outline to bars
                dataSet.setDrawIcons(false)
                dataSet.setDrawValues(false)
                dataSet.barBorderColor = Color.BLACK
                dataSet.barBorderWidth = 1f

                val barData = BarData(dataSet)
                barData.barWidth = binSize.toFloat() // Ensure bars fill the bin width
                holder.histogramChart.data = barData

                // Check if bins represent ranges
                val isRangeBins = observations.any { it.remainder(binSize).compareTo(BigDecimal.ZERO) != 0 }
                Log.d("FieldDetailAdapter", "isRangeBins: $isRangeBins")

                // Customize X axis
                val xAxis: XAxis = holder.histogramChart.xAxis
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.setDrawGridLines(false)
                xAxis.setDrawAxisLine(false)
                xAxis.textColor = Color.BLACK
                xAxis.axisMinimum = minValue.toFloat()
                xAxis.axisMaximum = maxValue.toFloat() + binSize.toFloat()

                if (isRangeBins) { // Bins represent ranges of values
                    xAxis.setCenterAxisLabels(false)
                    xAxis.setLabelCount(binCount + 1, true)
                } else { // Bins correspond directly to observation values
                    xAxis.setCenterAxisLabels(true)
                    xAxis.setLabelCount(binCount, false)
                }

                // Customize Y axis
                val leftAxis: YAxis = holder.histogramChart.axisLeft
                leftAxis.setDrawGridLines(false)
                leftAxis.setDrawAxisLine(false)
                leftAxis.textColor = Color.BLACK
                leftAxis.axisMinimum = 0f // Start y-axis from 0
                val maxY = entries.maxOfOrNull { it.y.toInt() } ?: 1
                leftAxis.granularity = ceil(maxY / 6f) // Ensure the axis increments are integers and don't exceed 6 labels
                Log.d("FieldDetailAdapter", "Y axis granularity is: " + leftAxis.granularity)
                leftAxis.axisMaximum = if (maxY < 5) maxY.toFloat() + 1 else leftAxis.granularity * 5
                leftAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }

                holder.histogramChart.axisRight.isEnabled = false

                // Disable zoom, drag behavior, touch interactions, legend and description
                holder.histogramChart.setScaleEnabled(false)
                holder.histogramChart.setDragEnabled(false)
                holder.histogramChart.setHighlightPerTapEnabled(false)

                val legend = holder.histogramChart.legend
                legend.isEnabled = false

                val description = Description()
                description.text = ""
                holder.histogramChart.description = description
                holder.histogramChart.setNoDataText("No data available")
                holder.histogramChart.setNoDataTextColor(Color.BLACK)

                holder.histogramChart.invalidate() // Refresh chart
            }
        } else {
            holder.histogramChart.visibility = View.GONE
            holder.nonNumericMessageTextView.visibility = View.VISIBLE
            holder.nonNumericMessageTextView.text = "Non-numeric data, no histogram available."
        }
    }

    private fun setupPieChart(holder: ViewHolder, completeness: Float) {
        val entries = listOf(
            PieEntry(completeness, ""),
            PieEntry(1 - completeness, "")
        )

        val dataSet = PieDataSet(entries, "")

        // Retrieve colors from the theme
        val theme = holder.itemView.context.theme
        val fbColorPrimaryValue = TypedValue()
        val fbTraitButtonBackgroundTintValue = TypedValue()
        theme.resolveAttribute(R.attr.fb_color_primary, fbColorPrimaryValue, true)
        theme.resolveAttribute(R.attr.fb_trait_button_background_tint, fbTraitButtonBackgroundTintValue, true)

        val primaryColor = fbColorPrimaryValue.data
        val backgroundColor = fbTraitButtonBackgroundTintValue.data

        dataSet.colors = listOf(primaryColor, backgroundColor)
        dataSet.setDrawValues(false)

        val data = PieData(dataSet)
        holder.traitCompletenessChart.data = data
        holder.traitCompletenessChart.description.isEnabled = false
        holder.traitCompletenessChart.isRotationEnabled = false
        holder.traitCompletenessChart.setDrawEntryLabels(false)
        holder.traitCompletenessChart.legend.isEnabled = false

        // Set the center text to show the percentage
        val percentageText = "${(completeness * 100).toInt()}%"
        holder.traitCompletenessChart.setCenterText(percentageText)
        holder.traitCompletenessChart.setCenterTextSize(12f)
        holder.traitCompletenessChart.setCenterTextColor(Color.BLACK)

        holder.traitCompletenessChart.holeRadius = 85f // Make the center hole larger to create a "donut" effect
        holder.traitCompletenessChart.setTransparentCircleAlpha(0) // Remove the transparency
        holder.traitCompletenessChart.setHoleColor(Color.TRANSPARENT)
        holder.traitCompletenessChart.invalidate() // Refresh chart
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
    val format: String,
    val subtitle: String,
    val icon: Drawable?,
    val observations: List<Any>? = null,
    val completeness: Float = 0.0f
)


