package com.fieldbook.tracker.storage

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.dao.ObservationDao
import com.fieldbook.tracker.database.models.ObservationModel
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RequiresApi(Build.VERSION_CODES.KITKAT)
class StorageMigratorFragment: Fragment(R.layout.fragment_storage_migrator) {

    private val mPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        result?.let { permissions ->
            if (!permissions.containsValue(false)) {
                //input is an optional uri that would define the folder to start from
                mDocumentTree.launch(null)
            } else {
                activity?.finish()
            }
        }
    }

    private val mDocumentTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->

        uri?.let { nonNulluri ->

            context?.let { ctx ->

                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                with (context?.contentResolver) {

                    if (this?.persistedUriPermissions != null && this.persistedUriPermissions.isNotEmpty()) {

                        this.persistedUriPermissions.first().uri?.let { newRoot ->

                            //add new uri to persistable that the user just picked
                            takePersistableUriPermission(nonNulluri, flags)

                            DocumentFile.fromTreeUri(ctx, nonNulluri)?.let { oldTree ->

                                DocumentFile.fromTreeUri(ctx, newRoot)?.let { newTree ->

                                    val executor = Executors.newFixedThreadPool(2)
                                    executor.execute {

                                        migrateStorage(oldTree, newTree)

                                    }

                                    executor.shutdown()
                                    executor.awaitTermination(10000L, TimeUnit.MILLISECONDS)
                                }
                            }

                            //release old storage directory from persistable if it exists
                            if (persistedUriPermissions.isNotEmpty()) {
                                persistedUriPermissions.forEach {
                                    if (it.uri != newRoot) {
                                        releasePersistableUriPermission(it.uri,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        //finish activity
        activity?.setResult(Activity.RESULT_OK)
        activity?.finish()
    }

    /**
     * Copy all folders from old uri to new uri.
     * If from uri is null, check the default <29 SDK root Coordinate folder.
     */
    private fun migrateStorage(from: DocumentFile, to: DocumentFile) {

        context?.let { ctx ->

            to.breadthCopy(to.uri, from)
        }
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

    private fun copy(root: Uri, output: DocumentFile, file: DocumentFile) {

        if (root != file.uri) { //do not permit infinite recursion

            file.name?.let { name ->

                if (!file.isDirectory) {

                    val checkDoc = output.findFile(name)
                    if (checkDoc == null || !checkDoc.exists()) {

                        output.createFile("*/*", name)?.let { docFile ->

                            context?.contentResolver?.openInputStream(file.uri)?.use { input ->

                                context?.contentResolver?.openOutputStream(docFile.uri)?.use { output ->

                                    input.copyTo(output)

                                }
                            }

                            file.updateUri(docFile)

                        }

                    } else file.updateUri(checkDoc)

                } else {

                    val dir = output.findFile(name)

                    if (dir == null || !dir.isDirectory) {

                        output.createDirectory(name)?.breadthCopy(root, file)

                    } else dir.breadthCopy(root, file)
                }
            }
        }
    }

    private fun DocumentFile.breadthCopy(root: Uri, file: DocumentFile) {
        file.listFiles().forEach { f ->
            copy(root, this, f)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val migrateButton = view.findViewById<Button>(R.id.frag_storage_migrator_choose_dir_btn)
        val skipButton = view.findViewById<Button>(R.id.frag_storage_migrator_skip_btn)

        migrateButton?.setOnClickListener { _ ->

            launchMigrator()

        }

        skipButton?.setOnClickListener {
            activity?.setResult(Activity.RESULT_OK)
            activity?.finish()
        }
    }

    private fun launchMigrator() {
        context?.let { ctx ->

            //request runtime permissions for storage
            if (ActivityCompat.checkSelfPermission(ctx,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(ctx,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {

                //input is an optional uri that would define the folder to start from
                mDocumentTree.launch(null)

            } else {

                mPermissions.launch(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE))

            }
        }
    }
}