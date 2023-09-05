package com.fieldbook.tracker.storage

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.DefineStorageActivity
import org.phenoapps.fragments.storage.PhenoLibStorageDefinerFragment

class StorageDefinerFragment: PhenoLibStorageDefinerFragment() {

    override val buttonColor = Color.parseColor("#8BC34A")
    override val backgroundColor = Color.parseColor("#FFFFFF")

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
        AssetSample("trait", "trait_sample.trt") to R.string.dir_trait,
        AssetSample("trait", "severity.txt") to R.string.dir_trait,
        AssetSample("database", "sample.db") to R.string.dir_database,
        AssetSample("database", "sample.db_sharedpref.xml") to R.string.dir_database)

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
                geonav, plotData, resources, trait, updates)
        }
    }

    override fun onTreeDefined(treeUri: Uri) {
        (activity as DefineStorageActivity).enableBackButton(false)
        super.onTreeDefined(treeUri)
        (activity as DefineStorageActivity).enableBackButton(true)
    }

    override fun actionNoMigrate() {
        activity?.setResult(Activity.RESULT_OK)
        activity?.finish()
    }
}