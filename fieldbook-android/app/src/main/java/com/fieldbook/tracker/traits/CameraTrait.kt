package com.fieldbook.tracker.traits

import android.content.Context
import android.util.AttributeSet
import android.widget.Toast
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.ImageAdapter
import dagger.hilt.android.AndroidEntryPoint
import java.lang.RuntimeException

@AndroidEntryPoint
open class CameraTrait : AbstractCameraTrait {

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun showSettings() {
        Toast.makeText(context, R.string.trait_photos_no_settings, Toast.LENGTH_SHORT).show()
    }

    override fun onSettingsChanged() {}

    override fun type(): String {
        throw RuntimeException()
    }

    fun getPreviewViewHolder(): ImageAdapter.PreviewViewHolder? {

        var returnHolder: ImageAdapter.PreviewViewHolder? = null

        (recyclerView?.adapter as? ImageAdapter)?.currentList?.let { list ->

            returnHolder =
                recyclerView?.findViewHolderForAdapterPosition(list.size - 1) as? ImageAdapter.PreviewViewHolder

        }

        return returnHolder
    }
}

