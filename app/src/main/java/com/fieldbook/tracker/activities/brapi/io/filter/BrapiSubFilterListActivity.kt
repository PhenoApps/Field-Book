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

    @OptIn(ExperimentalBadgeUtils::class)
    override fun resetSelectionCountDisplay() {

        val toolbar = findViewById<MaterialToolbar>(R.id.act_list_filter_tb)

        val numSelected = (recyclerView.adapter as CheckboxListAdapter).selected.size

        if (numFilterBadge != null) BadgeUtils.detachBadgeDrawable(numFilterBadge, toolbar, R.id.action_clear_selection)

        if (numSelected > 0) {
            selectionMenuItem?.isVisible = true
            numFilterBadge = BadgeDrawable.create(this).apply {
                isVisible = true
                number = numSelected
                horizontalOffset = 16
                maxNumber = 9
            }.also {
                BadgeUtils.attachBadgeDrawable(it, toolbar, R.id.action_clear_selection)
            }
        } else {
            selectionMenuItem?.isVisible = false
        }
    }
}