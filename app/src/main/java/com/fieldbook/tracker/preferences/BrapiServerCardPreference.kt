package com.fieldbook.tracker.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import com.fieldbook.tracker.ui.theme.AppTheme

/**
 * Compatibility shim for existing Field Book references.
 *
 * The BrAPI server card implementation lives in the shared brapi-provider module.
 */
class BrapiServerCardPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : org.phenoapps.brapi.ui.BrapiServerCardPreference(context, attrs) {

    /** Draws the card with the theme the user picked, not the brapi-provider palette. */
    @Composable
    override fun Theme(content: @Composable () -> Unit) {
        AppTheme(content = content)
    }
}
