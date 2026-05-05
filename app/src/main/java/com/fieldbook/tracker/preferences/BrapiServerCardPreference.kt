package com.fieldbook.tracker.preferences

import android.content.Context
import android.util.AttributeSet

/**
 * Compatibility shim for existing Field Book references.
 *
 * The BrAPI server card implementation lives in the shared brapi-provider module.
 */
class BrapiServerCardPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : org.phenoapps.brapi.ui.BrapiServerCardPreference(context, attrs)
