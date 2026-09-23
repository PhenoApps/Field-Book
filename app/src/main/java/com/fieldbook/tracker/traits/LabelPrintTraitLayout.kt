package com.fieldbook.tracker.traits

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.AttributeSet
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.toColorInt
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.adapters.AttributeAdapter
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.dialogs.AttributeChooserDialog
import com.fieldbook.tracker.dialogs.LabelFieldChooserDialog
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.printing.LabelPrintConfigDialog
import com.fieldbook.tracker.printing.LabelPrintMainView
import com.fieldbook.tracker.printing.LabelPrintService
import com.fieldbook.tracker.printing.LabelPrintStore
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.zpl.TemplateRepository
import org.phenoapps.labelprint.zpl.ParseResult
import org.phenoapps.labelprint.zpl.TokenizeResult
import org.phenoapps.labelprint.zpl.ZplParserImpl
import org.phenoapps.labelprint.zpl.ZplTokenizerImpl
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Trait layout for Zebra label printing.
 */
class LabelPrintTraitLayout : BaseTraitLayout {

    companion object {
        private const val TAG = "LabelPrintTraitLayout"
    }

    private lateinit var store: LabelPrintStore
    private lateinit var service: LabelPrintService
    private lateinit var templateRepository: TemplateRepository

    private var composeView: ComposeView? = null
    private var mActivity: Activity? = null

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_ON || state == BluetoothAdapter.STATE_OFF) {
                    refreshPrinterConnectionState()
                }
            }
        }
    }

    private val printerMessageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.extras == null) return

            val message = intent.extras?.getString("message")
            val numLabels = intent.extras?.getInt("numLabels", 0) ?: 0
            val plotId = intent.extras?.getString("plotId")
            val traitId = intent.extras?.getString("traitId")

            val activity = mActivity as? CollectActivity ?: return

            activity.runOnUiThread {
                message?.let {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                }
            }

            // numLabels is only set when the printer reported ready and the labels were sent
            if (LabelPrintService.shouldInsertObservation(numLabels, plotId, traitId)) {
                val cumulativeCount = saveLabelCount(activity, plotId!!, traitId!!, numLabels)

                activity.runOnUiThread {
                    triggerTts(context.getString(R.string.trait_print_label_success))

                    // the user may have moved on (e.g. auto switch plot) before the printer responded
                    if (plotId == currentRange?.uniqueId && traitId == currentTrait?.id) {
                        collectInputView?.text = cumulativeCount.toString()
                        collectInputView?.setTextColor(displayColor.toColorInt())
                        collectInputView?.markObservationSaved()
                        activity.updateCurrentTraitStatus(true)
                        activity.refreshRepeatedValuesToolbarIndicator()
                        collectInputView?.refreshTimestamp()
                    }
                    activity.refreshInfoBarAdapter()

                    if (::store.isInitialized) store.assignmentsRevision.intValue++
                }
            }
        }
    }

    /**
     * Adds [numLabels] to the printed-label count of the plot and trait the labels were printed
     * for, not the current plot, since the printer responds asynchronously.
     * Updates the latest rep if one exists, otherwise inserts a new observation.
     * Returns the new count.
     */
    private fun saveLabelCount(
        activity: CollectActivity,
        plotId: String,
        traitId: String,
        numLabels: Int
    ): Int {
        val studyId = activity.studyId
        val latest = database.getAllObservations(studyId, plotId, traitId)
            ?.maxByOrNull { it.rep.toIntOrNull() ?: 0 }
        val count = (latest?.value?.toIntOrNull() ?: 0) + numLabels

        val person = activity.person
        val location = try {
            activity.locationByPreferences
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get location", e)
            null
        }

        if (latest == null) {
            database.insertObservation(
                plotId, traitId, count.toString(), person, location, "", studyId,
                null, null, null, database.getDefaultRep(studyId, plotId, traitId)
            )
        } else {
            latest.value = count.toString()
            latest.collector = person
            latest.geo_coordinates = location
            latest.observation_time_stamp = OffsetDateTime.now()
                .format(DateTimeFormatter.ofPattern(DataHelper.TIME_FORMAT_PATTERN))
            database.updateObservationModels(database.db, listOf(latest))
        }

        return count
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun type(): String = "zebra label print"

    override fun layoutId(): Int = R.layout.trait_label_print_compose

    override fun init(act: Activity) {
        composeView = act.findViewById(R.id.compose_view)
        mActivity = act

        templateRepository = TemplateRepository(prefs)
        store = LabelPrintStore(prefs)
    }

    override fun loadLayout() {
        super.loadLayout()

        // Manual DI for service until fully refactored to Hilt if needed
        val printerConnector = com.fieldbook.tracker.printing.AppPrinterConnector(context, prefs)
        service = LabelPrintService(printerConnector)

        store.restoreConfig()
        store.currentPlotIdState.value = currentRange?.uniqueId
        refreshPrinterConnectionState()

        if (!store.isPrinterConnected.value && !service.getSavedPrinterName().isNullOrEmpty()) {
            connectToPrinter(false)
        }

        val studyId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)
        val attributes = database.getAllObservationUnitAttributeNames(studyId)
        store.buildFieldOptions(context, attributes)

        resolveTemplate()

        setupUi()

        toggleVisibility(VISIBLE)
        collectInputView?.editText?.isFocusable = false
        collectInputView?.editText?.isFocusableInTouchMode = false
        collectInputView?.editText?.isEnabled = false
    }

    /**
     * Traits created before per-trait templates have no template id, and a trait's template
     * may have been deleted, so fall back to the default template.
     */
    private fun resolveTemplate() {
        store.templateIdState.value =
            templateRepository.resolveTemplateId(currentTrait?.printTemplateId)
    }

    private fun setupUi() {
        composeView?.setContent {
            AppTheme {
                val copiesCount = store.selectedCopies.intValue

                val previewLabel = remember(
                    store.templateIdState.value,
                    store.currentPlotIdState.value,
                    store.assignmentsRevision.intValue
                ) {
                    buildPreviewLabel()
                }

                LabelPrintMainView(
                    onPrintClick = { printLabel() },
                    onSettingsClick = { store.showConfigDialog.value = true },
                    onConnectClick = { 
                        store.isManualDisconnected.value = false
                        connectToPrinter(forceChooser = true) 
                    },
                    isPrinterConnected = store.isPrinterConnected.value,
                    copiesCount = copiesCount,
                    previewLabel = previewLabel
                )

                if (store.showConfigDialog.value) {
                    val currentTemplateZpl = remember(
                        store.templateIdState.value
                    ) {
                        val id = store.templateIdState.value
                        if (id != null) templateRepository.getTemplate(id) ?: "" else ""
                    }

                    val currentAssignments = remember(
                        store.templateIdState.value,
                        store.assignmentsRevision.intValue
                    ) {
                        val id = store.templateIdState.value
                        val saved = if (id != null) {
                            val studyId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)
                            templateRepository.getAssignments(studyId, id)
                        } else emptyMap()

                        saved + store.fieldAssignments
                    }

                    LabelPrintConfigDialog(
                        currentCopies = store.selectedCopies.intValue.toString(),
                        copiesOptions = LabelPrintService.COPIES_OPTIONS,
                        onConfirm = { copies ->
                            store.selectedCopies.intValue = copies.toIntOrNull() ?: 1
                            store.saveConfig()

                            store.templateIdState.value?.let { id ->
                                val studyId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)
                                templateRepository.saveAssignments(studyId, id, currentAssignments)
                            }

                            store.assignmentsRevision.intValue++
                            store.fieldAssignments.clear()
                            store.showConfigDialog.value = false
                        },
                        onDismiss = {
                            store.fieldAssignments.clear()
                            store.assignmentsRevision.intValue++
                            store.showConfigDialog.value = false
                        },
                        isPrinterConnected = store.isPrinterConnected.value,
                        onConnectClick = { 
                            store.isManualDisconnected.value = false
                            connectToPrinter(forceChooser = true)
                        },
                        onDisconnectClick = { disconnectPrinter() },
                        onCalibrate = { calibratePrinter() },
                        templateZpl = currentTemplateZpl,
                        currentAssignments = currentAssignments,
                        onFieldClick = { placeholder ->
                            store.pendingFieldPlaceholder = placeholder
                            showAttributeChooserDialog()
                        }
                    )
                }
            }
        }
    }

    private fun showAttributeChooserDialog() {
        val activity = mActivity as? CollectActivity ?: return

        val dialog = LabelFieldChooserDialog()
        dialog.setOnAttributeSelectedListener(object :
            AttributeChooserDialog.OnAttributeSelectedListener {
            override fun onAttributeSelected(model: AttributeAdapter.AttributeModel) {
                val placeholder = store.pendingFieldPlaceholder ?: return
                store.fieldAssignments[placeholder] = model.label
                store.assignmentsRevision.intValue++
                store.pendingFieldPlaceholder = null
            }
        })
        dialog.show(activity.supportFragmentManager, "labelFieldAttributeChooser")
    }

    @SuppressLint("MissingPermission")
    private fun connectToPrinter(forceChooser: Boolean = false) {
        val activity = mActivity ?: return
        if (!service.checkBluetoothPermissions(activity)) return

        if (!service.isBluetoothEnabled()) {
            activity.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        if (forceChooser || service.getSavedPrinterName().isNullOrEmpty()) {
            service.choosePrinter(
                activity,
                object : com.fieldbook.tracker.utilities.BluetoothChooseCallback {
                    override fun onDeviceChosen(deviceName: String) {
                        activity.runOnUiThread { refreshPrinterConnectionState() }
                    }
                })
        } else {
            refreshPrinterConnectionState()
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshPrinterConnectionState() {
        // receivers are registered with the other layouts, before this one may have loaded
        if (!::store.isInitialized || !::service.isInitialized) return
        store.isPrinterConnected.value = service.isBluetoothEnabled() 
                && !service.getSavedPrinterName().isNullOrEmpty()
                && !store.isManualDisconnected.value
    }

    private fun disconnectPrinter() {
        store.isManualDisconnected.value = true
        refreshPrinterConnectionState()
        Toast.makeText(context, R.string.printer_not_connected, Toast.LENGTH_SHORT).show()
    }

    private fun calibratePrinter() {
        val activity = mActivity ?: return
        if (!service.checkBluetoothPermissions(activity)) return
        service.calibrate { success ->
            activity.runOnUiThread {
                val msg =
                    if (success) context.getString(R.string.calibration_sent) else context.getString(
                        R.string.calibration_failed
                    )
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun printLabel() {
        val activity = mActivity ?: return
        if (!service.checkBluetoothPermissions(activity)) return

        val collectActivity = context as? CollectActivity ?: return
        val trait = currentTrait ?: return

        resolveTemplate()
        val templateId = store.templateIdState.value
        val templateZpl = templateId?.let { templateRepository.getTemplate(it) }
        if (templateId == null || templateZpl == null) {
            Toast.makeText(context, R.string.label_print_no_template, Toast.LENGTH_LONG).show()
            return
        }

        val fieldName = prefs.getString(GeneralKeys.FIELD_FILE, "") ?: ""
        val fieldNameLabel = context.getString(R.string.field_name_attribute)

        val rangeObject = currentRange ?: return
        val uniqueId = rangeObject.uniqueId ?: return

        val observationUnitAttributes =
            buildObservationUnitAttributes(uniqueId, fieldNameLabel)

        val studyId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)
        val assignments = templateRepository.getAssignments(studyId, templateId)

        val resolvedAssignments = assignments.mapValues { (_, fieldOption) ->
            LabelPrintService.resolveFieldValue(
                fieldOption, observationUnitAttributes,
                fieldName, fieldNameLabel,
                database = database,
                studyId = collectActivity.studyId,
                plotId = uniqueId,
                context = context
            )
        }

        val resolvedZpl =
            LabelPrintService.applyPlaceholderAssignments(templateZpl, resolvedAssignments)

        val labels = List<String>(store.selectedCopies.intValue) { resolvedZpl }

        store.saveConfig()

        val plotId = collectActivity.getRangeBox().getPlotID() ?: return
        service.printLabels(context, labels, plotId, trait)
    }

    private fun buildPreviewLabel(): org.phenoapps.labelprint.zpl.ZplLabel? {
        val templateId = store.templateIdState.value ?: return null
        val templateZpl = templateRepository.getTemplate(templateId) ?: return null

        val fieldName = prefs.getString(GeneralKeys.FIELD_FILE, "") ?: ""
        val fieldNameLabel = context.getString(R.string.field_name_attribute)

        val uniqueId = currentRange?.uniqueId
        val observationUnitAttributes = if (uniqueId != null) {
            buildObservationUnitAttributes(uniqueId, fieldNameLabel)
        } else emptyMap()

        val studyId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)
        val assignments = templateRepository.getAssignments(studyId, templateId)

        val resolvedAssignments = assignments.mapValues { (_, fieldOption) ->
            LabelPrintService.resolveFieldValue(
                fieldOption, observationUnitAttributes,
                fieldName, fieldNameLabel,
                database = database,
                studyId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString(),
                plotId = uniqueId,
                context = context
            )
        }
        val previewZpl =
            LabelPrintService.applyPlaceholderAssignments(templateZpl, resolvedAssignments)

        if (previewZpl.isNotBlank()) {
            val tokenizer = ZplTokenizerImpl()
            val parser = ZplParserImpl()
            val tokenResult = tokenizer.tokenize(previewZpl)
            if (tokenResult is TokenizeResult.Success) {
                val parseResult = parser.parse(tokenResult.tokens)
                if (parseResult is ParseResult.Success) {
                    return parseResult.document.labels.firstOrNull()
                }
            }
        }
        return null
    }

    private fun buildObservationUnitAttributes(
        uniqueId: String,
        fieldNameLabel: String
    ): Map<String, String> {
        val attributes = mutableMapOf<String, String>()

        // Add core identifiers explicitly using their column names from preferences
        val uniqueName = prefs.getString(GeneralKeys.UNIQUE_NAME, "") ?: ""
        val primaryName = prefs.getString(GeneralKeys.PRIMARY_NAME, "") ?: ""
        val secondaryName = prefs.getString(GeneralKeys.SECONDARY_NAME, "") ?: ""

        if (uniqueName.isNotEmpty()) attributes[uniqueName] = uniqueId
        if (primaryName.isNotEmpty()) attributes[primaryName] =
            database.getObservationUnitPropertyValues(primaryName, uniqueId)
        if (secondaryName.isNotEmpty()) attributes[secondaryName] =
            database.getObservationUnitPropertyValues(secondaryName, uniqueId)

        // Add study-specific attributes
        for (option in store.fieldOptions) {
            if (option != fieldNameLabel
                && option != uniqueName && option != primaryName && option != secondaryName
            ) {
                val value = database.getObservationUnitPropertyValues(option, uniqueId)
                attributes[option] = value ?: ""
            }
        }
        return attributes
    }

    override fun setNaTraitsText() {
        store.assignmentsRevision.intValue++
    }

    override fun refreshLayout(onNew: Boolean?) {
        super.refreshLayout(onNew)
        resolveTemplate()
        store.currentPlotIdState.value = currentRange?.uniqueId
        refreshPrinterConnectionState()
        store.assignmentsRevision.intValue++
    }

    override fun deleteTraitListener() {
        if (isLocked) return
        (context as? CollectActivity)?.removeTrait()
        super.deleteTraitListener()
        collectInputView?.text = ""
        store.assignmentsRevision.intValue++
    }

    fun registerReceiver() {
        Log.d(TAG, "Registering printerMessageReceiver")
        LocalBroadcastManager.getInstance(context)
            .registerReceiver(printerMessageReceiver, IntentFilter("printer_message"))

        try {
            context.registerReceiver(
                bluetoothStateReceiver,
                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register bluetoothStateReceiver", e)
        }
    }

    fun unregisterReceiver() {
        Log.d(TAG, "Unregistering printerMessageReceiver")
        LocalBroadcastManager.getInstance(context)
            .unregisterReceiver(printerMessageReceiver)

        try {
            context.unregisterReceiver(bluetoothStateReceiver)
        } catch (_: Exception) {
            // ignore
        }
    }
}
