package com.fieldbook.tracker.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder
import com.fieldbook.tracker.preferences.composables.CollapsibleCategoryHeader
import com.fieldbook.tracker.ui.theme.AppTheme

/**
 * A [PreferenceCategory] whose children can be shown or hidden by tapping the header.
 * Starts collapsed. A chevron icon rotates to indicate the current state.
 */
class CollapsiblePreferenceCategory @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : PreferenceCategory(context, attrs) {

    private var _isExpanded by mutableStateOf(false)

    var isExpanded: Boolean
        get() = _isExpanded
        private set(value) { _isExpanded = value }

    init {
        layoutResource = com.fieldbook.tracker.R.layout.preference_category_collapsible
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val composeView = holder.itemView as? ComposeView ?: return
        composeView.apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
            )
            setContent {
                AppTheme {
                    CollapsibleCategoryHeader(
                        title = title?.toString() ?: "",
                        isExpanded = isExpanded,
                        onClick = {
                            _isExpanded = !_isExpanded
                            applyExpansionState()
                        },
                    )
                }
            }
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
