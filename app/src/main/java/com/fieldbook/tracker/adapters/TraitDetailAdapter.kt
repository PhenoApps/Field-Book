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
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.charts.HorizontalBarChartHelper
import com.fieldbook.tracker.charts.HistogramChartHelper
import com.fieldbook.tracker.charts.PieChartHelper
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.charts.PieChart
import java.math.BigDecimal

class TraitDetailAdapter(private var items: MutableList<TraitDetailItem>) : RecyclerView.Adapter<TraitDetailAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.titleTextView)
        val subtitleTextView: TextView = view.findViewById(R.id.subtitleTextView)
        val iconImageView: ImageView = view.findViewById(R.id.iconImageView)
        val completenessChart: PieChart = view.findViewById(R.id.completenessChart)
        val histogram: BarChart = view.findViewById(R.id.histogram)
        val barChart: HorizontalBarChart = view.findViewById(R.id.barChart)
        val noChartAvailableTextView: TextView = view.findViewById(R.id.noChartAvailableTextView)
        val collapsibleHeader: LinearLayout = view.findViewById(R.id.collapsible_header)
        val collapsibleContent: LinearLayout = view.findViewById(R.id.collapsible_content)
        val expandCollapseIcon: ImageView = view.findViewById(R.id.expand_collapse_icon)
        
        // Format details views
        val formatDetailsLayout: LinearLayout = view.findViewById(R.id.formatDetailsLayout)
        val defaultValueText: TextView = view.findViewById(R.id.defaultValueText)
        val minValueText: TextView = view.findViewById(R.id.minValueText)
        val maxValueText: TextView = view.findViewById(R.id.maxValueText)
        val categoriesText: TextView = view.findViewById(R.id.categoriesText)
        val detailsText: TextView = view.findViewById(R.id.detailsText)
    }

    interface OnTraitSelectedListener {
        fun onTraitSelected(traitId: String)
    }

    // Add a property to store the listener
    private var onTraitSelectedListener: OnTraitSelectedListener? = null

    // Add a method to set the listener
    fun setOnTraitSelectedListener(listener: OnTraitSelectedListener) {
        onTraitSelectedListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_trait_detail_recycler, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        holder.titleTextView.text = item.title
        holder.subtitleTextView.text = item.subtitle
        holder.iconImageView.setImageDrawable(item.icon)

        holder.itemView.setOnClickListener {
            onTraitSelectedListener?.onTraitSelected(item.id)  // Use item.id instead of traitObject!!.id
        }

        val chartTextSize = getChartTextSize(holder.itemView.context)
        PieChartHelper.setupPieChart(
            holder.itemView.context,
            holder.completenessChart,
            item.completeness,
            chartTextSize
        )
        
        // Set up collapsible section
        holder.collapsibleHeader.setOnClickListener {
            toggleCollapse(holder)
        }

        // Set initial state to expanded
        holder.collapsibleContent.visibility = View.VISIBLE
        holder.expandCollapseIcon.setImageResource(R.drawable.ic_chevron_up)
        
        // Handle format details item
        if (position == 0) { // First item is format details
            setupFormatDetails(holder, item, context)
            return
        }
        
        // Handle observation data item
        setupObservationCharts(holder, item, chartTextSize)

    }
    
    private fun setupFormatDetails(holder: ViewHolder, item: TraitDetailItem, context: Context) {
        holder.formatDetailsLayout.visibility = View.VISIBLE
        holder.histogram.visibility = View.GONE
        holder.barChart.visibility = View.GONE
        holder.noChartAvailableTextView.visibility = View.GONE
        
        // Default value
        if (!item.defaultValue.isNullOrEmpty()) {
            holder.defaultValueText.visibility = View.VISIBLE
            holder.defaultValueText.text = context.getString(R.string.trait_default_value, item.defaultValue)
        } else {
            holder.defaultValueText.visibility = View.GONE
        }
        
        // Min value
        if (!item.minimum.isNullOrEmpty()) {
            holder.minValueText.visibility = View.VISIBLE
            holder.minValueText.text = context.getString(R.string.trait_min_value, item.minimum)
        } else {
            holder.minValueText.visibility = View.GONE
        }
        
        // Max value
        if (!item.maximum.isNullOrEmpty()) {
            holder.maxValueText.visibility = View.VISIBLE
            holder.maxValueText.text = context.getString(R.string.trait_max_value, item.maximum)
        } else {
            holder.maxValueText.visibility = View.GONE
        }
        
        // Categories
        if (!item.categories.isNullOrEmpty()) {
            holder.categoriesText.visibility = View.VISIBLE
            holder.categoriesText.text = context.getString(R.string.trait_categories, item.categories)
        } else {
            holder.categoriesText.visibility = View.GONE
        }
        
        // Details
        if (!item.details.isNullOrEmpty()) {
            holder.detailsText.visibility = View.VISIBLE
            holder.detailsText.text = context.getString(R.string.trait_details, item.details)
        } else {
            holder.detailsText.visibility = View.GONE
        }
    }
    
    // private fun setupObservationCharts(holder: ViewHolder, item: TraitDetailItem, chartTextSize: Float) {
    //     holder.formatDetailsLayout.visibility = View.GONE
        
    //     val nonChartableFormats = setOf("audio", "gnss", "gopro", "location", "photo", "text", "usb camera")

    //     // Check for null and filter out NAs and empty strings
    //     val filteredObservations = item.observations?.filter { it.isNotEmpty() && it != "NA" } ?: emptyList()

    //     if (filteredObservations.isEmpty()) {
    //         noChartAvailableMessage(holder, holder.itemView.context.getString(R.string.field_trait_chart_no_data))
    //         return
    //     } else if (item.format in nonChartableFormats) {
    //         noChartAvailableMessage(holder, holder.itemView.context.getString(R.string.field_trait_chart_incompatible_format))
    //     } else {
    //         try {
    //             val numericObservations = filteredObservations.map { BigDecimal(it) }
    //             if (item.format == "categorical") {
    //                 throw NumberFormatException("Categorical traits must use bar chart")
    //             }
    //             holder.barChart.visibility = View.GONE
    //             holder.histogram.visibility = View.VISIBLE
    //             holder.noChartAvailableTextView.visibility = View.GONE
    //             HistogramChartHelper.setupHistogram(
    //                 holder.itemView.context,
    //                 holder.histogram,
    //                 numericObservations,
    //                 chartTextSize
    //             )
    //         } catch (e: NumberFormatException) {
    //             holder.barChart.visibility = View.VISIBLE
    //             holder.histogram.visibility = View.GONE
    //             holder.noChartAvailableTextView.visibility = View.GONE
    //             val parsedCategories = parseCategories(item.categories)
    //             HorizontalBarChartHelper.setupHorizontalBarChart(
    //                 holder.itemView.context,
    //                 holder.barChart,
    //                 filteredObservations,
    //                 parsedCategories.takeIf { it.isNotEmpty() },
    //                 chartTextSize
    //             )
    //         }
    //     }
    // }

    private fun setupObservationCharts(holder: ViewHolder, item: TraitDetailItem, chartTextSize: Float) {
        holder.formatDetailsLayout.visibility = View.GONE
        
        val nonChartableFormats = setOf("audio", "gnss", "gopro", "location", "photo", "text", "usb camera")

        // Check for null and filter out NAs and empty strings
        val filteredObservations = item.observations?.filter { it.isNotEmpty() && it != "NA" } ?: emptyList()

        if (filteredObservations.isEmpty()) {
            noChartAvailableMessage(holder, holder.itemView.context.getString(R.string.field_trait_chart_no_data))
            return
        } else if (item.format in nonChartableFormats) {
            noChartAvailableMessage(holder, holder.itemView.context.getString(R.string.field_trait_chart_incompatible_format))
        } else {
            try {
                // Try to parse as numeric for histogram
                val numericObservations = filteredObservations.map { BigDecimal(it) }
                if (item.format == "categorical" || item.format == "multicat" || item.format == "boolean") {
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
                // Use bar chart for categorical data
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

    fun updateItems(newItems: List<TraitDetailItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
    
    fun addOrUpdateItem(newItem: TraitDetailItem) {
        // Find if item with same title exists
        val existingIndex = items.indexOfFirst { it.title == newItem.title }
        if (existingIndex >= 0) {
            items[existingIndex] = newItem
            notifyItemChanged(existingIndex)
        } else {
            items.add(newItem)
            notifyItemInserted(items.size - 1)
        }
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

    private fun parseCategories(categories: String): List<String> {
        return try {
            if (categories.startsWith("[")) {
                val parsedCategories = CategoryJsonUtil.decode(categories)
                parsedCategories.map { it.value }
            } else {
                categories.split("/").map { it.trim() }
            }
        } catch (e: Exception) {
            Log.e("TraitDetailAdapter", "Failed to parse categories: $categories", e)
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

// data class TraitDetailItem(
//     val id: String,
//     val title: String,
//     val subtitle: String,
//     val format: String,
//     val categories: String,
//     val icon: Drawable?,
//     val observations: List<String>? = null,
//     val completeness: Float = 0.0f,
//     val defaultValue: String? = null,
//     val minimum: String? = null,
//     val maximum: String? = null,
//     val details: String? = null
// )

data class TraitDetailItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val format: String,
    val categories: String,
    val icon: Drawable?,
    val observations: List<String>? = null,
    val completeness: Float = 0.0f,
    val defaultValue: String? = null,
    val minimum: String? = null,
    val maximum: String? = null,
    val details: String? = null,
    val fieldsCount: Int = 0
)