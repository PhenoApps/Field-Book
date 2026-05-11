package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.fieldbook.tracker.R

class RequiredSetupItem @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val iconView: ImageView
    private val titleView: TextView
    private val summaryView: TextView
    private val statusView: ImageView

    init {
        orientation = HORIZONTAL
        val view = LayoutInflater.from(context).inflate(R.layout.app_intro_required_setup_item, this, true)

        iconView = view.findViewById(R.id.setup_icon)
        titleView = view.findViewById(R.id.setup_title)
        summaryView = view.findViewById(R.id.setup_summary)
        statusView = view.findViewById(R.id.setup_status)
    }

    fun setIcon(resourceId: Int) {
        iconView.setImageResource(resourceId)
    }

    fun setTitle(title: String) {
        titleView.text = title
    }

    fun setSummary(summary: String) {
        summaryView.text = summary
    }

    fun setStatus(resourceId: Int) {
        statusView.setImageResource(resourceId)
    }
}