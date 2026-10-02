package com.fieldbook.tracker.activities.brapi.io.filter

import android.text.TextWatcher
import android.view.MenuItem
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.annotation.OptIn
import androidx.appcompat.widget.ActionMenuView
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

    private val toolbar by lazy { findViewById<MaterialToolbar>(R.id.act_list_filter_tb) }

    //coalesces the many calls made while rows are rebound (e.g. select all) into one update
    private val selectionBadgeUpdate = Runnable { updateSelectionBadge() }

    /**
     * Shows the number of selected items as a badge on the clear selection item, on the next frame.
     */
    override fun resetSelectionCountDisplay() {
        toolbar.removeCallbacks(selectionBadgeUpdate)
        toolbar.post(selectionBadgeUpdate)
    }

    /**
     * Toolbar item views are reused for other items whenever an item is shown or hidden
     * (clear selection, clear filters), and a badge stays drawn on the view it was attached to.
     * Detaching by item id can't reach a badge once its view belongs to another item,
     * so badges are cleared from every toolbar item view and a new one is attached to wherever
     * the clear selection item is now. Attaching is posted by BadgeUtils, and since updates are also posted
     * the next update always runs after it.
     */
    @OptIn(ExperimentalBadgeUtils::class)
    private fun updateSelectionBadge() {

        val numSelected = (recyclerView.adapter as? CheckboxListAdapter)?.selected?.size ?: 0

        clearToolbarBadges()
        numFilterBadge = null

        selectionMenuItem?.isVisible = numSelected > 0

        if (numSelected > 0) {
            numFilterBadge = BadgeDrawable.create(this).apply {
                horizontalOffset = 16
                maxNumber = 9
                number = numSelected
            }.also {
                BadgeUtils.attachBadgeDrawable(it, toolbar, R.id.action_clear_selection)
            }
        }
    }

    //badges are drawn on the view's overlay, which toolbar item views don't otherwise use
    private fun clearToolbarBadges() {
        for (i in 0 until toolbar.childCount) {
            (toolbar.getChildAt(i) as? ActionMenuView)?.let { menuView ->
                for (j in 0 until menuView.childCount) {
                    menuView.getChildAt(j).overlay.clear()
                }
            }
        }
    }

    override fun onDestroy() {
        toolbar.removeCallbacks(selectionBadgeUpdate)
        super.onDestroy()
    }
}