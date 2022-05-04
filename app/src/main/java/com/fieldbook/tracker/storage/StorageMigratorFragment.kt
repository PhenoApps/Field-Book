package com.fieldbook.tracker.storage

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.DefineStorageActivity
import com.fieldbook.tracker.database.dao.ObservationDao
import com.fieldbook.tracker.database.dao.ObservationUnitDao
import com.fieldbook.tracker.database.dao.StudyDao
import com.fieldbook.tracker.database.models.ObservationModel
import org.phenoapps.fragments.storage.PhenoLibMigratorFragment

class StorageMigratorFragment: PhenoLibMigratorFragment() {

    override val migrateButtonColor = Color.parseColor("#795548")
    override val skipButtonColor = Color.parseColor("#795548")
    override val backgroundColor = Color.parseColor("#8BC34A")

    override fun onUpdateUri(from: DocumentFile, to: DocumentFile) {
        from.updateUri(to)
    }

    private fun DocumentFile.updateUri(docFile: DocumentFile) {
        //for field book we need to check if this type of file was persisted in the database
        //the observation value for  the file path must be updated in the database
        if (parentFile?.exists() == true && parentFile?.name in arrayOf("audio", "photos")) {
            name?.let { name ->
                ObservationDao.getObservationByValue(name)?.let { observation ->
                    if (observation.map.isNotEmpty()) {
                        ObservationDao.updateObservation(ObservationModel(
                            observation.createMap().apply {
                                this["value"] = docFile.uri.toString()
                            }))
                    }
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

            Toast.makeText(context, R.string.frag_migrator_status_complete,
                Toast.LENGTH_SHORT).show()

            activity?.setResult(Activity.RESULT_OK)
            activity?.finish()
        }
    }
}