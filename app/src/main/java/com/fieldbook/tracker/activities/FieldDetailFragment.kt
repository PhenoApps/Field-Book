package com.fieldbook.tracker.activities

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.FieldDetailAdapter
import com.fieldbook.tracker.adapters.FieldDetailItem
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.dialogs.BrapiSyncObsDialog
import com.fieldbook.tracker.interfaces.FieldAdapterController
import com.fieldbook.tracker.interfaces.FieldSortController
import com.fieldbook.tracker.interfaces.FieldSyncController
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.objects.ImportFormat
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.utilities.ExportUtil
import com.fieldbook.tracker.utilities.FileUtil
import com.fieldbook.tracker.utilities.SemanticDateUtil
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import dagger.hilt.android.AndroidEntryPoint
import pub.devrel.easypermissions.EasyPermissions
import javax.inject.Inject


@AndroidEntryPoint
class FieldDetailFragment : Fragment(), FieldSyncController {

    @Inject
    lateinit var database: DataHelper

    @Inject
    lateinit var preferences: SharedPreferences

    private var toolbar: Toolbar? = null
    private var fieldId: Int? = null
    private val PERMISSIONS_REQUEST_TRAIT_DATA = 9950

    private lateinit var exportUtil: ExportUtil
    private lateinit var rootView: View
    private lateinit var fieldDisplayNameTextView: TextView
    private lateinit var importDateTextView: TextView
    private lateinit var lastEditTextView: TextView
    private lateinit var lastExportTextView: TextView
    private lateinit var lastSyncTextView: TextView
    private lateinit var cardViewCollect: CardView
    private lateinit var cardViewExport: CardView
    private lateinit var cardViewSync: CardView
    private lateinit var sourceChip: Chip
    private lateinit var originalNameChip: Chip
    private lateinit var entryCountChip: Chip
    private lateinit var attributeCountChip: Chip
    private lateinit var sortOrderChip: Chip
    private lateinit var traitCountChip: Chip
    private lateinit var observationCountChip: Chip
    private lateinit var trialNameChip: Chip
    private lateinit var detailRecyclerView: RecyclerView
    private var adapter: FieldDetailAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        Log.d("FieldDetailFragment", "onCreateView Start")
        rootView = inflater.inflate(R.layout.fragment_field_detail, container, false)
        toolbar = rootView.findViewById(R.id.toolbar)
        exportUtil = ExportUtil(requireActivity(), database)
        fieldDisplayNameTextView = rootView.findViewById(R.id.fieldDisplayName)
        importDateTextView = rootView.findViewById(R.id.importDateTextView)
        lastEditTextView = rootView.findViewById(R.id.lastEditTextView)
        lastExportTextView = rootView.findViewById(R.id.lastExportTextView)
        cardViewSync = rootView.findViewById(R.id.cardViewSync)
        lastSyncTextView = rootView.findViewById(R.id.lastSyncTextView)
        sourceChip = rootView.findViewById(R.id.sourceChip)
        originalNameChip = rootView.findViewById(R.id.originalNameChip)
        entryCountChip = rootView.findViewById(R.id.entryCountChip)
        attributeCountChip = rootView.findViewById(R.id.attributeCountChip)
        sortOrderChip = rootView.findViewById(R.id.sortOrderChip)
        traitCountChip = rootView.findViewById(R.id.traitCountChip)
        observationCountChip = rootView.findViewById(R.id.observationCountChip)
        detailRecyclerView = rootView.findViewById(R.id.fieldDetailRecyclerView)
        trialNameChip = rootView.findViewById(R.id.trialNameChip)

        fieldId = arguments?.getInt("fieldId")
        loadFieldDetails()

        val overviewExpandCollapseIcon: ImageView = rootView.findViewById(R.id.overview_expand_collapse_icon)
        val overviewCollapsibleContent: LinearLayout = rootView.findViewById(R.id.overview_collapsible_content)
        val overviewCollapsibleHeader: LinearLayout = rootView.findViewById(R.id.overview_collapsible_header)

        // Set collapse state based on saved pref
        val overviewIsCollapsed = preferences.getBoolean(GeneralKeys.FIELD_DETAIL_OVERVIEW_COLLAPSED, false)
        overviewCollapsibleContent.visibility = if (overviewIsCollapsed) View.GONE else View.VISIBLE
        overviewExpandCollapseIcon.setImageResource(if (overviewIsCollapsed) R.drawable.ic_chevron_down else R.drawable.ic_chevron_up)

        overviewCollapsibleHeader.setOnClickListener { v: View? ->
            if (overviewCollapsibleContent.visibility == View.GONE) {
                overviewCollapsibleContent.visibility = View.VISIBLE
                overviewExpandCollapseIcon.setImageResource(R.drawable.ic_chevron_up)
                preferences.edit().putBoolean(GeneralKeys.FIELD_DETAIL_OVERVIEW_COLLAPSED, false).apply()
            } else {
                overviewCollapsibleContent.visibility = View.GONE
                overviewExpandCollapseIcon.setImageResource(R.drawable.ic_chevron_down)
                preferences.edit().putBoolean(GeneralKeys.FIELD_DETAIL_OVERVIEW_COLLAPSED, true).apply()
            }
        }

        cardViewCollect = rootView.findViewById(R.id.cardViewCollect)
        cardViewExport = rootView.findViewById(R.id.cardViewExport)

        cardViewCollect.setOnClickListener {
            fieldId?.let { id ->
                if (checkTraitsExist() >= 0) {
                    (activity as? FieldEditorActivity)?.setActiveField(id)
                    collectDataFilePermission()
                }
            } ?: Log.e("FieldDetailFragment", "Field ID is null, cannot collect data")
        }

        cardViewExport.setOnClickListener {
            fieldId?.let { id ->
                if (checkTraitsExist() >= 0) {
                    exportUtil.exportMultipleFields(listOf(id))
                }
            } ?: Log.e("FieldDetailFragment", "Field ID is null, cannot export data")
        }

        val dataExpandCollapseIcon: ImageView = rootView.findViewById(R.id.data_expand_collapse_icon)
        val dataCollapsibleContent: LinearLayout = rootView.findViewById(R.id.data_collapsible_content)
        val dataCollapsibleHeader: LinearLayout = rootView.findViewById(R.id.data_collapsible_header)

        // Set collapse state based on saved pref
        val dataIsCollapsed = preferences.getBoolean(GeneralKeys.FIELD_DETAIL_DATA_COLLAPSED, false)
        dataCollapsibleContent.visibility = if (dataIsCollapsed) View.GONE else View.VISIBLE
        dataExpandCollapseIcon.setImageResource(if (dataIsCollapsed) R.drawable.ic_chevron_down else R.drawable.ic_chevron_up)

        dataCollapsibleHeader.setOnClickListener { v: View? ->
            if (dataCollapsibleContent.visibility == View.GONE) {
                dataCollapsibleContent.visibility = View.VISIBLE
                dataExpandCollapseIcon.setImageResource(R.drawable.ic_chevron_up)
                preferences.edit().putBoolean(GeneralKeys.FIELD_DETAIL_DATA_COLLAPSED, false).apply()
            } else {
                dataCollapsibleContent.visibility = View.GONE
                dataExpandCollapseIcon.setImageResource(R.drawable.ic_chevron_down)
                preferences.edit().putBoolean(GeneralKeys.FIELD_DETAIL_DATA_COLLAPSED, true).apply()
            }
        }

        // Add click listeners for rename and sort chips
        originalNameChip.setOnClickListener {
            fieldId?.let { id ->
                val field = database.getFieldObject(id)
                field?.let {
                    showEditDisplayNameDialog(it)
                }
            }
        }

        sortOrderChip.setOnClickListener {
            fieldId?.let { id ->
                val field = database.getFieldObject(id)
                field?.let {
                    (activity as? FieldSortController)?.showSortDialog(it)
                }
            }
        }

        disableDataChipRipples()

        Log.d("FieldDetailFragment", "onCreateView End")
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rootView.setOnTouchListener { v, event ->
            // Consume touch event to prevent propagation to FieldEditor RecyclerView
            true
        }
    }

    override fun onResume() {
        super.onResume()
        loadFieldDetails()
    }

    override fun onSyncComplete() {
        loadFieldDetails()
    }

    override fun startSync(field: FieldObject) {
        val syncDialog = BrapiSyncObsDialog(requireActivity(), this, field)
        syncDialog.show()
    }

    private fun disableDataChipRipples() {
        // Intercept data card touch events to prevent chip ripple but still trigger expand/collapse

        val chipGroup: ChipGroup = rootView.findViewById(R.id.dataChipGroup)
        chipGroup.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                rootView.findViewById<View>(R.id.data_collapsible_header).performClick()
            }
            true
        }

        rootView.findViewById<View>(R.id.data_collapsible_header).setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                v.performClick()
            }
            true
        }
    }

    fun loadFieldDetails() {
        fieldId?.let { id ->
            val field = database.getFieldObject(id)
            updateFieldData(field)
            if (detailRecyclerView.adapter == null) { // initial load
                detailRecyclerView.layoutManager = LinearLayoutManager(context)
                val initialItems = createTraitDetailItems(field).toMutableList()
                adapter = FieldDetailAdapter(initialItems)
                detailRecyclerView.adapter = adapter
                setupToolbar(field)
            } else { // reload after data change
                val newItems = createTraitDetailItems(field)
                adapter?.updateItems(newItems)
            }
        } ?: Log.e("FieldDetailFragment", "Field ID is null")
    }

    private fun updateFieldData(field: FieldObject) {

        cardViewSync.visibility = View.GONE
        cardViewSync.setOnClickListener(null)

        fieldDisplayNameTextView.text = field.exp_alias
        val importDate = field.date_import
        if (!importDate.isNullOrEmpty()) {
            importDateTextView.text = SemanticDateUtil.getSemanticDate(requireContext(), importDate)
        }

        val expSource = field.exp_source ?: "${field.exp_name}.csv"
        var importFormat: ImportFormat? = field.import_format
        var entryCount = field.count.toString()
        val attributeCount = field.attribute_count.toString()

        if (importFormat == ImportFormat.BRAPI) {
            cardViewSync.visibility = View.VISIBLE
            cardViewSync.setOnClickListener {
                if (preferences.getBoolean(PreferenceKeys.BRAPI_ENABLED, false)) {
                    if (BrAPIService.checkMatchBrapiUrl(requireContext(), field.exp_source)) {
                        startSync(field)
                    } else {
                        showWrongSourceDialog(field)
                    }
                } else {
                    Toast.makeText(context, getString(R.string.brapi_enable_before_sync), Toast.LENGTH_LONG).show()
                }
            }
            entryCount = "${entryCount} ${field.observation_level}"

            trialNameChip.visibility = View.GONE
            trialNameChip.text = field.trial_name
            if (trialNameChip.text.isNotBlank()) {
                trialNameChip.visibility = View.VISIBLE
            }
        }

//        val sortOrder = field.exp_sort.takeIf { !it.isNullOrBlank() } ?: getString(R.string.field_default_sort_order)

        sourceChip.text = expSource
        originalNameChip.text = getString(R.string.fields_rename_study)
        entryCountChip.text = entryCount
        attributeCountChip.text = attributeCount
        sortOrderChip.text = getString(R.string.field_sort_entries)

        val lastEdit = field.date_edit
        if (!lastEdit.isNullOrEmpty()) {
            lastEditTextView.text = SemanticDateUtil.getSemanticDate(requireContext(), lastEdit)
        } else {
            getString(R.string.no_activity)
        }

        val lastExport = field.date_export
        if (!lastExport.isNullOrEmpty()) {
            lastExportTextView.text = SemanticDateUtil.getSemanticDate(requireContext(), lastExport)
        } else {
            getString(R.string.no_activity)
        }

        val lastSync = field.date_sync
        if (!lastSync.isNullOrEmpty()) {
            lastSyncTextView.text = SemanticDateUtil.getSemanticDate(requireContext(), lastSync)
        } else {
            getString(R.string.no_activity)
        }

        traitCountChip.text = field.trait_count.toString()
        if (field.observation_count.toInt() > 0) {
            observationCountChip.visibility = View.VISIBLE
            observationCountChip.text = field.observation_count.toString()
        } else {
            observationCountChip.visibility = View.GONE
        }

    }

    private fun createTraitDetailItems(field: FieldObject): List<FieldDetailItem> {
        field.getTraitDetails()?.let { traitDetails ->
            return traitDetails.map { traitDetail ->
                val iconRes = Formats.entries
                    .find { it.getDatabaseName() == traitDetail.format }?.getIcon()

                FieldDetailItem(
                    traitDetail.traitName,
                    traitDetail.format,
                    traitDetail.categories,
                    getString(R.string.field_trait_observation_total, traitDetail.count),
                    ContextCompat.getDrawable(requireContext(), iconRes ?: R.drawable.ic_trait_categorical),
                    traitDetail.observations,
                    traitDetail.completeness
                )
            }
        }
        return emptyList()
    }


    private fun setupToolbar(field: FieldObject) {

        toolbar?.inflateMenu(R.menu.menu_field_details)

        toolbar?.setTitle(R.string.field_detail_title)

        toolbar?.setNavigationIcon(R.drawable.arrow_left)

        toolbar?.setNavigationOnClickListener {

            parentFragmentManager.popBackStack()
        }

        toolbar?.setOnMenuItemClickListener { item ->

            when (item.itemId) {
                android.R.id.home -> {
                    parentFragmentManager.popBackStack()
                }
                R.id.delete -> {
                    (activity as? FieldEditorActivity)?.showDeleteConfirmationDialog(listOf(field.exp_id), true)
                }
            }

            true
        }
    }

    private fun showWrongSourceDialog(field: FieldObject) {
        val builder = AlertDialog.Builder(requireContext(), R.style.AppAlertDialog)
            .setTitle(getString(R.string.brapi_field_non_matching_sources_title))
            .setMessage(String.format(getString(R.string.brapi_field_non_matching_sources), field.exp_source, BrAPIService.getHostUrl(context)))
            .setPositiveButton(getString(R.string.dialog_ok)) { d, _ ->
                d.dismiss()
            }

        builder.create().show()
    }

    private fun showEditDisplayNameDialog(field: FieldObject) {
        val inflater = requireActivity().layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_field_edit_name, null)

        val editText = dialogView.findViewById<EditText>(R.id.edit_text)
        val errorMessageView = dialogView.findViewById<TextView>(R.id.error_message)
        editText.setText(field.exp_alias)

        val builder = AlertDialog.Builder(requireContext(), R.style.AppAlertDialog)
            .setTitle(getString(R.string.field_edit_display_name))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.dialog_save), null) // Custom handling later
            .setNegativeButton(getString(R.string.dialog_cancel), null) // Default dismiss action

        val dialog = builder.create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val newName = editText.text.toString()

                if (newName.isNotBlank()) {
                    val illegalCharactersMessage = FileUtil.checkForIllegalCharacters(newName)
                    if (illegalCharactersMessage.isEmpty()) {
                        val nameCheckResult = nameUniquenessCheck(newName, field.exp_id)
                        if (nameCheckResult.isUnique) {
                            database.updateStudyAlias(field.exp_id, newName)
                            fieldDisplayNameTextView.text = newName
                            field.exp_alias = newName
                            (activity as? FieldAdapterController)?.queryAndLoadFields()
                            dialog.dismiss() // Only dismiss if everything is fine
                        } else {
                            val conflictType = if (nameCheckResult.conflictType == "name") getString(R.string.name_conflict_import_name) else getString(R.string.name_conflict_display_name)
                            showErrorMessage(errorMessageView, getString(R.string.name_conflict_message, newName, conflictType))
                        }
                    } else {
                        showErrorMessage(errorMessageView, getString(R.string.illegal_characters_message, illegalCharactersMessage))
                    }
                } else {
                    showErrorMessage(errorMessageView, getString(R.string.name_cannot_be_empty))
                }
            }
        }

        dialog.show()
    }

    /**
     * Checks if the given newName is unique among all fields, considering both import names and aliases.
     */

    private fun nameUniquenessCheck(newName: String, currentFieldId: Int): NameCheckResult {
        database.getAllFieldObjects().let { fields ->
            fields.firstOrNull { it.exp_id != currentFieldId && (it.exp_name == newName || it.exp_alias == newName) }?.let { field ->
                val conflictType = if (field.exp_name == newName) "name" else "alias"
                return NameCheckResult(isUnique = false, conflictType = conflictType)
            }
        }
        return NameCheckResult(isUnique = true)
    }

    data class NameCheckResult(val isUnique: Boolean, val conflictType: String? = null)

    private fun showErrorMessage(messageView: TextView, message: String) {
        messageView.text = message
        messageView.visibility = View.VISIBLE
    }

    fun checkTraitsExist(): Int {
        val traits = database.getVisibleTrait()

        return when {
            traits.isEmpty() -> {
                Toast.makeText(context, R.string.warning_traits_missing, Toast.LENGTH_SHORT).show()
                -1
            }
            else -> 1
        }
    }

    fun collectDataFilePermission() {
        var perms = arrayOf<String?>(
            Manifest.permission.VIBRATE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            perms = arrayOf(
                Manifest.permission.VIBRATE,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CAMERA
            )
        }
        if (EasyPermissions.hasPermissions(requireActivity(), *perms)) {
            startCollectActivity()
        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(
                this, getString(R.string.permission_rationale_trait_features),
                PERMISSIONS_REQUEST_TRAIT_DATA, *perms
            )
        }
    }

    private fun startCollectActivity() {
        val selectedField = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, -1)
        val field = database.getFieldObject(selectedField)

        if (field != null && field.date_import != null && field.date_import.isNotEmpty()) {
            val intent = Intent(context, CollectActivity::class.java)
            startActivity(intent)
        }
    }

}
