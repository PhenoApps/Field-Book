package com.fieldbook.tracker.adapters

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.charts.HorizontalBarChartHelper
import com.fieldbook.tracker.charts.HistogramChartHelper
import com.fieldbook.tracker.charts.PieChartHelper
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.charts.PieChart
import java.math.BigDecimal

class FieldDetailAdapter(private var items: MutableList<FieldDetailItem>) : RecyclerView.Adapter<FieldDetailAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val traitNameTextView: TextView = view.findViewById(R.id.traitNameTextView)
        val traitCountTextView: TextView = view.findViewById(R.id.traitCountTextView)
        val traitIconImageView: ImageView = view.findViewById(R.id.traitIconImageView)
        val traitCompletenessChart: PieChart = view.findViewById(R.id.traitCompletenessChart)
        val histogram: BarChart = view.findViewById(R.id.histogram)
        val barChart: HorizontalBarChart = view.findViewById(R.id.barChart)
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

        val chartTextSize = getChartTextSize(holder.itemView.context)
        PieChartHelper.setupPieChart(
            holder.itemView.context,
            holder.traitCompletenessChart,
            item.completeness,
            chartTextSize
        )

        val nonChartableFormats = setOf("audio", "gnss", "gopro", "location", "photo", "text", "usb camera")

        holder.collapsibleHeader.setOnClickListener {
            toggleCollapse(holder)
        }

        // Set initial state to expanded
        holder.collapsibleContent.visibility = View.VISIBLE
        holder.expandCollapseIcon.setImageResource(R.drawable.ic_chevron_up)

        // Check for null and filter out NAs and empty strings
        val filteredObservations = item.observations?.filter { it.isNotEmpty() && it != "NA" } ?: emptyList()

        if (filteredObservations == null || filteredObservations.isEmpty()) {
            noChartAvailableMessage(holder, holder.itemView.context.getString(R.string.field_trait_chart_no_data))
            return
        } else if (item.format in nonChartableFormats) {
            noChartAvailableMessage(holder, holder.itemView.context.getString(R.string.field_trait_chart_incompatible_format))
        } else {
            try {
                val numericObservations = filteredObservations.map { BigDecimal(it) }
                if (item.format == "categorical") {
                    throw NumberFormatException("Categorical traits must use bar chart")
                }
                holder.barChart.visibility = View.GONE
                holder.histogram.visibility = View.VISIBLE
                holder.noChartAvailableTextView.visibility = View.GONE
                HistogramChartHelper.setupHistogram(
                    holder.itemView.context,
                    holder.histogram,
                    numericObservations,
                    chartTextSize
                )
            } catch (e: NumberFormatException) {
                holder.barChart.visibility = View.VISIBLE
                holder.histogram.visibility = View.GONE
                holder.noChartAvailableTextView.visibility = View.GONE
                val parsedCategories = parseCategories(item.categories)
                HorizontalBarChartHelper.setupHorizontalBarChart(
                    holder.itemView.context,
                    holder.barChart,
                    filteredObservations,
                    parsedCategories.takeIf { it.isNotEmpty() },
                    chartTextSize
                )
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
        holder.barChart.visibility = View.GONE
        holder.histogram.visibility = View.GONE
        holder.noChartAvailableTextView.visibility = View.VISIBLE
        holder.noChartAvailableTextView.text = message
    }

    private fun parseCategories(categories: String?): List<String> {
        return try {
            if (categories.isNullOrEmpty()) {
                emptyList()
            } else if (categories.startsWith("[")) {
                val parsedCategories = CategoryJsonUtil.decode(categories)
                parsedCategories.map { it.value }
            } else {
                categories.split("/").map { it.trim() }
            }
        } catch (e: Exception) {
            Log.e("FieldDetailAdapter", "Failed to parse categories: $categories", e)
            emptyList()
        }
    }

    private fun getChartTextSize(context: Context): Float {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.fb_subheading_text_size, typedValue, true)
        val textSizePx = context.resources.getDimension(typedValue.resourceId)
        return textSizePx / context.resources.displayMetrics.scaledDensity
    }
}

data class FieldDetailItem(
    val title: String,
    val format: String,
    val categories: String?,
    val subtitle: String,
    val icon: Drawable?,
    val observations: List<String>? = null,
    val completeness: Float = 0.0f
)
