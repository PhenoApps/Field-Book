package com.fieldbook.tracker.storage

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.DefineStorageActivity
import com.fieldbook.tracker.database.dao.ObservationDao
import com.fieldbook.tracker.database.models.ObservationModel
import org.phenoapps.fragments.storage.PhenoLibMigratorFragment

class StorageMigratorFragment: PhenoLibMigratorFragment() {

    override fun onUpdateUri(from: DocumentFile, to: DocumentFile) {
        from.updateUri(to)
    }

    /**
     * Files that need to be updated in the database will be of this structure
     * root/                                <-- user defined
     *      plot_data/                      <-- field book folder
     *              field/                  <-- user defined field name
     *                  trait/              <-- user defined trait name
     *                      media files     <-- image/audio uri's or thumbnails folder with media
     *
     */
    private fun DocumentFile.updateUri(docFile: DocumentFile) {

        val plotDataDir = getString(R.string.dir_plot_data)
        //for field book we need to check if this type of file was persisted in the database
        //the observation value for  the file path must be updated in the database
        if (parentFile?.exists() == true
            && parentFile?.parentFile?.exists() == true
            && parentFile?.parentFile?.parentFile?.exists() == true
            && parentFile?.parentFile?.parentFile?.name == plotDataDir) {
            name?.let { name ->
                try {
                    ObservationDao.getObservationByValue(name)?.let { observation ->
                        if (observation.map.isNotEmpty()) {
                            ObservationDao.updateObservation(ObservationModel(
                                observation.createMap().apply {
                                    this["value"] = docFile.uri.toString()
                                }))
                        }
                    }
                } catch (e: Exception) {
                    Log.d("FieldBook", "Could not migrate ${docFile.uri}")
                    e.printStackTrace()
                }
            }
        }
    }

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

    override fun migrateStorage(from: DocumentFile, to: DocumentFile) {
        (activity as DefineStorageActivity).enableBackButton(false)
        super.migrateStorage(from, to)
        (activity as DefineStorageActivity).enableBackButton(true)
    }

    override fun navigateEnd() {
        activity?.runOnUiThread {

            Toast.makeText(
                context, org.phenoapps.androidlibrary.R.string.frag_migrator_status_complete,
                Toast.LENGTH_SHORT
            ).show()

            activity?.setResult(Activity.RESULT_OK)
            activity?.finish()
        }
    }
}