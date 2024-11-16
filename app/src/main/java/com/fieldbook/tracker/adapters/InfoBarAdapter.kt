package com.fieldbook.tracker.adapters

import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.InfoBarModel
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_infobar, parent, false)
        return ViewHolder(v as ConstraintLayout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = currentList[position]

        setViewHolderText(holder, item.prefix, item.value)

        updateWordWrapState(holder, item.isWordWrapped)

        holder.prefixTextView.setOnClickListener {

            (context as InfoBarController).onInfoBarClicked(position)

        }

        holder.valueTextView.setOnLongClickListener {
            item.isWordWrapped = !item.isWordWrapped

            saveWordWrapState(position, item.isWordWrapped)

            updateWordWrapState(holder, item.isWordWrapped)

            showWordWrapToast(item.prefix, item.isWordWrapped)

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


    private fun showWordWrapToast(prefix: String, isWordWrapped: Boolean) {
        val message = if (isWordWrapped) {
            String.format(context.getString(R.string.infobar_word_wrap_enabled), prefix)
        } else {
            String.format(context.getString(R.string.infobar_word_wrap_disabled), prefix)
        }
        Utils.makeToast(context, message)
    }

    private fun setViewHolderText(holder: ViewHolder, label: String?, value: String) {
        holder.prefixTextView.text = "$label: "
        holder.valueTextView.text = value
    }

    class ViewHolder(v: ConstraintLayout) : RecyclerView.ViewHolder(v) {

        var prefixTextView: TextView
        var valueTextView: TextView

        init {
            prefixTextView = v.findViewById(R.id.list_item_infobar_prefix)
            valueTextView = v.findViewById(R.id.list_item_infobar_value)
        }
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
