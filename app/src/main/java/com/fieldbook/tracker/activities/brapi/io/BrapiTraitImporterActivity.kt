package com.fieldbook.tracker.activities.brapi.io

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.activities.brapi.io.filterer.BrapiTraitFilterActivity
import com.fieldbook.tracker.adapters.BrapiTraitImportAdapter
import com.fieldbook.tracker.dialogs.NewTraitDialog
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

class BrapiTraitImporterActivity : BrapiTraitImportAdapter.TraitLoader, ThemedActivity(), CoroutineScope by MainScope() {

    //override val titleResId: Int = R.string.act_brapi_trait_preprocess_title

    companion object {

        fun getIntent(activity: Activity): Intent {
            return Intent(activity, BrapiTraitImporterActivity::class.java)
        }
    }

    private var toolbar: Toolbar? = null
    private var recyclerView: androidx.recyclerview.widget.RecyclerView? = null
    private var finishButton: MaterialButton? = null

    protected val intentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            //restoreModels()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_brapi_trait_preprocess)
        toolbar = findViewById(R.id.act_brapi_trait_import_tb)
        recyclerView = findViewById(R.id.act_brapi_trait_import_rv)

        val selectedTraits = BrapiFilterTypeAdapter.toModelList(prefs, BrapiTraitFilterActivity.FILTER_NAME)

        recyclerView?.adapter = BrapiTraitImportAdapter(this).also {
            it.submitList(selectedTraits.map {
                BrapiTraitImportAdapter.Model(it.id, it.label, R.drawable.ic_trait_text)
            })
            it.notifyDataSetChanged()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onItemClicked(id: String) {

        val traitDialog = NewTraitDialog(this) {

        }

        traitDialog.show(supportFragmentManager, "NewTraitDialog")
    }
}