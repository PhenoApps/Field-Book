package com.fieldbook.tracker.utilities.export

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.traits.formats.tree.TreeFlattenExport
import com.fieldbook.tracker.utilities.FileUtil
import com.fieldbook.tracker.utilities.TreeSidecarWriter
import org.phenoapps.utils.BaseDocumentTreeUtil

object TreeExportHelper {

    private const val TAG = "TreeExportHelper"

    /**
     * Writes `_nodes.csv` into the field export directory (zip root), not into plot_data
     * media folders — otherwise a media bundle copies the same CSV again under the trait dir.
     */
    fun exportFlattenedNodesCsv(
        context: Context,
        database: DataHelper,
        studyName: String,
        exportTraits: List<TraitObject>,
    ): List<DocumentFile> {
        val created = mutableListOf<DocumentFile>()
        val treeTraits = exportTraits.filter {
            it.format.equals(Formats.TREE_ARCHITECTURE.getDatabaseName(), ignoreCase = true)
        }
        if (treeTraits.isEmpty()) return created

        val mediaDir = BaseDocumentTreeUtil.getFile(context, R.string.dir_plot_data, studyName) ?: return created
        val exportDir = BaseDocumentTreeUtil.getDirectory(context, R.string.dir_field_export) ?: return created
        treeTraits.forEach { trait ->
            val traitDirName = FileUtil.sanitizeFileName(trait.name)
            val traitDir = mediaDir.findFile(traitDirName) ?: return@forEach
            val jsonFiles = traitDir.listFiles().filter {
                it.isFile && it.name?.endsWith(".json") == true
            }
            var header: String? = null
            val bodyLines = mutableListOf<String>()
            jsonFiles.forEach { file ->
                val obs = TreeSidecarWriter.read(context, file.uri) ?: return@forEach
                val rows = TreeFlattenExport.rows(obs)
                if (header == null && rows.isNotEmpty()) {
                    header = rows.first().joinToString(",") { TreeFlattenExport.escapeCsv(it) }
                }
                bodyLines += rows.drop(1).joinToString("\n") { row ->
                    row.joinToString(",") { TreeFlattenExport.escapeCsv(it) }
                }
            }
            val csvHeader = header ?: return@forEach
            if (bodyLines.isEmpty()) return@forEach
            val csvName = "${studyName}_${traitDirName}_nodes.csv"
            // Drop stale copies previously written into the trait media folder.
            traitDir.findFile(csvName)?.delete()
            exportDir.findFile(csvName)?.delete()
            exportDir.createFile("text/csv", csvName)?.let { csvFile ->
                context.contentResolver.openOutputStream(csvFile.uri, "wt")?.use { out ->
                    out.write((csvHeader + "\n" + bodyLines.joinToString("\n")).toByteArray())
                }
                created += csvFile
            } ?: Log.w(TAG, "Could not create nodes csv for $traitDirName")
        }
        return created
    }

    /** Media-bundle denylist: never ship legacy MTG companions or stale nodes.csv copies. */
    fun shouldSkipBundledMediaLeaf(fileName: String): Boolean {
        val n = fileName
        if (n.endsWith("_nodes.csv", ignoreCase = true)) return true
        if (TreeSidecarWriter.isOrphanMtgCompanionName(n)) return true
        return false
    }
}
