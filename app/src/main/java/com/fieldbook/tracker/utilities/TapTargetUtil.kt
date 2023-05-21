package com.fieldbook.tracker.utilities

import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import com.fieldbook.tracker.R
import com.getkeepsafe.taptargetview.TapTarget

class TapTargetUtil {

    companion object {

        fun getTapTargetSettingsView(context: Context, view: View, color: Int, targetRadius: Int, title: String, desc: String): TapTarget {
            val value = TypedValue()
            val outerCircleValue = TypedValue()
            context.theme.resolveAttribute(R.attr.fb_color_primary_dark, outerCircleValue, true)
            context.theme.resolveAttribute(R.attr.fb_tap_target_color, value, true)
            return TapTarget.forView(
                view,
                title,
                desc
            ) // All options below are optional
                .outerCircleColor(color) // Specify a color for the outer circle
                .outerCircleAlpha(0.95f) // Specify the alpha amount for the outer circle
                .targetCircleColor(value.data) // Specify a color for the target circle
                .titleTextSize(30) // Specify the size (in sp) of the title text
                .descriptionTextSize(20) // Specify the size (in sp) of the description text
                .descriptionTextColor(value.data) // Specify the color of the description text
                .descriptionTypeface(Typeface.DEFAULT_BOLD)
                .textColor(value.data) // Specify a color for both the title and description text
                .dimColor(value.data) // If set, will dim behind the view with 30% opacity of the given color
                .drawShadow(true) // Whether to draw a drop shadow or not
                .cancelable(false) // Whether tapping outside the outer circle dismisses the view
                .tintTarget(true) // Whether to tint the target view's color
                .transparentTarget(true) // Specify whether the target is transparent (displays the content underneath)
                .targetRadius(targetRadius)
        }

        fun getTapTargetSettingsRect(context: Context, item: Rect, title: String, desc: String): TapTarget {
            val value = TypedValue()
            val outerCircleValue = TypedValue()
            context.theme.resolveAttribute(R.attr.fb_color_primary_dark, outerCircleValue, true)
            context.theme.resolveAttribute(R.attr.fb_tap_target_color, value, true)
            return TapTarget.forBounds(item, title, desc) // All options below are optional
                .outerCircleColor(outerCircleValue.data) // Specify a color for the outer circle
                .outerCircleAlpha(0.95f) // Specify the alpha amount for the outer circle
                .targetCircleColor(value.data) // Specify a color for the target circle
                .titleTextSize(30) // Specify the size (in sp) of the title text
                .descriptionTextSize(20) // Specify the size (in sp) of the description text
                .descriptionTypeface(Typeface.DEFAULT_BOLD)
                .descriptionTextColor(value.data) // Specify the color of the description text
                .textColor(value.data) // Specify a color for both the title and description text
                .dimColor(value.data) // If set, will dim behind the view with 30% opacity of the given color
                .drawShadow(true) // Whether to draw a drop shadow or not
                .cancelable(false) // Whether tapping outside the outer circle dismisses the view
                .tintTarget(true) // Whether to tint the target view's color
                .transparentTarget(true) // Specify whether the target is transparent (displays the content underneath)
                .targetRadius(60)
        }

        fun getTapTargetSettingsView(context: Context, view: View, title: String, desc: String): TapTarget {
            val value = TypedValue()
            val outerCircleValue = TypedValue()
            context.theme.resolveAttribute(R.attr.fb_color_primary_dark, outerCircleValue, true)
            context.theme.resolveAttribute(R.attr.fb_tap_target_color, value, true)
            return TapTarget.forView(
                view,
                title,
                desc
            ) // All options below are optional
                .outerCircleColor(outerCircleValue.data) // Specify a color for the outer circle
                .outerCircleAlpha(0.95f) // Specify the alpha amount for the outer circle
                .targetCircleColor(value.data) // Specify a color for the target circle
                .titleTextSize(30) // Specify the size (in sp) of the title text
                .descriptionTextSize(20) // Specify the size (in sp) of the description text
                .descriptionTextColor(value.data) // Specify the color of the description text
                .descriptionTypeface(Typeface.DEFAULT_BOLD)
                .textColor(value.data) // Specify a color for both the title and description text
                .dimColor(value.data) // If set, will dim behind the view with 30% opacity of the given color
                .drawShadow(true) // Whether to draw a drop shadow or not
                .cancelable(false) // Whether tapping outside the outer circle dismisses the view
                .tintTarget(true) // Whether to tint the target view's color
                .transparentTarget(true) // Specify whether the target is transparent (displays the content underneath)
                .targetRadius(60)
        }
    }
}