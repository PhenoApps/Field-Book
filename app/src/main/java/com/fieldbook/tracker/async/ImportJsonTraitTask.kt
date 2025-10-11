package com.fieldbook.tracker.async

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.FieldFileObject
import com.fieldbook.tracker.objects.TraitAttributesJson
import com.fieldbook.tracker.objects.TraitImportFile
import com.fieldbook.tracker.objects.TraitJson
import com.fieldbook.tracker.objects.TraitObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.phenoapps.utils.BaseDocumentTreeUtil
import java.io.BufferedReader
import java.io.InputStreamReader

class ImportJsonTraitTask(
    private val context: Context,
    private val db: DataHelper,
    private val fileUri: Uri,
    private val scope: CoroutineScope,
    private val onPostExecute: OnPostExecute
) {

    companion object {
        private const val TAG = "ImportJsonTraitTask"
    }

    private var loadingDialog: AlertDialog? = null

    interface OnPostExecute {
        fun execute(success: Boolean, errorMessage: String?)
    }

    fun start() {

        scope.launch {

            showLoadingDialog()

            val result = withContext(Dispatchers.IO) {
                importJsonTraits()
            }

            dismissLoadingDialog()

            onPostExecute.execute(result.first, result.second)

        }

    }

    private fun showLoadingDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_loading, null)
        val messageTv = dialogView.findViewById<TextView>(R.id.loading_message)
        messageTv.text = context.getString(R.string.import_dialog_importing)

        loadingDialog = AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setView(dialogView)
            .create()

        loadingDialog?.show()

    }

    private fun dismissLoadingDialog() {

        loadingDialog?.dismiss()

        loadingDialog = null

    }

    private suspend fun importJsonTraits(): Pair<Boolean, String?> {

        return try {

            val inputStream =
                BaseDocumentTreeUtil.getUriInputStream(context, fileUri)
                    ?: return Pair(false, context.getString(R.string.act_field_editor_file_open_failed))

            val jsonContent = BufferedReader(InputStreamReader(inputStream)).use { reader -> reader.readText() }

            val importFile = try {
                Json.decodeFromString<TraitImportFile>(jsonContent)
            } catch (e: Exception) {
                Log.e(TAG, "JSON parsing error", e)
                return Pair(false, context.getString(R.string.import_error_format_trait_json, e.message))
            }

            if (importFile.traits.isEmpty()) {
                return Pair(false, context.getString(R.string.import_error_no_traits))
            }

            val fileObject = FieldFileObject.create(context, fileUri, null, null)
            val sourceString = fileObject.fileStem

            var successCount = 0

            withContext(Dispatchers.IO) {
                importFile.traits.forEach { traitJson ->
                    convertToTraitObject(traitJson, sourceString)?.let { trait ->
                        trait.realPosition = db.maxPositionFromTraits + 1
                        val result = db.insertTraits(trait)
                        if (result != -1L) {
                            successCount++
                        }
                    }
                }
            }

            Log.d(TAG, "Finished importing $successCount traits")
            Pair(true, null)

        } catch (e: Exception) {
            Log.e(TAG, "Error importing JSON traits", e)
            Pair(false, e.message ?: context.getString(R.string.traits_create_unknown_error, e.message))
        }
    }

    private fun convertToTraitObject(json: TraitJson, sourceString: String): TraitObject? {
        return try {
            if (json.name.isEmpty() || json.format.isEmpty()) {
                Log.d(TAG, "Skipping trait with missing name or format")
                return null
            }

            TraitObject().apply {
                name = json.name
                alias = json.alias ?: json.name
                synonyms = json.synonyms.ifEmpty { listOf(alias) }
                format = json.format
                defaultValue = json.defaultValue
                details = json.details
                visible = json.visible
                traitDataSource = sourceString

                json.attributes?.let { attrs ->
                    applyAttributes(attrs)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting trait: ${json.name}", e)
            null
        }
    }

    private fun TraitObject.applyAttributes(attrs: TraitAttributesJson) {
        attrs.minValue?.let { minimum = it }
        attrs.maxValue?.let { maximum = it }
        attrs.categories?.let { categories = it }
        attrs.closeKeyboard?.let { closeKeyboardOnOpen = it }
        attrs.cropImage?.let { cropImage = it }
        attrs.saveImage?.let { saveImage = it }
        attrs.useDayOfYear?.let { useDayOfYear = it }
        attrs.categoryDisplayValue?.let { categoryDisplayValue = it }
        attrs.resourceFile?.let { resourceFile = it }
        attrs.decimalPlaces?.let { maxDecimalPlaces = it }
        attrs.mathSymbolsEnabled?.let { mathSymbolsEnabled = it }
        attrs.allowMulticat?.let { allowMulticat = it }
        attrs.repeatedMeasures?.let { repeatedMeasures = it }
        attrs.autoSwitchPlot?.let { autoSwitchPlot = it }
        attrs.unit?.let { unit = it }
        attrs.invalidValues?.let { invalidValues = it }
    }
}