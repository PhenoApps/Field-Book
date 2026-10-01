package com.fieldbook.tracker.activities.brapi.io.filter

import android.text.TextWatcher
import android.view.MenuItem
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.annotation.OptIn
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class BrapiSubFilterListActivity<T> : BrapiListFilterActivity<T>() {

    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: android.text.Editable?) {

            searchJob?.cancel()

            searchJob = launch(Dispatchers.IO) {

                val searchModels = cache.copy().filterBySearchText()

                withContext(Dispatchers.Main) {

                    submitAdapterItems(searchModels)
                }
            }
        }
    }

    protected var numFilterBadge: BadgeDrawable? = null

    override fun List<CheckboxListAdapter.Model>.filterExists(): List<CheckboxListAdapter.Model> = this

    override fun setupSearch(models: List<CheckboxListAdapter.Model>) {

        val searchEditText = searchBar.editText

        searchModels.clear()

        searchModels.addAll(models.map { it.label }.distinct())

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            searchModels
        )

        searchEditText.threshold = 1

        searchEditText.setAdapter(adapter)

        searchEditText.onItemClickListener =
            AdapterView.OnItemClickListener { parent, _, position, _ ->
                val selected = parent?.getItemAtPosition(position).toString()
                searchEditText.setText(selected)
            }

        searchEditText.addTextChangedListener(textWatcher)
    }

    /**
     * Shows the number of selected items as a badge on the clear selection item.
     * This runs once per rebound row, so it can be called many times in one frame (e.g. check all).
     * Attaching a badge to a toolbar item is posted until the toolbar is laid out, so a single badge is kept
     * and only its number updated, and detaching is posted to run after any pending attach.
     */
    @OptIn(ExperimentalBadgeUtils::class)
    override fun resetSelectionCountDisplay() {

        val toolbar = findViewById<MaterialToolbar>(R.id.act_list_filter_tb)

        val numSelected = (recyclerView.adapter as CheckboxListAdapter).selected.size

        if (numSelected > 0) {

            selectionMenuItem?.isVisible = true

            val badge = numFilterBadge ?: BadgeDrawable.create(this).apply {
                horizontalOffset = 16
                maxNumber = 9
            }.also {
                numFilterBadge = it
                BadgeUtils.attachBadgeDrawable(it, toolbar, R.id.action_clear_selection)
            }

            badge.number = numSelected
            badge.isVisible = true

        } else {

            val badge = numFilterBadge

            if (badge == null) {
                selectionMenuItem?.isVisible = false
                return
            }

            numFilterBadge = null

            //detach while the clear selection item still exists, then hide it,
            //otherwise its view is reused by another toolbar item with the badge still drawn on it
            toolbar.post {
                BadgeUtils.detachBadgeDrawable(badge, toolbar, R.id.action_clear_selection)
                if (numFilterBadge == null) selectionMenuItem?.isVisible = false
            }
        }
    }
}