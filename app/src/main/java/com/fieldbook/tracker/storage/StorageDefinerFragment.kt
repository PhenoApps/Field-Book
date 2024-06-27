package com.fieldbook.tracker.storage

import android.app.Activity
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.DefineStorageActivity
import com.fieldbook.tracker.utilities.SharedPreferenceUtils
import dagger.hilt.android.AndroidEntryPoint
import org.phenoapps.fragments.storage.PhenoLibStorageDefinerFragment
import javax.inject.Inject

@AndroidEntryPoint
class StorageDefinerFragment: PhenoLibStorageDefinerFragment() {

    @Inject
    lateinit var prefs: SharedPreferences

    //default root folder name if user choose an incorrect root on older devices
    override val defaultAppName: String = "fieldBook"

    //if this file exists the migrator will be skipped
    override val migrateChecker: String = ".fieldbook"

    //define sample data and where to transfer
    override val samples = mapOf(
        AssetSample("field_import", "field_sample.csv") to R.string.dir_field_import,
        AssetSample("field_import", "field_sample2.csv") to R.string.dir_field_import,
        AssetSample("field_import", "field_sample3.csv") to R.string.dir_field_import,
        AssetSample("field_import", "rtk_sample.csv") to R.string.dir_field_import,
        AssetSample("field_import", "training_sample.csv") to R.string.dir_field_import,
        AssetSample("resources", "feekes_sample.jpg") to R.string.dir_resources,
        AssetSample("resources", "stem_rust_sample.jpg") to R.string.dir_resources,
        AssetSample("trait", "trait_sample.trt") to R.string.dir_trait,
        AssetSample("trait", "severity.txt") to R.string.dir_trait,
        AssetSample("database", "sample_db.zip") to R.string.dir_database)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //define directories that should be created in root storage
        context?.let { ctx ->
            val archive = ctx.getString(R.string.dir_archive)
            val db = ctx.getString(R.string.dir_database)
            val fieldExport = ctx.getString(R.string.dir_field_export)
            val fieldImport = ctx.getString(R.string.dir_field_import)
            val geonav = ctx.getString(R.string.dir_geonav)
            val plotData = ctx.getString(R.string.dir_plot_data)
            val resources = ctx.getString(R.string.dir_resources)
            val trait = ctx.getString(R.string.dir_trait)
            val updates = ctx.getString(R.string.dir_updates)
            directories = arrayOf(archive, db, fieldExport, fieldImport,
                geonav, plotData, resources, trait, updates
            )
        }
    }

    override fun onTreeDefined(treeUri: Uri) {
        (activity as DefineStorageActivity).enableBackButton(false)
        super.onTreeDefined(treeUri)
        (activity as DefineStorageActivity).enableBackButton(true)
    }

    override fun actionAfterDefine() {
        actionNoMigrate()
    }

    override fun actionNoMigrate() {
        activity?.setResult(Activity.RESULT_OK)
        activity?.finish()
    }

    //https://stackoverflow.com/questions/9469174/set-theme-for-a-fragment
    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
        val inflater = super.onGetLayoutInflater(savedInstanceState)
        val contextThemeWrapper =
            ContextThemeWrapper(context, SharedPreferenceUtils.getThemeResource(prefs))
        return inflater.cloneInContext(contextThemeWrapper)
    }
}