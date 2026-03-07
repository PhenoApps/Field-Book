package com.fieldbook.tracker.preferences

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder
import com.fieldbook.tracker.R

/**
 * A [PreferenceCategory] whose children can be shown or hidden by tapping the header.
 * Starts collapsed. A chevron icon rotates to indicate the current state.
 */
class CollapsiblePreferenceCategory @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : PreferenceCategory(context, attrs) {

    var isExpanded: Boolean = false
        private set

    init {
        layoutResource = R.layout.preference_category_collapsible
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val chevron = holder.itemView.findViewById<ImageView>(R.id.category_chevron)
        chevron?.rotation = if (isExpanded) 180f else 0f

        holder.itemView.isClickable = true
        holder.itemView.isFocusable = true
        holder.itemView.setOnClickListener {
            isExpanded = !isExpanded
            val targetRotation = if (isExpanded) 180f else 0f
            ViewCompat.animate(chevron!!).rotation(targetRotation).setDuration(200).start()
            applyExpansionState()
        }
    }

    /** Sets each child's visibility to match [isExpanded]. */
    fun applyExpansionState() {
        for (i in 0 until preferenceCount) {
            getPreference(i).isVisible = isExpanded
        }
    }

    /** Ensures newly-added children start with the correct visibility. */
    override fun addPreference(preference: Preference): Boolean {
        preference.isVisible = isExpanded
        return super.addPreference(preference)
    }
}
