package com.fieldbook.tracker.adapters

import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.InfoBarModel
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.utilities.Utils

/**
 * Reference:
 * https://developer.android.com/guide/topics/ui/layout/recyclerview
 *
 * Infobar adapter handles data within the infobar recycler view on the collect screen.
 * The infobars are a user preference, and can be set to display any of the plot attributes or traits,
 * each list item has a prefix and value, where the value represents the attr's value for the current plot.
 * e.g:
 * prefix: value
 * col: 1,
 * row: 2,
 * height: 21
 * They are displayed in the top left corner of the collect screen
 */
class InfoBarAdapter(private val context: Context) :
    ListAdapter<InfoBarModel, InfoBarAdapter.ViewHolder>(DiffCallback()) {

    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    interface InfoBarController {
        fun onInfoBarClicked(position: Int)
    }

    private var hidePrefixEnabled: Boolean = false

    private val wordWrapEnabledIconResources = intArrayOf(
        R.drawable.ic_infobar_rhombus,
        R.drawable.ic_infobar_circle,
        R.drawable.ic_infobar_triangle,
        R.drawable.ic_infobar_square_rounded,
        R.drawable.ic_infobar_pentagon,
        R.drawable.ic_infobar_hexagon
    )

    private val wordWrapDisabledIconResources = intArrayOf(
        R.drawable.ic_infobar_rhombus_outline,
        R.drawable.ic_infobar_circle_outline,
        R.drawable.ic_infobar_triangle_outline,
        R.drawable.ic_infobar_square_rounded_outline,
        R.drawable.ic_infobar_pentagon_outline,
        R.drawable.ic_infobar_hexagon_outline
    )

    init {
        hidePrefixEnabled = preferences.getBoolean(PreferenceKeys.HIDE_INFOBAR_PREFIX, false)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_infobar, parent, false)
        return ViewHolder(v as ConstraintLayout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = currentList[position]

        if (hidePrefixEnabled) { // set infobar to hide prefix using icon and display value
            holder.prefixTextView.visibility = View.GONE
            holder.iconView.visibility = View.VISIBLE

            setIcon(holder, position, item.isWordWrapped)

            holder.valueTextView.text = item.value
        } else { // set infobar to prefix: value
            holder.prefixTextView.visibility = View.VISIBLE
            holder.iconView.visibility = View.GONE

            setViewHolderText(holder, item.prefix, item.value)
        }

        // initial check of isWordWrapped
        updateWordWrapState(holder, item.isWordWrapped)

        // change infobar prefix when clicked
        val clickableView = if (hidePrefixEnabled) holder.valueTextView else holder.prefixTextView
        clickableView.setOnClickListener {

            (context as InfoBarController).onInfoBarClicked(position)

        }

        // enable/disable word wrap when value is long clicked
        holder.valueTextView.setOnLongClickListener {
            item.isWordWrapped = !item.isWordWrapped

            saveWordWrapState(position, item.isWordWrapped)

            setIcon(holder, position, item.isWordWrapped)

            updateWordWrapState(holder, item.isWordWrapped)

            showWordWrapToast((position + 1).toString(), item.isWordWrapped)

            true
        }
    }

    private fun saveWordWrapState(position: Int, isWordWrapped: Boolean) {
        preferences.edit().putBoolean("INFOBAR_WORD_WRAP_$position", isWordWrapped).apply()
    }

    private fun updateWordWrapState(holder: ViewHolder, isWordWrapped: Boolean) {
        holder.valueTextView.apply {
            if (isWordWrapped) {
                maxLines = 5
                ellipsize = null
            } else {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
        }
    }


    private fun showWordWrapToast(position: String, isWordWrapped: Boolean) {
        val message = if (isWordWrapped) {
            String.format(context.getString(R.string.infobar_word_wrap_enabled), position)
        } else {
            String.format(context.getString(R.string.infobar_word_wrap_disabled), position)
        }
        Utils.makeToast(context, message)
    }

    // if wordWrap is disabled, use outline icon
    // else use filled icon
    private fun setIcon(holder: ViewHolder, position: Int, isWordWrapped: Boolean) {
        val iconIndex = position % wordWrapEnabledIconResources.size
        holder.iconView.setImageResource(if (isWordWrapped) wordWrapEnabledIconResources[iconIndex] else wordWrapDisabledIconResources[iconIndex])
    }

    private fun setViewHolderText(holder: ViewHolder, label: String?, value: String) {
        holder.prefixTextView.text = "$label: "
        holder.valueTextView.text = value
    }

    class ViewHolder(v: ConstraintLayout) : RecyclerView.ViewHolder(v) {
        val prefixTextView: TextView = v.findViewById(R.id.list_item_infobar_prefix)
        val valueTextView: TextView = v.findViewById(R.id.list_item_infobar_value)
        val iconView: ImageView = v.findViewById(R.id.list_item_infobar_icon)
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = currentList.size

    class DiffCallback : DiffUtil.ItemCallback<InfoBarModel>() {

        override fun areItemsTheSame(oldItem: InfoBarModel, newItem: InfoBarModel): Boolean {
            return oldItem.prefix == newItem.prefix && oldItem.value == newItem.value
        }

        override fun areContentsTheSame(oldItem: InfoBarModel, newItem: InfoBarModel): Boolean {
            return oldItem.prefix == newItem.prefix && oldItem.value == newItem.value
        }
    }
}
