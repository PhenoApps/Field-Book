package com.fieldbook.tracker.fragments

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.utilities.Constants
import com.fieldbook.tracker.utilities.Utils
import com.fieldbook.tracker.views.RequiredSetupItem
import com.github.appintro.SlidePolicy
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.phenoapps.fragments.storage.PhenoLibStorageDefinerFragment.AssetSample
import org.phenoapps.utils.BaseDocumentTreeUtil
import org.phenoapps.utils.BaseDocumentTreeUtil.Companion.getRoot
import org.phenoapps.utils.BaseDocumentTreeUtil.Companion.isVolumeRoot
import org.phenoapps.utils.KeyUtil
import pub.devrel.easypermissions.EasyPermissions


class RequiredSetupPolicyFragment : Fragment(), SlidePolicy {
    private var slideTitle: String? = null
    private var slideSummary: String? = null
    private var slideBackgroundColor: Int? = null

    private var permissionsSetupItem: RequiredSetupItem? = null
    private var storageDefinerSetupItem: RequiredSetupItem? = null

    private var prefs: SharedPreferences? = null

    private val scope by lazy { CoroutineScope(Dispatchers.Main) }

    private var directories: Array<String>? = null

    private var samples = mapOf(
        AssetSample("field_import", "field_sample.csv") to R.string.dir_field_import,
        AssetSample("field_import", "field_sample2.csv") to R.string.dir_field_import,
        AssetSample("field_import", "field_sample3.csv") to R.string.dir_field_import,
        AssetSample("field_import", "rtk_sample.csv") to R.string.dir_field_import,
        AssetSample("field_import", "training_sample.csv") to R.string.dir_field_import,
        AssetSample("resources", "feekes_sample.jpg") to R.string.dir_resources,
        AssetSample("resources", "stem_rust_sample.jpg") to R.string.dir_resources,
        AssetSample("trait", "trait_sample.trt") to R.string.dir_trait,
        AssetSample("trait", "trait_sample_json.trt") to R.string.dir_trait,
        AssetSample("trait", "severity.txt") to R.string.dir_trait,
        AssetSample("database", "sample_db.zip") to R.string.dir_database)

    private val launcher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->

        val ctx = context ?: return@registerForActivityResult

        uri?.let { nonNullUri ->

            persistSinglePermission(ctx, nonNullUri)

            //older devices can choose the volume root, getRoot expects a default folder to be saved in that case
            val treeUri = if (nonNullUri.isVolumeRoot()) defineDefaultFolder(ctx, nonNullUri) else nonNullUri

            if (treeUri == null) return@registerForActivityResult

            runBlocking {

                directories?.let { dirs ->

                    BaseDocumentTreeUtil.defineRootStructure(ctx, treeUri, dirs)?.let { _ ->

                        samples.entries.forEach { entry ->

                            val sampleAsset = entry.key
                            val dir = entry.value

                            BaseDocumentTreeUtil.copyAsset(context, sampleAsset.name, sampleAsset.dir, dir)
                        }
                    }
                }
            }
        }
    }

    /**
     * Persists the chosen uri and releases any previously chosen ones,
     * getRoot only reads the first persisted permission.
     */
    private fun persistSinglePermission(ctx: Context, uri: Uri) {

        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        with(ctx.contentResolver) {

            takePersistableUriPermission(uri, flags)

            persistedUriPermissions.forEach {
                if (it.uri != uri) {
                    releasePersistableUriPermission(it.uri, flags)
                }
            }
        }
    }

    /**
     * Finds or creates the default app folder within a volume root and saves it as the default tree uri.
     */
    private fun defineDefaultFolder(ctx: Context, volumeRoot: Uri): Uri? {

        val root = DocumentFile.fromTreeUri(ctx, volumeRoot) ?: return null

        val folder = root.findFile(DEFAULT_APP_FOLDER)?.takeIf { it.isDirectory }
            ?: root.createDirectory(DEFAULT_APP_FOLDER)
            ?: return null

        PreferenceManager.getDefaultSharedPreferences(ctx).edit()
            .putString(KeyUtil(ctx).defaultTreeUri, folder.uri.toString())
            .apply()

        return folder.uri
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.app_intro_required_setup_slide, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = context?.let { PreferenceManager.getDefaultSharedPreferences(it) }

        val slideTitle = view.findViewById<TextView>(R.id.slide_title)
        val slideSummary = view.findViewById<TextView>(R.id.slide_summary)

        slideTitle?.text = this.slideTitle
        slideSummary?.text = this.slideSummary

        slideBackgroundColor?.let { view.setBackgroundResource(it) }

        permissionsSetupItem = view.findViewById(R.id.permissions_setup_item)
        storageDefinerSetupItem = view.findViewById(R.id.storage_definer_setup_item)

        initSetupItems()

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
    }

    private fun initSetupItems() {
        permissionsSetupItem?.apply {
            setIcon(R.drawable.configure)
            setTitle(getString(R.string.app_intro_permissions_title))
            setSummary(getString(R.string.permission_rationale_trait_features))
            setOnClickListener {
                performSetup(this, {requestPermissions()}, {validatePermissions()})
            }
        }

        storageDefinerSetupItem?.apply {
            setIcon(R.drawable.storage_lock)
            setTitle(getString(R.string.app_intro_storage_title))
            setSummary(getString(R.string.app_intro_storage_summary))
            setOnClickListener {
                performSetup(this, {requestStorageDefiner()}, {validateStorage()})
            }
        }

        checkSetupStatus(permissionsSetupItem, validatePermissions())
        checkSetupStatus(storageDefinerSetupItem, validateStorage())
    }

    private fun performSetup(requiredSetupItem: RequiredSetupItem, setupLaunch: () -> Unit, validatorFunction: () -> Boolean) {
        scope.launch {

            setupLaunch()

            withContext(Dispatchers.Default) {
                while (!validatorFunction()) {
                    // check every 100ms if the item is set up i.e. call back is completed
                    delay(100)
                }
            }

            checkSetupStatus(requiredSetupItem, validatorFunction())

        }
    }

    private fun checkSetupStatus(setupItemView: RequiredSetupItem?, isSet: Boolean) {
        if (isSet) {
            setupItemView?.setStatus(R.drawable.ic_about_up_to_date)
        }
    }

    private fun requestPermissions() {
        activity?.let { ActivityCompat.requestPermissions(it, Constants.permissions, Constants.PERM_REQ) }
    }

    private fun requestStorageDefiner() {
        launcher.launch(null)
    }

    private fun validatePermissions(): Boolean {
        var permissionsGranted = false
        var perms = arrayOf<String?>(
            Manifest.permission.VIBRATE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
        )
        val finePerms = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarsePerms =
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            perms = arrayOf(
                Manifest.permission.VIBRATE,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
            )
        }

        context?.let {
            permissionsGranted =
                (EasyPermissions.hasPermissions(it, *perms)
                && (
                    EasyPermissions.hasPermissions(it, *finePerms) || EasyPermissions.hasPermissions(it, *coarsePerms)
                )
            )
        }

        return permissionsGranted
    }

    private fun validateStorage(): Boolean {
        return try {
            val root = getRoot(context)
            root != null && root.exists()
        } catch (e: Exception) {
            //e.g a volume root was persisted without a default folder, user must choose storage again
            e.printStackTrace()
            false
        }
    }

    override val isPolicyRespected: Boolean
        get() = validateItems()

    override fun onUserIllegallyRequestedNextPage() {
        try {
            if (!validatePermissions()) {
                Utils.makeToast(context, getString(R.string.app_intro_permissions_warning))
            } else if (!validateStorage()) {
                Utils.makeToast(context, getString(R.string.app_intro_storage_warning))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private fun validateItems(): Boolean {
        return validatePermissions() && validateStorage()
    }

    companion object {

        //matches StorageDefinerFragment.defaultAppName
        private const val DEFAULT_APP_FOLDER = "fieldBook"

        fun newInstance(
            slideTitle: String,
            slideSummary: String,
            slideBackgroundColor: Int
        ): RequiredSetupPolicyFragment {
            val fragment = RequiredSetupPolicyFragment()
            fragment.slideTitle = slideTitle
            fragment.slideSummary = slideSummary
            fragment.slideBackgroundColor = slideBackgroundColor
            return fragment
        }
    }
}