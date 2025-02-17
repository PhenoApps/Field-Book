package com.fieldbook.tracker.storage

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.DefineStorageActivity
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.SharedPreferenceUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import org.phenoapps.fragments.storage.PhenoLibStorageDefinerFragment
import org.phenoapps.security.Security
import org.phenoapps.utils.BaseDocumentTreeUtil
import javax.inject.Inject

@AndroidEntryPoint
class StorageDefinerFragment: PhenoLibStorageDefinerFragment() {

    @Inject
    lateinit var prefs: SharedPreferences

    private val advisor by Security().secureDocumentTree()

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
            val preferences = ctx.getString(R.string.dir_preferences)
            directories = arrayOf(archive, db, fieldExport, fieldImport,
                geonav, plotData, resources, trait, updates, preferences
            )
        }

        // Set the title and summary text
        view.findViewById<TextView>(R.id.frag_storage_definer_title_tv).text = getString(R.string.storage_definer_title)
        view.findViewById<TextView>(R.id.frag_storage_definer_summary_tv).text = getString(R.string.storage_definer_summary)

        view.visibility = View.GONE

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        prefs.edit().putBoolean(GeneralKeys.FROM_INTRO_AUTOMATIC, true).apply()

        advisor.defineDocumentTree({ treeUri ->

            runBlocking {

                directories?.let { dirs ->

                    BaseDocumentTreeUtil.defineRootStructure(context, treeUri, dirs)?.let { root ->

                        samples.entries.forEach { entry ->

                            val sampleAsset = entry.key
                            val dir = entry.value

                            BaseDocumentTreeUtil.copyAsset(context, sampleAsset.name, sampleAsset.dir, dir)
                        }

                        activity?.setResult(Activity.RESULT_OK)
                        activity?.finish()
                    }
                }
            } },
            {
                activity?.finish()
            }
        )
    }

    //https://stackoverflow.com/questions/9469174/set-theme-for-a-fragment
    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
        val inflater = super.onGetLayoutInflater(savedInstanceState)
        val contextThemeWrapper =
            ContextThemeWrapper(context, SharedPreferenceUtils.getThemeResource(prefs))
        return inflater.cloneInContext(contextThemeWrapper)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        advisor.initialize()
    }
}