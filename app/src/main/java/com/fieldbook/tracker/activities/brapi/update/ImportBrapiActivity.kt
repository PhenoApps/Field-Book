package com.fieldbook.tracker.activities.brapi.update

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.MenuItem
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.activities.brapi.update.contracts.ProgramsResultContract
import com.fieldbook.tracker.brapi.model.BrapiProgram
import com.fieldbook.tracker.views.CloseableTextView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class ImportBrapiActivity: ThemedActivity() {

    private lateinit var searchToolbar: Toolbar
    private lateinit var programChipGroup: ChipGroup

    private val programFilterLauncher = registerForActivityResult(ProgramsResultContract()) { result ->
        if (result != null) {
            //do something with the result
        }
    }

    private val filterLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
//            val filter = data?.getParcelableExtra<BrapiFilter>("filter")
//            if (filter != null) {
//
//            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_brapi_importer)

        setupMainToolbar()
        setupChipGroups()

//        val filterContainer = findViewById<LinearLayout>(R.id.brapi_importer_filter_container_ll)
//
//        (Handler(Looper.getMainLooper())).postDelayed({
//            filterContainer.addView(CloseableTextView(this))
//        }, 10000)
    }

    private fun setupChipGroups() {

        programChipGroup = findViewById(R.id.brapi_importer_program_cg)

        prefs.getStringSet("programDbIds", setOf())?.forEach { programDbId ->
            val themedContext = ContextThemeWrapper(this, com.google.android.material.R.style.Theme_MaterialComponents)
            val chip = Chip(themedContext)
            chip.text = programDbId
            programChipGroup.addView(chip)
        }
    }

    private fun setupMainToolbar() {

        searchToolbar = findViewById(R.id.search_toolbar_tb)

        setSupportActionBar(searchToolbar)

        supportActionBar?.title = getString(R.string.import_brapi_title)

        //enable home button as back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        //add menu
    // supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back)

    }

    //add menu to toolbar
    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_import_brapi, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        } else if (item.itemId == R.id.action_brapi_filter) {
            startBrapiFilterActivity()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun startBrapiFilterActivity() {
        filterLauncher.launch(BrapiProgramFilterActivity.getIntent(this))
    }
}