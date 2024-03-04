package com.fieldbook.tracker.activities

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
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
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.FieldDetailAdapter
import com.fieldbook.tracker.adapters.FieldDetailItem
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.dialogs.BrapiSyncObsDialog
import com.fieldbook.tracker.interfaces.FieldAdapterController
import com.fieldbook.tracker.interfaces.FieldSortController
import com.fieldbook.tracker.interfaces.FieldSyncController
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.objects.ImportFormat
import com.fieldbook.tracker.offbeat.traits.formats.Formats
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.ExportUtil
import com.fieldbook.tracker.utilities.FileUtil
import com.fieldbook.tracker.utilities.SemanticDateUtil
import com.fieldbook.tracker.utilities.StringUtil
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
    private lateinit var fieldNarrativeTextView: TextView
    private lateinit var lastEditTextView: TextView
    private lateinit var lastExportTextView: TextView
    private lateinit var traitCountTextView: TextView
    private lateinit var observationCountTextView: TextView
    private lateinit var lastSyncTextView: TextView
    private lateinit var cardViewCollect: CardView
    private lateinit var cardViewExport: CardView
    private lateinit var cardViewSync: CardView
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
        fieldNarrativeTextView = rootView.findViewById(R.id.fieldNarrativeTextView)
        lastEditTextView = rootView.findViewById(R.id.lastEditTextView)
        lastExportTextView = rootView.findViewById(R.id.lastExportTextView)
        traitCountTextView = rootView.findViewById(R.id.traitCountTextView)
        observationCountTextView = rootView.findViewById(R.id.observationCountTextView)
        cardViewSync = rootView.findViewById(R.id.cardViewSync)
        lastSyncTextView = rootView.findViewById(R.id.lastSyncTextView)
        detailRecyclerView = rootView.findViewById(R.id.fieldDetailRecyclerView)

        fieldId = arguments?.getInt("fieldId")
        loadFieldDetails()

        val expandCollapseIcon: ImageView = rootView.findViewById(R.id.expand_collapse_icon)
        val collapsibleContent: LinearLayout = rootView.findViewById(R.id.collapsible_content)
        val collapsibleHeader: LinearLayout = rootView.findViewById(R.id.collapsible_header)

        // Set collapse state based on saved pref
        val isCollapsed = preferences.getBoolean(GeneralKeys.FIELD_DETAIL_COLLAPSED, false)
        collapsibleContent.visibility = if (isCollapsed) View.GONE else View.VISIBLE
        expandCollapseIcon.setImageResource(if (isCollapsed) R.drawable.ic_chevron_down else R.drawable.ic_chevron_up)

        collapsibleHeader.setOnClickListener { v: View? ->
            if (collapsibleContent.visibility == View.GONE) {
                collapsibleContent.visibility = View.VISIBLE
                expandCollapseIcon.setImageResource(R.drawable.ic_chevron_up)
                preferences.edit().putBoolean(GeneralKeys.FIELD_DETAIL_COLLAPSED, false).apply()
            } else {
                collapsibleContent.visibility = View.GONE
                expandCollapseIcon.setImageResource(R.drawable.ic_chevron_down)
                preferences.edit().putBoolean(GeneralKeys.FIELD_DETAIL_COLLAPSED, true).apply()
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
                    (activity as? FieldEditorActivity)?.setActiveField(id)
                    exportUtil.exportActiveField()
                }
            } ?: Log.e("FieldDetailFragment", "Field ID is null, cannot export data")
        }

        Log.d("FieldDetailFragment", "onCreateView End")
        return rootView
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

        var source_prefix = getString(R.string.field_import_string)
        var exp_source: String? = field.exp_source
        if (exp_source == getString(R.string.field_book)) {
            source_prefix = getString(R.string.field_create_string)
        } else if (exp_source.isNullOrEmpty()) { // Sample file import
            exp_source = field.exp_name + ".csv"
        }

        var importFormat: ImportFormat? = field.import_format
        var entryCount = field.count.toString()

        if (importFormat == ImportFormat.BRAPI) {
            cardViewSync.visibility = View.VISIBLE
            cardViewSync.setOnClickListener {
                startSync(field)
            }
            entryCount = "${entryCount} ${field.observation_level}"
        }

        val sortOrder =
            if (field.exp_sort.isNullOrEmpty()) getString(R.string.field_default_sort_order) else field.exp_sort

        val narrativeTemplate = getString(
            R.string.field_detail_narrative,
            source_prefix,
            exp_source,
            field.exp_name,
            entryCount,
            field.attribute_count.toString(),
            sortOrder
        )
        val narrativeSpannable = StringUtil.applyBoldStyleToString(
            narrativeTemplate,
            source_prefix,
            exp_source,
            field.exp_name,
            entryCount,
            field.attribute_count.toString(),
            sortOrder
        )
        fieldNarrativeTextView.text = narrativeSpannable

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

        val traitString = getString(R.string.field_trait_total, field.trait_count)
        val observationString =
            getString(R.string.field_observation_total, field.observation_count)
        traitCountTextView.text =
            HtmlCompat.fromHtml(traitString, HtmlCompat.FROM_HTML_MODE_LEGACY)
        observationCountTextView.text =
            HtmlCompat.fromHtml(observationString, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    private fun createTraitDetailItems(field: FieldObject): List<FieldDetailItem> {
        field.getTraitDetails()?.let { traitDetails ->
            return traitDetails.map { traitDetail ->
                val iconRes = Formats.values()
                    .find { it.getDatabaseName(requireActivity()) == traitDetail.getFormat() }?.getIcon()

                FieldDetailItem(
                    traitDetail.getTraitName(),
                    getString(R.string.field_trait_observation_total, traitDetail.getCount()),
                    ContextCompat.getDrawable(requireContext(), iconRes ?: R.drawable.ic_trait_categorical)
                )
            }
        }
        return emptyList()  // Return an empty list if traitDetails is null
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
                R.id.rename -> {
                    showEditDisplayNameDialog(field)
                }
                R.id.sort -> {
                    (activity as? FieldSortController)?.showSortDialog(field)
                }
                R.id.delete -> {
                    (activity as? FieldEditorActivity)?.showDeleteConfirmationDialog(listOf(field.exp_id), true)
                }
            }

            true
        }
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
            val intent = Intent()
            intent.setClassName(
                requireActivity(),
                "com.fieldbook.tracker.activities.CollectActivity"
            )
            startActivity(intent)
        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(
                this, getString(R.string.permission_rationale_trait_features),
                PERMISSIONS_REQUEST_TRAIT_DATA, *perms
            )
        }
    }

}
