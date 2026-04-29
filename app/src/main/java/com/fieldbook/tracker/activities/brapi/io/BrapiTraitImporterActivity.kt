package com.fieldbook.tracker.activities.brapi.io

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import androidx.activity.OnBackPressedDispatcher
import androidx.appcompat.widget.Toolbar
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.activities.brapi.io.filter.filterer.BrapiTraitFilterActivity
import com.fieldbook.tracker.activities.brapi.io.mapper.DataTypes
import com.fieldbook.tracker.activities.brapi.io.mapper.toTraitObject
import com.fieldbook.tracker.adapters.BrapiTraitImportAdapter
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.dialogs.NewTraitDialog
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.TextTraitLayout
import com.fieldbook.tracker.traits.formats.Formats
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import javax.inject.Inject
import androidx.core.content.edit
import com.fieldbook.tracker.utilities.InsetHandler

@AndroidEntryPoint
class BrapiTraitImporterActivity : BrapiTraitImportAdapter.TraitLoader, ThemedActivity(),
    CoroutineScope by MainScope(),
    NewTraitDialog.TraitObjectUpdateListener {

    companion object {

        const val EXTRA_TRAIT_DB_ID = "observationVariableDbId"

        fun getIntent(activity: Activity): Intent {
            return Intent(activity, BrapiTraitImporterActivity::class.java)
        }
    }

    @Inject
    lateinit var database: DataHelper

    private var toolbar: Toolbar? = null
    private var recyclerView: androidx.recyclerview.widget.RecyclerView? = null
    private var finishButton: MaterialButton? = null
    private var progressBar: ProgressBar? = null

    private var selectId: String? = null

    private var cache: List<CheckboxListAdapter.Model> = listOf()

    private var varUpdates: HashMap<String, TraitObject> = hashMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_brapi_trait_preprocess)
        toolbar = findViewById(R.id.act_brapi_trait_import_tb)
        recyclerView = findViewById(R.id.act_brapi_trait_import_rv)
        progressBar = findViewById(R.id.act_brapi_trait_import_pb)

        finishButton = findViewById(R.id.act_brapi_trait_import_finish_button)

        setupToolbar()

        parseIntentExtras()

        val rootView = findViewById<View>(android.R.id.content)
        InsetHandler.setupStandardInsets(rootView, toolbar)

        onBackPressedDispatcher.addCallback(this, standardBackCallback())
    }

    private fun parseIntentExtras() {
        val dbIds = intent.getStringArrayListExtra(EXTRA_TRAIT_DB_ID)
        if (dbIds == null) {
            setResult(RESULT_CANCELED)
            finish()
        }

        cache = BrapiFilterCache.getStoredModels(this).variables.values
            .filter { it.observationVariableDbId != null && dbIds?.contains(it.observationVariableDbId) == true }
            .map { model ->
                CheckboxListAdapter.Model(
                    checked = false,
                    id = model.observationVariableDbId,
                    label = model.synonyms?.firstOrNull() ?: model.observationVariableName ?: model.observationVariableDbId,
                    subLabel = "${model.commonCropName ?: ""} ${model.observationVariableDbId ?: ""}"
                ).also {
                    model.scale?.dataType?.name?.let { dataType ->
                        Formats.findTrait(DataTypes.convertBrAPIDataType(dataType))?.iconDrawableResourceId?.let { icon ->
                            it.iconResId = icon
                        }
                    }
                }
            }.toList()

        recyclerView?.adapter = BrapiTraitImportAdapter(this).also {
            it.submitList(cache)
        }

        var nextPosition = database.maxPositionFromTraits + 1

        finishButton?.setOnClickListener {

            recyclerView?.visibility = View.GONE
            finishButton?.visibility = View.GONE
            progressBar?.visibility = View.VISIBLE

            BrapiFilterCache.getStoredModels(this).variables.values.forEach { trait ->
                if (varUpdates[trait.observationVariableDbId] == null) {
                    varUpdates[trait.observationVariableDbId] = trait.toTraitObject(this)
                }
            }

            varUpdates.forEach { (t, u) ->
                if (t in dbIds!!) {
                    database.insertTraits(u.apply {
                        realPosition = nextPosition++
                    })
                }
            }

            prefs.edit { remove(BrapiTraitFilterActivity.FILTER_NAME) }

            setResult(RESULT_OK)
            finish()
        }
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
                setResult(RESULT_CANCELED)
                onBackPressedDispatcher.onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onItemClicked(id: String) {

        selectId = id

        val traitObject =
            BrapiFilterCache.getStoredModels(this).variables.values
                .find { it.observationVariableDbId == id }?.toTraitObject(this)

        if (traitObject?.format == TextTraitLayout.type) {

            val traitDialog = NewTraitDialog(this)

            //traitDialog.isSelectingFormat = true

            traitDialog.isBrapiTraitImport = true

            traitDialog.setTraitObject(traitObject)

            traitDialog.show(supportFragmentManager, "TraitDialogChooser")
        }
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