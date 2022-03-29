package com.fieldbook.tracker.storage

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.fieldbook.tracker.R
import com.fieldbook.tracker.utilities.DocumentTreeUtil.Companion.createFieldBookFolders
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RequiresApi(Build.VERSION_CODES.KITKAT)
class StorageDefinerFragment: Fragment(R.layout.fragment_storage_definer) {

    private val mPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        result?.let { permissions ->
            if (!permissions.containsValue(false)) {
                //input is an optional uri that would define the folder to start from
                mDocumentTree.launch(null)
            } else {
                activity?.setResult(Activity.RESULT_CANCELED)
                activity?.finish()
            }
        }
    }

    private val mDocumentTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->

        uri?.let { nonNulluri ->

            context?.let { ctx ->

                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                with (context?.contentResolver) {

//                    val lastPermitted = if (this?.persistedUriPermissions != null
//                        && this.persistedUriPermissions.isNotEmpty()) {
//                        this.persistedUriPermissions.first().uri
//                    } else null

                    //add new uri to persistable that the user just picked
                    this?.takePersistableUriPermission(nonNulluri, flags)

                    //release old storage directory from persistable if it exists
                    val oldPermitted = this?.persistedUriPermissions
                    if (oldPermitted != null && oldPermitted.isNotEmpty()) {
                        this?.persistedUriPermissions?.forEach {
                            if (it.uri != nonNulluri) {
                                releasePersistableUriPermission(it.uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            }
                        }
                    }

                    DocumentFile.fromTreeUri(ctx, nonNulluri)?.let { root ->

//                        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
//                        if (prefs.getBoolean(DocumentTreeUtil.MIGRATE_ASK_KEY, true)) {

                        val executor = Executors.newFixedThreadPool(2)
                        executor.execute {
                            root.createFieldBookFolders(context)
                        }
                        executor.shutdown()
                        executor.awaitTermination(10000, TimeUnit.MILLISECONDS)
                        //copy the HTPG.xml file to the newly defined folder
                        //copy FB sample files into sample directories s.a in coordinate:
//                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
//
//                                root.createDirectory(ctx.getString(R.string.FolderExport))
//                                root.createDirectory(ctx.getString(R.string.FolderTemplate))?.let { templates ->
//
//                                    templates.createFile("*/*", "HTPG.xml")?.uri?.let { uri ->
//
//                                        ctx.contentResolver.openOutputStream(uri)?.let { output ->
//
//                                            ctx.resources.openRawResource(R.raw.htpg).copyTo(output)
//
//                                        }
//                                    }
//                                }
//                            }

//                            prefs.edit().putBoolean(DocumentTreeUtil.MIGRATE_ASK_KEY, false).apply()
//                            activity?.setResult(Activity.RESULT_OK)
//                            activity?.finish()

                        findNavController().navigate(StorageDefinerFragmentDirections
                            .actionStorageDefinerToStorageMigrator())


                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val defineButton = view.findViewById<Button>(R.id.frag_storage_definer_choose_dir_btn)

        defineButton?.setOnClickListener { _ ->

            launchDefiner()

        }
    }

    private fun launchDefiner() {
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