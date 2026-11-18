package com.fieldbook.tracker.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 */
class ObservationMetadataAdapter :
    ListAdapter<ObservationMetadataAdapter.ObservationMetadataListModel, ObservationMetadataAdapter.ViewHolder>(
        DiffCallback()
    ) {

    data class ObservationMetadataListModel(val key: String, val value: String)
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val keyTextView: TextView = view.findViewById(R.id.list_item_observation_metadata_key_tv)
        val valueTextView: TextView = view.findViewById(R.id.list_item_observation_metadata_value_tv)
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.list_item_observation_metadata, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        with(currentList[position]) {
            viewHolder.keyTextView.text = key
            viewHolder.valueTextView.text = formatValueIfJson(value)
        }
    }

    private fun formatValueIfJson(value: String): String {
        val trimmed = value.trim()
        try {
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                val obj = JSONObject(trimmed)
                val sb = StringBuilder()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append("$k: ${obj.get(k)}")
                }
                return sb.toString()
            } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                val arr = JSONArray(trimmed)
                val sb = StringBuilder()
                for (i in 0 until arr.length()) {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append(arr.get(i).toString())
                }
                return sb.toString()
            }
        } catch (e: JSONException) {
            // not valid JSON, fall through to return as-is
        }
        return value
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = currentList.size

    class DiffCallback : DiffUtil.ItemCallback<ObservationMetadataListModel>() {

        override fun areItemsTheSame(
            oldItem: ObservationMetadataListModel, newItem: ObservationMetadataListModel
        ): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(
            oldItem: ObservationMetadataListModel, newItem: ObservationMetadataListModel
        ): Boolean {
            return oldItem == newItem
        }
    }
}