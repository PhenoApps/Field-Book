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

            if (LabelPrintService.shouldInsertObservation(numLabels, plotId, traitId)) {
                val studyId = activity.studyId
                val currentVal = database.getAllObservations(studyId, plotId!!, traitId!!)
                    ?.lastOrNull()?.value
                val priorCount = currentVal?.toIntOrNull() ?: 0
                val cumulativeCount = priorCount + numLabels

                activity.updateObservation(currentTrait, cumulativeCount.toString(), null)

                activity.runOnUiThread {
                    collectInputView?.text = cumulativeCount.toString()
                    collectInputView?.setTextColor(displayColor.toColorInt())
                    collectInputView?.markObservationSaved()

                    store.assignmentsRevision.intValue++
                }
            }
        }
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
        service = LabelPrintService(prefs, templateRepository, printerConnector)

        store.restoreConfig()
        store.currentPlotIdState.value = currentRange?.uniqueId
        refreshPrinterConnectionState()

        val studyId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)
        val attributes = database.getAllObservationUnitAttributeNames(studyId)
        store.buildFieldOptions(context, attributes)

        currentTrait?.printTemplateId?.let { templateId ->
            if (templateId.isNotEmpty()) {
                store.selectedTemplateNameState.value =
                    templateRepository.getTemplateName(templateId)
            }
        }

        if (store.selectedTemplateNameState.value == null) {
            val defaultName = "2×1 Simple"
            val templates = templateRepository.getAllTemplates()
            if (templates.values.contains(defaultName)) {
                store.selectedTemplateNameState.value = defaultName
            }
        }

        setupUi()

        toggleVisibility(VISIBLE)
        collectInputView?.editText?.isFocusable = false
        collectInputView?.editText?.isFocusableInTouchMode = false
        collectInputView?.editText?.isEnabled = false
    }

    private fun setupUi() {
        composeView?.setContent {
            AppTheme {
                val copiesCount = store.selectedCopies.intValue

                val previewLabel = remember(
                    currentTrait?.printTemplateId,
                    store.currentPlotIdState.value,
                    store.assignmentsRevision.intValue
                ) {
                    buildPreviewLabel()
                }

                LabelPrintMainView(
                    onPrintClick = { printLabel() },
                    onSettingsClick = { store.showConfigDialog.value = true },
                    onConnectClick = { connectToPrinter() },
                    isPrinterConnected = store.isPrinterConnected.value,
                    copiesCount = copiesCount,
                    previewLabel = previewLabel
                )

                if (store.showConfigDialog.value) {
                    val currentTemplateZpl = remember(
                        currentTrait?.printTemplateId
                    ) {
                        val id = currentTrait?.printTemplateId
                        if (id != null) templateRepository.getTemplate(id) ?: "" else ""
                    }

                    val currentAssignments = remember(
                        currentTrait?.printTemplateId,
                        store.assignmentsRevision.intValue
                    ) {
                        val id = currentTrait?.printTemplateId
                        val templateName =
                            if (id != null) templateRepository.getTemplateName(id) else null

                        val templateLoaded = if (templateName != null) {
                            templateRepository.getAssignments(templateName)
                        } else emptyMap()

                        val studyId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)
                        val studyLoaded = templateRepository.getStudyAssignments(studyId)

                        val merged = templateLoaded.toMutableMap()
                        merged.putAll(studyLoaded)
                        merged.putAll(store.fieldAssignments)
                        merged
                    }

                    LabelPrintConfigDialog(
                        currentCopies = store.selectedCopies.intValue.toString(),
                        copiesOptions = LabelPrintService.COPIES_OPTIONS,
                        onConfirm = { copies ->
                            store.selectedCopies.intValue = copies.toIntOrNull() ?: 1
                            store.saveConfig()

                            val id = currentTrait?.printTemplateId
                            val templateName =
                                if (id != null) templateRepository.getTemplateName(id) else null
                            if (templateName != null) {
                                templateRepository.saveAssignments(templateName, currentAssignments)
                                val studyId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)
                                templateRepository.saveStudyAssignments(studyId, currentAssignments)
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
                        onConnectClick = { connectToPrinter() },
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
    private fun connectToPrinter() {
        val activity = mActivity ?: return
        if (!service.checkBluetoothPermissions(activity)) return

        if (!service.isBluetoothEnabled()) {
            activity.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        service.choosePrinter(
            context,
            object : com.fieldbook.tracker.utilities.BluetoothChooseCallback {
                override fun onDeviceChosen(deviceName: String) {
                    activity.runOnUiThread { refreshPrinterConnectionState() }
                }
            })
    }

    @SuppressLint("MissingPermission")
    private fun refreshPrinterConnectionState() {
        store.isPrinterConnected.value = service.isBluetoothEnabled() && !service.getSavedPrinterName().isNullOrEmpty()
    }

    private fun disconnectPrinter() {
        prefs.edit().remove(GeneralKeys.LABEL_PRINT_DEVICE_NAME).apply()
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
        val templateId = trait.printTemplateId
        if (templateId.isEmpty()) return

        val templateZpl = templateRepository.getTemplate(templateId) ?: return
        val templateName = templateRepository.getTemplateName(templateId) ?: return

        val dateString = LabelPrintService.currentDateString()
        val fieldName = prefs.getString(GeneralKeys.FIELD_FILE, "") ?: ""
        val dateLabel = context.getString(R.string.trait_layout_print_label_date_option)
        val blankLabel = context.getString(R.string.trait_layout_print_label_blank_option)
        val fieldNameLabel = context.getString(R.string.field_name_attribute)

        val rangeObject = currentRange ?: return
        val uniqueId = rangeObject.uniqueId ?: return

        val observationUnitAttributes =
            buildObservationUnitAttributes(uniqueId, dateLabel, blankLabel, fieldNameLabel)

        val studyId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)
        val assignments = templateRepository.getAssignments(templateName).toMutableMap().apply {
            putAll(templateRepository.getStudyAssignments(studyId))
        }

        val resolvedAssignments = assignments.mapValues { (_, fieldOption) ->
            LabelPrintService.resolveFieldValue(
                fieldOption, observationUnitAttributes,
                fieldName, dateString, blankLabel, dateLabel, fieldNameLabel,
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
        triggerTts(context.getString(R.string.trait_print_label_success))

        val plotId = collectActivity.getRangeBox().getPlotID() ?: return
        service.printLabels(context, labels, plotId, trait)
    }

    private fun buildPreviewLabel(): org.phenoapps.labelprint.zpl.ZplLabel? {
        val trait = currentTrait ?: return null
        val templateId = trait.printTemplateId
        if (templateId.isEmpty()) return null
        val templateZpl = templateRepository.getTemplate(templateId) ?: return null
        val templateName = templateRepository.getTemplateName(templateId) ?: return null

        val dateString = LabelPrintService.currentDateString()
        val fieldName = prefs.getString(GeneralKeys.FIELD_FILE, "") ?: ""
        val dateLabel = context.getString(R.string.trait_layout_print_label_date_option)
        val blankLabel = context.getString(R.string.trait_layout_print_label_blank_option)
        val fieldNameLabel = context.getString(R.string.field_name_attribute)

        val uniqueId = currentRange?.uniqueId
        val observationUnitAttributes = if (uniqueId != null) {
            buildObservationUnitAttributes(uniqueId, dateLabel, blankLabel, fieldNameLabel)
        } else emptyMap()

        val studyId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)
        val assignments = templateRepository.getAssignments(templateName).toMutableMap().apply {
            putAll(templateRepository.getStudyAssignments(studyId))
        }

        val resolvedAssignments = assignments.mapValues { (_, fieldOption) ->
            LabelPrintService.resolveFieldValue(
                fieldOption, observationUnitAttributes,
                fieldName, dateString, blankLabel, dateLabel, fieldNameLabel,
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
        dateLabel: String,
        blankLabel: String,
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
            if (option != dateLabel && option != blankLabel && option != fieldNameLabel
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
    }

    fun unregisterReceiver() {
        Log.d(TAG, "Unregistering printerMessageReceiver")
        LocalBroadcastManager.getInstance(context)
            .unregisterReceiver(printerMessageReceiver)
    }
}
