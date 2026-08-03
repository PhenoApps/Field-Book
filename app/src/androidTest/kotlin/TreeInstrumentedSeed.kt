package com.fieldbook.tracker.traits.tree

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.TraitActivity
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import kotlinx.coroutines.runBlocking
import org.phenoapps.utils.BaseDocumentTreeUtil
import java.io.File

object TreeInstrumentedSeed {

    const val FIELD_NAME = "field1"
    const val SAMPLE_ID = "sample1"
    /** Second plot for A→B flush/leak instrumented proofs (R-01 / R-02). */
    const val SAMPLE_ID_B = "sample2"
    private const val UNIQUE_COL = "plot_id"
    private const val PRIMARY_COL = "row"
    private const val SECONDARY_COL = "column"
    private const val STORAGE_FOLDER = "fieldBook"
    private const val DEFAULT_TREE_URI_KEY = "org.phenoapps.phenolib.keys.default_uri"

    fun enableExperimentalTraits() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appContext = context.applicationContext
        listOf(context, appContext).forEach { ctx ->
            PreferenceManager.getDefaultSharedPreferences(ctx).edit {
                putBoolean(PreferenceKeys.EXPERIMENTAL_TRAITS_CATEGORY, true)
            }
        }
    }

    /**
     * Best-effort SAF seed for headless emulators. Collect flush also falls back to
     * app-specific external files when [DocumentTreeUtil.getFieldMediaDirectory] is null.
     */
    fun ensureDocumentTree(context: Context = InstrumentationRegistry.getInstrumentation().targetContext) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val pkg = context.packageName
        automation.executeShellCommand("mkdir -p /sdcard/$STORAGE_FOLDER/resources").close()
        automation.executeShellCommand("appops set $pkg MANAGE_EXTERNAL_STORAGE allow").close()
        automation.executeShellCommand("pm grant $pkg android.permission.READ_EXTERNAL_STORAGE").close()
        automation.executeShellCommand("pm grant $pkg android.permission.WRITE_EXTERNAL_STORAGE").close()
        automation.adoptShellPermissionIdentity()
        try {
            val rootDir = File(Environment.getExternalStorageDirectory(), STORAGE_FOLDER)
            if (!rootDir.exists()) {
                rootDir.mkdirs()
            }
            File(rootDir, "resources").mkdirs()

            val treeUri = DocumentsContract.buildTreeDocumentUri(
                "com.android.externalstorage.documents",
                "primary:$STORAGE_FOLDER",
            )
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            // Shell can grant to the app; takePersistable alone fails after pm clear.
            runCatching {
                context.grantUriPermission(pkg, treeUri, flags)
                context.contentResolver.takePersistableUriPermission(treeUri, flags)
            }

            val dirs = arrayOf(
                context.getString(R.string.dir_archive),
                context.getString(R.string.dir_database),
                context.getString(R.string.dir_field_export),
                context.getString(R.string.dir_field_import),
                context.getString(R.string.dir_geonav),
                context.getString(R.string.dir_plot_data),
                context.getString(R.string.dir_resources),
                context.getString(R.string.dir_trait),
                context.getString(R.string.dir_updates),
                context.getString(R.string.dir_preferences),
            )
            runCatching { BaseDocumentTreeUtil.defineRootStructure(context, treeUri, dirs) }

            listOf(context, context.applicationContext).forEach { ctx ->
                PreferenceManager.getDefaultSharedPreferences(ctx).edit {
                    putString(DEFAULT_TREE_URI_KEY, treeUri.toString())
                    putString(GeneralKeys.DEFAULT_STORAGE_LOCATION_PREFERENCE, treeUri.toString())
                    putString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY, rootDir.absolutePath)
                    if (PreferenceManager.getDefaultSharedPreferences(ctx)
                            .getString(GeneralKeys.FIELD_FILE, "").isNullOrBlank()
                    ) {
                        putString(GeneralKeys.FIELD_FILE, FIELD_NAME)
                    }
                }
            }
        } finally {
            automation.dropShellPermissionIdentity()
        }
    }

    fun seedStudyTraits(activity: TraitActivity) {
        runBlocking {
            val repo = activity.traitRepo
            val existing = repo.getTraits().map { it.name }.toSet()
            listOf(
                Triple("length", "numeric", emptyList<String>()),
                Triple("color", "text", emptyList()),
                Triple("flowering date", "date", emptyList()),
                Triple("branch photo", "photo", emptyList()),
            ).forEachIndexed { index, (name, format, _) ->
                if (name !in existing) {
                    repo.insertTrait(
                        TraitObject().apply {
                            this.name = name
                            alias = name
                            this.format = format
                            visible = true
                            realPosition = index
                        },
                    )
                }
            }
        }
    }

    /**
     * CollectActivity.onResume → RangeBoxView.reload() calls cancelAndFinish() when
     * there are no plots for SELECTED_FIELD_ID. Seed field1/sample1 and select it.
     */
    fun seedAndSelectSampleField(activity: TraitActivity): Int {
        val studyId = ensureField1(activity.database)
        require(studyId > 0) { "Failed to seed collect-ready field" }
        ensureSample2(activity.database, studyId)
        selectField(activity.database, studyId)
        val selected = PreferenceManager.getDefaultSharedPreferences(activity)
            .getInt(GeneralKeys.SELECTED_FIELD_ID, -1)
        require(selected == studyId) {
            "Field switch did not stick (expected $studyId, got $selected)"
        }
        return studyId
    }

    private fun ensureField1(db: DataHelper): Int {
        val existing = db.getAllFieldObjects("study_name").firstOrNull { it.name == FIELD_NAME }
        if (existing != null && existing.studyId > 0) return existing.studyId

        val columns = listOf(PRIMARY_COL, SECONDARY_COL, UNIQUE_COL)
        val field = FieldObject().apply {
            name = FIELD_NAME
            alias = FIELD_NAME
            uniqueId = UNIQUE_COL
            primaryId = PRIMARY_COL
            secondaryId = SECONDARY_COL
            entryCount = "2"
        }
        DataHelper.db.beginTransaction()
        try {
            val studyId = db.createField(field, columns, false)
            require(studyId > 0) { "createField failed for $FIELD_NAME" }
            db.createFieldData(studyId, columns, listOf("1", "1", SAMPLE_ID))
            db.createFieldData(studyId, columns, listOf("1", "2", SAMPLE_ID_B))
            db.updateImportDate(studyId)
            DataHelper.db.setTransactionSuccessful()
            return studyId
        } finally {
            if (DataHelper.db.inTransaction()) {
                DataHelper.db.endTransaction()
            }
        }
    }

    /** Adds sample2 when an older field1 only has sample1. */
    private fun ensureSample2(db: DataHelper, studyId: Int) {
        val units = db.getAllObservationUnits(studyId)
        if (units.any { it.observation_unit_db_id == SAMPLE_ID_B }) return
        val columns = listOf(PRIMARY_COL, SECONDARY_COL, UNIQUE_COL)
        DataHelper.db.beginTransaction()
        try {
            db.createFieldData(studyId, columns, listOf("1", "2", SAMPLE_ID_B))
            db.updateImportDate(studyId)
            DataHelper.db.setTransactionSuccessful()
        } finally {
            if (DataHelper.db.inTransaction()) {
                DataHelper.db.endTransaction()
            }
        }
    }

    private fun selectField(db: DataHelper, studyId: Int) {
        db.switchField(studyId)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        listOf(context, context.applicationContext).forEach { ctx ->
            PreferenceManager.getDefaultSharedPreferences(ctx).edit {
                putInt(GeneralKeys.SELECTED_FIELD_ID, studyId)
                putString(GeneralKeys.FIELD_FILE, FIELD_NAME)
                putString(GeneralKeys.FIELD_ALIAS, FIELD_NAME)
                putString(GeneralKeys.UNIQUE_NAME, UNIQUE_COL)
                putString(GeneralKeys.PRIMARY_NAME, PRIMARY_COL)
                putString(GeneralKeys.SECONDARY_NAME, SECONDARY_COL)
                putBoolean(GeneralKeys.IMPORT_FIELD_FINISHED, true)
                putString(GeneralKeys.LAST_PLOT, SAMPLE_ID)
            }
        }
    }
}
