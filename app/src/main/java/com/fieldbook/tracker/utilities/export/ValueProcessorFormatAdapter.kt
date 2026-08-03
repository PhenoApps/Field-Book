package com.fieldbook.tracker.utilities.export

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.traits.CategoricalTraitLayout
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.traits.formats.presenters.UriPresenter
import com.fieldbook.tracker.traits.formats.presenters.ValuePresenter
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import com.fieldbook.tracker.utilities.DateJsonUtil
import com.fieldbook.tracker.utilities.FileUtil
import com.fieldbook.tracker.utilities.TreePathPortability
import dagger.hilt.android.qualifiers.ApplicationContext
import org.phenoapps.utils.BaseDocumentTreeUtil
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class ValueProcessorFormatAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val spectralFileProcessor: SpectralFileProcessor
) {

    /** When set (export), prefer this study folder over the active FIELD_FILE preference. */
    @Volatile
    var exportFieldName: String? = null

    companion object {
        private const val TAG = "ValueProcessorFA"
        const val MISSING_SIDECAR_PREFIX = "MISSING:"

        private val missingSidecarNames = ConcurrentHashMap.newKeySet<String>()

        /** Call once at the start of a study export so tallies do not leak across runs. */
        @JvmStatic
        fun resetMissingSidecarTally() {
            missingSidecarNames.clear()
        }

        @JvmStatic
        fun missingSidecarCount(): Int = missingSidecarNames.size

        private fun noteMissingSidecar(shown: String) {
            missingSidecarNames.add(shown)
        }
    }

    fun processValue(value: String, trait: TraitObject, forBrapi: Boolean = false): String? {
        return when {

            trait.format in setOf(Formats.DATE.getDatabaseName()) -> {

                val dateValue = DateJsonUtil.decode(value)

                return (Formats.DATE.getTraitFormatDefinition() as ValuePresenter).represent(context, dateValue, trait)
            }

            trait.format in CategoricalTraitLayout.POSSIBLE_VALUES -> {
                return CategoryJsonUtil.processValue(
                    buildMap {
                        put("value", value)
                        put("observation_variable_field_book_format", trait.format)
                        put("forBrapi", forBrapi)
                        put("categoryDisplayValue", trait.categoryDisplayValue)
                    })
            }

            trait.format in Formats.getSpectralFormats().map { it.getDatabaseName() } -> {
                spectralFileProcessor.processValue(value)
                    .onSuccess { value ->
                        val presentable = UriPresenter().represent(context, value)
                        return presentable
                    }
                    .onFailure {

                        Log.d(TAG, "Error processing value: ${it.message}")

                        return ""
                    }
            }

            Formats.isCameraTrait(trait.format) -> {
                if (value.isEmpty()) "" else UriPresenter().represent(context, value, trait)
            }

            trait.format == Formats.TREE_ARCHITECTURE.getDatabaseName() -> {
                if (value.isEmpty()) {
                    ""
                } else {
                    val relative = TreePathPortability.toRelative(value)
                    val exists = when {
                        value.startsWith("file:") -> {
                            val path = value.toUri().path
                            path != null && java.io.File(path).exists()
                        }
                        value.startsWith("content:") ->
                            DocumentFile.fromSingleUri(context, value.toUri())?.exists() == true
                        value.startsWith("/") -> java.io.File(value).exists()
                        else -> {
                            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                            val studyName = exportFieldName
                                ?.takeIf { it.isNotBlank() }
                                ?: prefs.getString(GeneralKeys.FIELD_FILE, null)
                            if (studyName.isNullOrBlank()) {
                                // Blank study → cannot verify; treat as missing (R-06).
                                false
                            } else {
                                val mediaRoot = BaseDocumentTreeUtil.getFile(
                                    context,
                                    R.string.dir_plot_data,
                                    studyName,
                                )
                                val traitDir = mediaRoot?.findFile(FileUtil.sanitizeFileName(trait.name))
                                val leaf = relative.substringAfterLast('/').ifBlank { relative }
                                traitDir?.findFile(leaf)?.exists() == true ||
                                    mediaRoot?.findFile(leaf)?.exists() == true
                            }
                        }
                    }
                    val shown = relative.ifBlank {
                        UriPresenter().represent(context, value, trait)
                    }
                    if (exists) {
                        shown
                    } else {
                        noteMissingSidecar(shown)
                        "$MISSING_SIDECAR_PREFIX$shown"
                    }
                }
            }

            else -> value

        }.toString()
    }
}