package com.fieldbook.tracker.async

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitAttributesJson
import com.fieldbook.tracker.objects.TraitImportFile
import com.fieldbook.tracker.objects.TraitJson
import com.fieldbook.tracker.objects.TraitObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.phenoapps.utils.BaseDocumentTreeUtil
import java.io.OutputStreamWriter

/**
 * Exports trait objects to JSON format asynchronously
 * Shows loading dialog during export and executes a callback with result
 */
class ExportJsonTraitTask(
    private val context: Context,
    private val traits: List<TraitObject>,
    private val fileName: String,
    private val scope: CoroutineScope,
    private val onPostExecute: OnPostExecute
) {

    sealed class ExportResult {
        data class Success(val uri: Uri, val exportedCount: Int) : ExportResult()
        data class Error(val message: String, val error: Throwable? = null) : ExportResult()
    }

    companion object {
        private const val TAG = "ExportJsonTraitTask"

        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }

    private var loadingDialog: AlertDialog? = null

    interface OnPostExecute {
        fun execute(result: ExportResult)
    }

    fun start() {
        scope.launch {
            showLoadingDialog()

            val result = withContext(Dispatchers.IO) {
                exportJsonTraits()
            }

            dismissLoadingDialog()

            onPostExecute.execute(result)
        }
    }

    private fun showLoadingDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_loading, null)
        val messageTv = dialogView.findViewById<TextView>(R.id.loading_message)
        messageTv.text = context.getString(R.string.export_dialog_traits_exporting)

        loadingDialog = AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setView(dialogView)
            .create()

        loadingDialog?.show()
    }

    private fun dismissLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    private suspend fun exportJsonTraits(): ExportResult {
        return try {
            val traitDir = BaseDocumentTreeUtil.getDirectory(context, R.string.dir_trait)
                ?: return ExportResult.Error(context.getString(R.string.export_error_directory_access))

            if (!traitDir.exists()) {
                return ExportResult.Error(context.getString(R.string.export_error_directory_not_found))
            }

            val exportDoc = traitDir.createFile("*/*", fileName)
                ?: return ExportResult.Error(context.getString(R.string.export_error_file_creation))

            val output = BaseDocumentTreeUtil.getFileOutputStream(context, R.string.dir_trait, fileName)
                ?: return ExportResult.Error(context.getString(R.string.export_error_file_stream))

            val exportedCount = withContext(Dispatchers.IO) {
                val traitJsonList = traits.map { convertTraitObjectToJson(it) }
                val exportFile = TraitImportFile(traitJsonList)

                val jsonString = json.encodeToString(exportFile)

                OutputStreamWriter(output).use { writer ->
                    writer.write(jsonString)
                    writer.flush()
                }
                output.close()

                traits.size
            }

            Log.d(TAG, "Successfully exported $exportedCount traits to JSON")
            ExportResult.Success(exportDoc.uri, exportedCount)

        } catch (e: Exception) {
            Log.e(TAG, "Error exporting JSON traits", e)
            ExportResult.Error(
                message = e.message ?: context.getString(R.string.export_error_unknown),
                error = e
            )
        }
    }

    private fun convertTraitObjectToJson(trait: TraitObject): TraitJson {
        trait.loadAttributeAndValues()

        // take only if the attribute is not equal to it's default
        val attributes = TraitAttributesJson(
            minValue = trait.minimum.takeIf { it.isNotEmpty() },
            maxValue = trait.maximum.takeIf { it.isNotEmpty() },
            categories = trait.categories.takeIf { it.isNotEmpty() },
            closeKeyboard = trait.closeKeyboardOnOpen.takeIf { it != false },
            cropImage = trait.cropImage.takeIf { it != false },
            saveImage = trait.saveImage.takeIf { it != true },
            useDayOfYear = trait.useDayOfYear.takeIf { it != false },
            categoryDisplayValue = trait.categoryDisplayValue.takeIf { it != false },
            resourceFile = trait.resourceFile.takeIf { it.isNotEmpty() },
            decimalPlaces = trait.maxDecimalPlaces.takeIf { it.isNotEmpty() && it != "-1" },
            mathSymbolsEnabled = trait.mathSymbolsEnabled.takeIf { it != true },
            allowMulticat = trait.allowMulticat.takeIf { it != false },
            repeatedMeasures = trait.repeatedMeasures.takeIf { it != false },
            autoSwitchPlot = trait.autoSwitchPlot.takeIf { it != false },
            unit = trait.unit.takeIf { it.isNotEmpty() },
            invalidValues = trait.invalidValues.takeIf { it != false }
        )

        return TraitJson(
            name = trait.name,
            alias = trait.alias,
            synonyms = trait.synonyms,
            format = trait.format,
            defaultValue = trait.defaultValue,
            details = trait.details,
            visible = trait.visible,
            position = trait.realPosition,
            attributes = if (hasNonNullAttributes(attributes)) attributes else null
        )
    }

    private fun hasNonNullAttributes(attributes: TraitAttributesJson): Boolean {
        return with(attributes) {
            minValue != null ||
            maxValue != null ||
            categories != null ||
            closeKeyboard != null ||
            cropImage != null ||
            saveImage != null ||
            useDayOfYear != null ||
            categoryDisplayValue != null ||
            resourceFile != null ||
            decimalPlaces != null ||
            mathSymbolsEnabled != null ||
            allowMulticat != null ||
            repeatedMeasures != null ||
            autoSwitchPlot != null ||
            unit != null ||
            invalidValues != null
        }
    }
}