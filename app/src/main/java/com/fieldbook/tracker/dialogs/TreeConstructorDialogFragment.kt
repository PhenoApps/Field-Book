package com.fieldbook.tracker.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.traits.composables.constructor.TreeConstructorScreen
import com.fieldbook.tracker.traits.composables.constructor.blankSchema
import com.fieldbook.tracker.traits.formats.tree.TreeCodec
import com.fieldbook.tracker.traits.formats.tree.TreeSchema
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.utilities.FileUtil
import com.fieldbook.tracker.utilities.TreeSchemaLoader
import dagger.hilt.android.AndroidEntryPoint
import org.phenoapps.utils.BaseDocumentTreeUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Fullscreen schema editor. Not a bottom sheet — scrolling must not dismiss it;
 * exit only via Close / Cancel / system Back.
 *
 * Save notifies callers via [REQUEST_KEY_SCHEMA_SAVED] FragmentResult (survives recreate).
 */
@AndroidEntryPoint
class TreeConstructorDialogFragment : DialogFragment() {

    companion object {
        const val REQUEST_KEY_SCHEMA_SAVED = "TreeConstructor.schema_saved"
        const val RESULT_RESOURCE_REF = "resource_ref"

        private const val ARG_TRAIT_NAME = "trait_name"
        private const val ARG_RESOURCE_REF = "resource_ref"

        fun show(
            activity: FragmentActivity,
            traitName: String = "",
            existingResourceRef: String? = null,
        ) {
            TreeConstructorDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TRAIT_NAME, traitName)
                    putString(ARG_RESOURCE_REF, existingResourceRef)
                }
            }.show(activity.supportFragmentManager, "TreeConstructor")
        }
    }

    @Inject
    lateinit var database: DataHelper

    /** When Blank/Soy resets the in-memory schema, never overwrite the previously picked leaf. */
    private var forceNewLeaf = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.AppTheme)
        isCancelable = true
        forceNewLeaf = false
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dialog?.setCanceledOnTouchOutside(false)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val traits = database.allTraitObjects
        val traitName = arguments?.getString(ARG_TRAIT_NAME).orEmpty()
        val resourceRef = arguments?.getString(ARG_RESOURCE_REF)
        val initial: TreeSchema? = if (resourceRef.isNullOrBlank()) {
            blankSchema()
        } else {
            TreeSchemaLoader.load(requireContext(), resourceRef)
        }
        if (initial == null) {
            Toast.makeText(
                requireContext(),
                getString(R.string.tree_schema_load_failed),
                Toast.LENGTH_LONG,
            ).show()
            return ComposeView(requireContext()).also { view ->
                view.post { dismissAllowingStateLoss() }
            }
        }
        val initialLeaf = resourceRef
            ?.let { TreeSchemaLoader.schemaLeaf(it) }
            ?.takeIf { it.isNotBlank() }
        return ComposeView(requireContext()).apply {
            setContent {
                AppTheme {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { testTagsAsResourceId = true },
                    ) {
                        TreeConstructorScreen(
                            initialSchema = initial,
                            availableTraits = traits,
                            traitNameHint = traitName,
                            existingResourceLeaf = initialLeaf,
                            onTemplateReset = { forceNewLeaf = true },
                            isSchemaUsedByObservations = { schemaId ->
                                isSchemaIdUsedByObservations(schemaId)
                            },
                            onSave = { schema, leafFileName -> saveSchema(schema, leafFileName) },
                            onCancel = { dismiss() },
                        )
                    }
                }
            }
        }
    }

    private fun isSchemaIdUsedByObservations(schemaId: String): Boolean {
        if (schemaId.isBlank()) return false
        val treeTraits = database.allTraitObjects.filter {
            it.format.equals(
                com.fieldbook.tracker.traits.formats.Formats.TREE_ARCHITECTURE.getDatabaseName(),
                ignoreCase = true,
            )
        }
        for (trait in treeTraits) {
            val loaded = TreeSchemaLoader.load(requireContext(), trait.resourceFile) ?: continue
            if (loaded.id != schemaId) continue
            if (database.getAllObservationsOfVariable(trait.id).isNotEmpty()) return true
        }
        return false
    }

    private fun saveSchema(schema: TreeSchema, leafFileName: String) {
        val dir = BaseDocumentTreeUtil.getDirectory(requireContext(), R.string.dir_resources)
        val existingRef = if (forceNewLeaf) null else arguments?.getString(ARG_RESOURCE_REF)
        val leaf = if (dir != null) {
            val file = resolveSaveTarget(dir, schema, existingRef, leafFileName)
            if (file == null) {
                Toast.makeText(requireContext(), getString(R.string.tree_schema_save_failed), Toast.LENGTH_LONG).show()
                return
            }
            val wrote = runCatching {
                requireContext().contentResolver.openOutputStream(file.uri, "wt")?.use {
                    it.write(TreeCodec.encodeSchema(schema).toByteArray())
                } != null
            }.getOrDefault(false)
            if (!wrote) {
                Toast.makeText(requireContext(), getString(R.string.tree_schema_save_failed), Toast.LENGTH_LONG).show()
                return
            }
            file.name ?: leafFileName.ifBlank {
                TreeSchemaLoader.schemaLeaf(existingRef.orEmpty()).ifBlank {
                    "${FileUtil.sanitizeFileName(schema.id)}_schema.json"
                }
            }
        } else {
            // SAF tree missing (common after instrumented pm clear) — app-specific files.
            val fallbackDir = java.io.File(
                requireContext().getExternalFilesDir(null),
                getString(R.string.dir_resources),
            )
            if (!fallbackDir.exists() && !fallbackDir.mkdirs()) {
                Toast.makeText(requireContext(), getString(R.string.tree_schema_save_failed), Toast.LENGTH_LONG).show()
                return
            }
            val leaf = leafFileName.ifBlank {
                val stamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date())
                val stem = FileUtil.sanitizeFileName(schema.id).ifBlank { "tree_schema" }
                "${stem}_$stamp.json"
            }
            val out = java.io.File(fallbackDir, leaf)
            val wrote = runCatching {
                out.writeText(TreeCodec.encodeSchema(schema))
                true
            }.getOrDefault(false)
            if (!wrote) {
                Toast.makeText(requireContext(), getString(R.string.tree_schema_save_failed), Toast.LENGTH_LONG).show()
                return
            }
            leaf
        }
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY_SCHEMA_SAVED,
            Bundle().apply { putString(RESULT_RESOURCE_REF, leaf) },
        )
        dismiss()
    }

    /** Overwrite the edited resource when possible; otherwise create using the confirmed leaf name. */
    private fun resolveSaveTarget(
        dir: DocumentFile,
        schema: TreeSchema,
        existingRef: String?,
        leafFileName: String,
    ): DocumentFile? {
        if (!forceNewLeaf && !existingRef.isNullOrBlank()) {
            val leaf = TreeSchemaLoader.schemaLeaf(existingRef).ifBlank { leafFileName }
            if (leaf.isNotBlank()) {
                dir.findFile(leaf)?.takeIf { it.isFile }?.let { return it }
                dir.createFile("application/json", leaf)?.let { return it }
            }
        }
        val leaf = leafFileName.ifBlank {
            val stamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date())
            val stem = FileUtil.sanitizeFileName(schema.id).ifBlank { "tree_schema" }
            "${stem}_$stamp.json"
        }
        return dir.createFile("application/json", leaf)
    }
}
