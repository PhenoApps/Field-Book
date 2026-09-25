package com.fieldbook.tracker.printing

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.utilities.BluetoothChooseCallback
import org.phenoapps.labelprint.service.LabelPrintManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reusable label printing service that encapsulates all printing functionality.
 * Delegates core logic to the :label-print library's [LabelPrintManager].
 */
@Singleton
class LabelPrintService @Inject constructor(
    private val printerConnector: AppPrinterConnector
) {

    companion object {
        private const val BLUETOOTH_PERMISSION_REQUEST_CODE = 99

        const val MAX_COPIES = 10
        val COPIES_OPTIONS = (1..MAX_COPIES).map { it.toString() }

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

        /**
         * The value a placeholder's assigned field option prints for a plot: the field name,
         * an observation unit attribute, or the latest observation of a trait.
         */
        fun resolveFieldValue(
            fieldOption: String,
            observationUnitAttributes: Map<String, String>,
            fieldName: String,
            fieldNameLabel: String,
            database: com.fieldbook.tracker.database.DataHelper? = null,
            studyId: String? = null,
            plotId: String? = null,
            context: Context? = null
        ): String {
            return when (fieldOption) {
                fieldNameLabel -> fieldName
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

        fun shouldInsertObservation(numLabels: Int, plotId: String?, traitId: String?): Boolean {
            return numLabels > 0 && plotId != null && traitId != null
        }

        fun applyPlaceholderAssignments(zpl: String, assignments: Map<String, String>) =
            LabelPrintManager.applyPlaceholderAssignments(zpl, assignments)
    }

    fun printLabels(context: Context, labels: List<String>, plotId: String, trait: TraitObject?) {
        printerConnector.print(labels, plotId, trait) { success, error ->
            if (!success) {
                Toast.makeText(
                    context,
                    error ?: context.getString(R.string.print_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
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
        printerConnector.connectToPrinter(context) { success ->
            if (success) {
                callback?.onDeviceChosen(printerConnector.getConnectedPrinterName() ?: "")
            }
        }
    }

    fun getSavedPrinterName(): String? = printerConnector.getConnectedPrinterName()

    fun isBluetoothEnabled(): Boolean = printerConnector.isBluetoothEnabled()

    fun calibrate(onResult: (Boolean) -> Unit) {
        printerConnector.calibrate(onResult)
    }
}
