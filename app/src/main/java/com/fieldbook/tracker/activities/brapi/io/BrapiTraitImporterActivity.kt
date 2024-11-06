package com.fieldbook.tracker.activities.brapi.io

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.activities.brapi.io.filter.filterer.BrapiTraitFilterActivity
import com.fieldbook.tracker.activities.brapi.io.mapper.toTraitObject
import com.fieldbook.tracker.adapters.BrapiTraitImportAdapter
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory
import com.fieldbook.tracker.brapi.service.BrAPIServiceV1
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.dialogs.NewTraitDialog
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.TextTraitLayout
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.traits.formats.TextFormat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BrapiTraitImporterActivity : BrapiTraitImportAdapter.TraitLoader, ThemedActivity(),
    CoroutineScope by MainScope(),
    NewTraitDialog.TraitObjectUpdateListener {

    companion object {

        fun getIntent(activity: Activity): Intent {
            return Intent(activity, BrapiTraitImporterActivity::class.java)
        }
    }

    @Inject
    lateinit var database: DataHelper

    private val brapiService by lazy {
        BrAPIServiceFactory.getBrAPIService(this).also { service ->
            if (service is BrAPIServiceV1) {
                launch(Dispatchers.Main) {
                    Toast.makeText(
                        this@BrapiTraitImporterActivity,
                        getString(R.string.brapi_v1_is_not_compatible),
                        Toast.LENGTH_SHORT
                    ).show()
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
        }
    }

    private var toolbar: Toolbar? = null
    private var recyclerView: androidx.recyclerview.widget.RecyclerView? = null
    private var finishButton: MaterialButton? = null

    private var selectId: String? = null

    private var cache: List<CheckboxListAdapter.Model> = listOf()

    private var varUpdates: HashMap<String, TraitObject> = hashMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_brapi_trait_preprocess)
        toolbar = findViewById(R.id.act_brapi_trait_import_tb)
        recyclerView = findViewById(R.id.act_brapi_trait_import_rv)

        cache = BrapiFilterTypeAdapter.toModelList(prefs, BrapiTraitFilterActivity.FILTER_NAME)

        recyclerView?.adapter = BrapiTraitImportAdapter(this).also {
            it.submitList(cache)
            //it.notifyDataSetChanged()
        }

        finishButton = findViewById(R.id.act_brapi_trait_import_finish_button)

        finishButton?.setOnClickListener {

            //TODO show progress here, dismiss trait dialog when finish
            BrapiFilterCache.getStoredModels(this).mapNotNull { it.variables }.flatten().forEach { trait ->
                if (varUpdates[trait.observationVariableDbId] == null) {
                    varUpdates[trait.observationVariableDbId] = trait.toTraitObject(this)
                }
            }

            varUpdates.forEach { (t, u) ->
                database.insertTraits(u)
            }

            setResult(Activity.RESULT_OK)
            finish()
        }

        setupToolbar()

    }

    private fun setupToolbar() {

        setSupportActionBar(findViewById(R.id.act_brapi_trait_import_tb))

        supportActionBar?.title = getString(R.string.act_brapi_trait_import_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            android.R.id.home -> {
                setResult(Activity.RESULT_CANCELED)
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onItemClicked(id: String) {

        selectId = id

        val traitObject =
            BrapiFilterCache.getStoredModels(this).mapNotNull { it.variables }.flatten()
                .find { it.observationVariableDbId == id }?.toTraitObject(this)

       // if (traitObject?.format == TextTraitLayout.type) {

            val traitDialog = NewTraitDialog(this)

            //traitDialog.isSelectingFormat = true

            traitDialog.isBrapiTraitImport = true

            traitDialog.setTraitObject(traitObject)

            traitDialog.show(supportFragmentManager, "TraitDialogChooser")
       // }
    }

    override fun onTraitObjectUpdated(traitObject: TraitObject) {

        cache.find { it.id == selectId }?.let {
            it.iconResId = Formats.findTrait(traitObject.format)?.iconDrawableResourceId
            varUpdates[selectId!!] = traitObject
        }

        (recyclerView?.adapter as? BrapiTraitImportAdapter)?.submitList(cache)

        (recyclerView?.adapter?.notifyDataSetChanged())
    }
}