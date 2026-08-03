package com.fieldbook.tracker.traits

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity

class TreeSummaryTraitLayout : BaseTraitLayout {

    companion object {
        const val type = "tree summary"
    }

    private var summaryText: TextView? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun type(): String = type
    override fun layoutId(): Int = R.layout.trait_tree_summary

    override fun init(act: Activity) {
        summaryText = findTraitView(R.id.tree_summary_value)
    }

    override fun afterLoadExists(act: CollectActivity, value: String?) {
        super.afterLoadExists(act, value)
        summaryText?.text = value ?: ""
        summaryText?.isEnabled = false
    }

    override fun afterLoadNotExists(act: CollectActivity) {
        super.afterLoadNotExists(act)
        summaryText?.text = context.getString(R.string.tree_summary_empty)
        summaryText?.isEnabled = false
    }

    override fun setNaTraitsText() {
        summaryText?.text = "NA"
    }
}
