package com.fieldbook.tracker.adapters

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.google.android.material.chip.Chip

/**
 * One card per study being imported, listing the study's importable observation levels as checkboxes.
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 */
class StudyAdapter(private val studyLoader: StudyLoader) :
    ListAdapter<StudyAdapter.Model, StudyAdapter.ViewHolder>(DiffCallback()) {

    interface StudyLoader {
        fun isLoading(id: String): Boolean
        fun getLevels(id: String): List<Level>
        fun onLevelChecked(id: String, levelName: String, checked: Boolean)
        fun getLocation(id: String): String
        fun getTrialName(id: String): String
    }

    data class Model(
        val id: String,
        val title: String,
    )

    data class Level(
        val name: String,
        val unitCount: Int,
        val traitCount: Int,
        val checked: Boolean,
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_study, parent, false)
        return ViewHolder(v as CardView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        with(currentList[position]) {

            holder.titleTextView.text = title

            holder.locationChip.text = studyLoader.getLocation(id)
            holder.trialChip.text = studyLoader.getTrialName(id)
            holder.locationChip.visibility = if (holder.locationChip.text.isNotBlank()) View.VISIBLE else View.GONE
            holder.trialChip.visibility = if (holder.trialChip.text.isNotBlank()) View.VISIBLE else View.GONE

            val loading = studyLoader.isLoading(id)
            holder.progressBar.visibility = if (loading) View.VISIBLE else View.GONE

            bindLevels(holder.levelsLayout, id, if (loading) emptyList() else studyLoader.getLevels(id))
        }
    }

    private fun bindLevels(layout: LinearLayout, id: String, levels: List<Level>) {

        layout.removeAllViews()

        val inflater = LayoutInflater.from(layout.context)

        levels.forEach { level ->

            val pill = inflater.inflate(R.layout.list_item_study_level, layout, false)

            pill.findViewById<Chip>(R.id.list_item_study_level_chip).text = level.name
            pill.findViewById<Chip>(R.id.list_item_study_level_units_chip).text = level.unitCount.toString()
            pill.findViewById<Chip>(R.id.list_item_study_level_traits_chip).text = level.traitCount.toString()

            pill.accessibilityDelegate = pillAccessibilityDelegate

            setPillSelected(pill, level.checked)

            pill.setOnClickListener {
                val selected = !pill.isSelected
                setPillSelected(pill, selected)
                studyLoader.onLevelChecked(id, level.name, selected)
            }

            layout.addView(pill)
        }

        layout.visibility = if (levels.isEmpty()) View.GONE else View.VISIBLE
    }

    /**
     * Selected pills use the highlighted background, unselected ones are grayed out.
     */
    private fun setPillSelected(pill: View, selected: Boolean) {
        pill.isSelected = selected
        pill.alpha = if (selected) 1f else unselectedAlpha(pill)
    }

    /**
     * Themes can opt out of fading unselected pills, the high contrast theme does.
     */
    private fun unselectedAlpha(view: View): Float {
        val value = TypedValue()
        return if (view.context.theme.resolveAttribute(R.attr.fb_unselected_pill_alpha, value, true)
            && value.type == TypedValue.TYPE_FLOAT
        ) value.float else DEFAULT_UNSELECTED_ALPHA
    }

    //announces a pill as a checkbox so screen readers read its selected state
    private val pillAccessibilityDelegate = object : View.AccessibilityDelegate() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.className = CheckBox::class.java.name
            info.isCheckable = true
            info.isChecked = host.isSelected
        }
    }

    companion object {
        private const val DEFAULT_UNSELECTED_ALPHA = 0.5f
    }

    override fun getItemCount(): Int {
        return currentList.size
    }

    inner class ViewHolder(v: CardView) : RecyclerView.ViewHolder(v) {
        var titleTextView: TextView = v.findViewById(R.id.list_item_study_title_tv)
        var locationChip: Chip = v.findViewById(R.id.list_item_study_location_chip)
        var progressBar: ProgressBar = v.findViewById(R.id.list_item_study_pb)
        var trialChip: Chip = v.findViewById(R.id.list_item_trial_chip)
        var levelsLayout: LinearLayout = v.findViewById(R.id.list_item_study_levels_ll)
    }

    class DiffCallback : DiffUtil.ItemCallback<Model>() {

        override fun areItemsTheSame(oldItem: Model, newItem: Model): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Model, newItem: Model): Boolean {
            return oldItem.title == newItem.title
        }
    }
}
