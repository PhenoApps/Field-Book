package com.fieldbook.tracker.adapters

import android.content.Context
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.viewpager.widget.PagerAdapter
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.views.RepeatedValuesView

/**
 * Used in the repeated values view.
 * This is an adapter class that uses a sparse array to track the observation values.
 */
class RepeatedValuesPagerAdapter(private val mContext: Context) : PagerAdapter() {

    private var items: List<RepeatedValuesView.ObservationModelViewHolder> = listOf()

    private val views = SparseArray<EditText>()

    override fun instantiateItem(collection: ViewGroup, position: Int): Any {
        val inflater = LayoutInflater.from(mContext)
        val layout = inflater.inflate(R.layout.list_item_edit_text_view, collection, false) as ViewGroup
        collection.addView(layout)
        val editText = layout.findViewById<EditText>(R.id.list_item_text_et)
        editText.setText(items[position].model.value)
        editText.tag = position
        
        //disable editing but allow scrolling
        editText.isFocusableInTouchMode = false
        editText.clearFocus()

        // Set the long click listener
        editText.setOnLongClickListener {
            val observationId = items[position].model.internal_id_observation
            (mContext as CollectActivity).showObservationMetadataDialog(observationId)
            true
        }

        editText.setTextColor(items[position].color)
        views.put(position, editText)

        return layout
    }

    override fun getItemPosition(`object`: Any): Int {
        return POSITION_NONE
    }

    override fun destroyItem(collection: ViewGroup, position: Int, view: Any) {
        views.remove(position)
        collection.removeView(view as View)
    }

    override fun getCount(): Int {
        return items.size
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view === `object`
    }

    fun get(position: Int): EditText? = views.get(position)

    fun submitItems(values: List<RepeatedValuesView.ObservationModelViewHolder>) {
        this.items = values
        notifyDataSetChanged()
    }
}