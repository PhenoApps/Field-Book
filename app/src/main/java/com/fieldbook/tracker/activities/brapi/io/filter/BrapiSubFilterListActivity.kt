package com.fieldbook.tracker.activities.brapi.io.filter

import android.text.TextWatcher
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.CheckboxListAdapter
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

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menu?.findItem(R.id.action_check_all)?.isVisible = true
        menu?.findItem(R.id.action_reset_cache)?.isVisible = false
        menu?.findItem(R.id.action_brapi_filter)?.isVisible = false
        return true
    }
}