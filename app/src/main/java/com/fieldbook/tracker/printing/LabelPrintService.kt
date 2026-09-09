package com.fieldbook.tracker.printing

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.BluetoothChooseCallback
import com.fieldbook.tracker.zpl.TemplateRepository
import org.phenoapps.labelprint.service.LabelPrintManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reusable label printing service that encapsulates all printing functionality.
 * Delegates core logic to the :feature-labelprint library's [LabelPrintManager].
 */
@Singleton
class LabelPrintService @Inject constructor(
    private val prefs: SharedPreferences,
    private val templateRepository: TemplateRepository,
    private val printerConnector: AppPrinterConnector
) {

    companion object {
        private const val BLUETOOTH_PERMISSION_REQUEST_CODE = 99

        val COPIES_OPTIONS = (1..10).map { it.toString() }

        fun getRequiredPermissions(apiLevel: Int): Array<String> {
            return if (apiLevel >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            } else {
                arrayOf(
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }
        }

        fun resolveFieldValue(
            fieldOption: String,
            observationUnitAttributes: Map<String, String>,
            fieldName: String,
            dateString: String,
            blankLabel: String,
            dateLabel: String,
            fieldNameLabel: String,
            database: com.fieldbook.tracker.database.DataHelper? = null,
            studyId: String? = null,
            plotId: String? = null,
            context: Context? = null
        ): String {
            return when (fieldOption) {
                dateLabel -> dateString
                fieldNameLabel -> fieldName
                blankLabel -> ""
                else -> {
                    val attrValue = observationUnitAttributes[fieldOption]
                    if (!attrValue.isNullOrEmpty()) return attrValue

                    if (database != null && studyId != null && plotId != null) {
                        val trait = database.getTraitByAlias(fieldOption)
                            ?: database.getTraitByName(fieldOption)
                        if (trait != null) {
                            val observations =
                                database.getAllObservations(studyId, plotId, trait.id)
                            if (observations != null && observations.isNotEmpty()) {
                                val rawValue = observations.last().value
                                if (context != null) {
                                    val traitFormat =
                                        com.fieldbook.tracker.traits.formats.Formats.findTrait(trait.format)
                                    if (traitFormat != null) {
                                        val valueModel: Any =
                                            if (traitFormat is com.fieldbook.tracker.traits.formats.coders.StringCoder) {
                                                try {
                                                    traitFormat.decode(rawValue)
                                                } catch (_: Exception) {
                                                    rawValue
                                                }
                                            } else rawValue

                                        if (traitFormat is com.fieldbook.tracker.traits.formats.presenters.ValuePresenter) {
                                            try {
                                                return traitFormat.represent(
                                                    context,
                                                    valueModel,
                                                    trait
                                                )
                                            } catch (_: Exception) {
                                            }
                                        }
                                    }
                                }
                                return rawValue
                            }
                        }
                    }
                    ""
                }
            }
        }

        fun buildFieldOptions(
            fieldNameLabel: String,
            attributes: List<String>
        ): List<String> {
            return buildList {
                add(fieldNameLabel)
                addAll(attributes)
            }
        }

        fun currentDateString(): String = LabelPrintManager.currentDateString()

        fun shouldInsertObservation(numLabels: Int, plotId: String?, traitId: String?): Boolean {
            return numLabels > 0 && plotId != null && traitId != null
        }

        fun applyPlaceholderAssignments(zpl: String, assignments: Map<String, String>) =
            LabelPrintManager.applyPlaceholderAssignments(zpl, assignments)
    }

    fun printLabels(context: Context, labels: List<String>, plotId: String, trait: TraitObject?) {
        // AppPrinterConnector currently ignores plotId/trait in its generic print implementation.
        // If logging is needed, it should be done here or inside the connector.
        printerConnector.print(labels) { success, error ->
            if (!success) {
                Toast.makeText(
                    context,
                    error ?: context.getString(R.string.print_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun printFromTemplate(
        context: Context,
        observationUnitAttributes: Map<String, String>,
        fieldName: String,
        copies: Int,
        plotId: String,
        trait: TraitObject,
        dateLabel: String,
        blankLabel: String,
        fieldNameLabel: String
    ) {
        val selectedId = if (trait.printTemplateId.isNotEmpty()) {
            trait.printTemplateId
        } else {
            templateRepository.getSelectedTemplateId()
        } ?: return

        val templateZpl = templateRepository.getTemplate(selectedId) ?: return
        val templateName = templateRepository.getTemplateName(selectedId) ?: return

        val dateString = currentDateString()
        val assignments = templateRepository.getAssignments(templateName).toMutableMap()
        val studyId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)
        assignments.putAll(templateRepository.getStudyAssignments(studyId))

        val resolvedAssignments = assignments.mapValues { (_, fieldOption) ->
            resolveFieldValue(
                fieldOption, observationUnitAttributes,
                fieldName, dateString, blankLabel, dateLabel, fieldNameLabel,
                database = null, // resolveTemplate usually has db passed if needed
                studyId = studyId.toString(),
                plotId = plotId,
                context = context
            )
        }

        val resolvedZpl = applyPlaceholderAssignments(templateZpl, resolvedAssignments)
        val labels = List<String>(copies) { resolvedZpl }

        printLabels(context, labels, plotId, trait)
    }

    fun checkBluetoothPermissions(activity: Activity): Boolean {
        val permissions = getRequiredPermissions(Build.VERSION.SDK_INT)
        val missingPermissions = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(
                activity,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        }

        return if (missingPermissions.isEmpty()) {
            true
        } else {
            ActivityCompat.requestPermissions(
                activity,
                missingPermissions.toTypedArray(),
                BLUETOOTH_PERMISSION_REQUEST_CODE
            )
            Toast.makeText(activity, R.string.permission_ask_bluetooth, Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun choosePrinter(context: Context, callback: BluetoothChooseCallback? = null) {
        printerConnector.connectToPrinter { success ->
            if (success) {
                callback?.onDeviceChosen(printerConnector.getConnectedPrinterName() ?: "")
            }
        }
    }

    fun getSavedPrinterName(): String? = printerConnector.getConnectedPrinterName()

    fun calibrate(onResult: (Boolean) -> Unit) {
        printerConnector.calibrate(onResult)
    }
}
