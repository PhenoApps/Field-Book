package com.fieldbook.tracker.utilities

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import android.widget.ImageButton
import androidx.core.graphics.Insets
import androidx.core.view.updateLayoutParams
import com.google.android.material.appbar.AppBarLayout

object InsetHandler {

    private val systemBarOrDisplayCutout = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

    /**
     * Standard inset handling for activities with toolbar
     */
    fun setupStandardInsets(rootView: View, toolbar: Toolbar? = null) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(systemBarOrDisplayCutout)

            toolbar?.updatePadding(top = systemBars.top)

            rootView.updatePadding(bottom = systemBars.bottom)

            insets
        }

        ViewCompat.requestApplyInsets(rootView)
    }

    /**
     * For fragments of activities that already handle bottom insets that need top insets only
     * Consumes bottom insets to prevent double spacing
     */
    fun setupFragmentWithTopInsetsOnly(rootView: View, toolbar: Toolbar? = null) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(systemBarOrDisplayCutout)

            toolbar?.updatePadding(top = systemBars.top)

            WindowInsetsCompat.Builder(insets)
                .setInsets(
                    WindowInsetsCompat.Type.systemBars(),
                    Insets.of(systemBars.left, systemBars.top, systemBars.right, 0)
                )
                .build()
        }

        ViewCompat.requestApplyInsets(rootView)
    }

    /**
     * Apply margins to UI elements instead of root
     */
    fun setupCameraInsets(rootView: View, titleView: View? = null, shutterButton: ImageButton? = null) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(systemBarOrDisplayCutout)

            titleView?.let { title ->
                val params = title.layoutParams as ViewGroup.MarginLayoutParams
                params.topMargin = params.topMargin + systemBars.top
                title.layoutParams = params
            }

            shutterButton?.let { button ->
                val params = button.layoutParams as ViewGroup.MarginLayoutParams
                params.bottomMargin = params.bottomMargin + systemBars.bottom
                button.layoutParams = params
            }

            insets
        }

        ViewCompat.requestApplyInsets(rootView)
    }

    /**
     * For image related activities/fragments - apply padding to top and bottom of the rootView
     */
    fun setupCropImageInsets(rootView: View) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(systemBarOrDisplayCutout)

            rootView.updatePadding(bottom = systemBars.bottom, top = systemBars.top)

            insets
        }

        ViewCompat.requestApplyInsets(rootView)
    }

    /**
     * For preference activity - apply insets to toolbar, and top and bottom insets for rootView
     */
    fun setupPreferenceInsets(rootView: View, toolbar: Toolbar?) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(systemBarOrDisplayCutout)

            toolbar?.updatePadding(top = systemBars.top)

            rootView.updatePadding(
                top = systemBars.top,
                bottom = systemBars.bottom
            )

            insets
        }

        ViewCompat.requestApplyInsets(rootView)
    }

    fun setupAboutActivityInsets(rootView: View, appBarLayout: AppBarLayout? = null) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(systemBarOrDisplayCutout)

            appBarLayout?.updatePadding(top = systemBars.top)

            rootView.updatePadding(bottom = systemBars.bottom)

            insets
        }

        ViewCompat.requestApplyInsets(rootView)
    }

    /**
     * Handles insets for a view with top and a bottom toolbar
     * The bottom one should draw behind gesture nav but keep its content centered
     */
    fun setupInsetsWithBottomBar(
        rootView: View,
        topToolbar: Toolbar,
        bottomToolbar: Toolbar,
        bottomContent: View
    ) {
        val tv = TypedValue()
        rootView.context.theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)
        
        val actionBarPx = TypedValue.complexToDimensionPixelSize(
            tv.data,
            rootView.resources.displayMetrics
        )

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemBars = insets.getInsets(systemBarOrDisplayCutout)

            topToolbar.updatePadding(top = systemBars.top)

            // update the bottom toolbar height so it draws under nav/gesture area
            val desiredHeight = actionBarPx + systemBars.bottom
            bottomToolbar.updateLayoutParams {
                height = desiredHeight
            }

            // update bottom toolbar content padding
            bottomContent.updatePadding(bottom = systemBars.bottom)

            insets
        }

        ViewCompat.requestApplyInsets(rootView)
    }
}