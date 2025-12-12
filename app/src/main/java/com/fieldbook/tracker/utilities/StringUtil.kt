package com.fieldbook.tracker.utilities

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.graphics.Typeface

object StringUtil {
    fun applyBoldStyleToString(fullText: String, vararg substrings: String): SpannableStringBuilder {
        val spannableString = SpannableStringBuilder(fullText)

        substrings.forEach { substring ->
            var start = spannableString.indexOf(substring)
            while (start >= 0) {
                val end = start + substring.length
                spannableString.setSpan(
                    StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                // Look for the next occurrence
                start = spannableString.indexOf(substring, end)
            }
        }

        return spannableString
    }

    fun String?.escape() = this?.replace("\"", "\"\"")

    fun String.capitalizeFirstLetter() = if (isEmpty()) this else this.lowercase().replaceFirstChar { it.uppercaseChar() }
}