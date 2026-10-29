package com.fieldbook.tracker.activities

import android.Manifest
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.FieldDetailAdapter
import com.fieldbook.tracker.adapters.FieldDetailItem
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.dialogs.SearchAttributeChooserDialog
import com.fieldbook.tracker.interfaces.FieldSortController
import com.fieldbook.tracker.interfaces.FieldSyncController
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.objects.ImportFormat
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.utilities.export.ExportUtil
import com.fieldbook.tracker.utilities.DateJsonUtil
import com.fieldbook.tracker.utilities.FileUtil
import com.fieldbook.tracker.utilities.DateResult
import com.fieldbook.tracker.utilities.SemanticDateUtil
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pub.devrel.easypermissions.EasyPermissions
import javax.inject.Inject
import androidx.core.view.isGone
import androidx.core.content.edit
import com.fieldbook.tracker.activities.brapi.BrapiAuthActivity
import com.fieldbook.tracker.activities.brapi.io.sync.BrapiAutoSyncManager
import com.fieldbook.tracker.activities.brapi.io.sync.BrapiAutoSyncManager.AutoSyncStatus
import com.fieldbook.tracker.activities.brapi.io.sync.BrapiSyncActivity
import com.fieldbook.tracker.activities.brapi.io.sync.BrapiSyncEngine
import com.fieldbook.tracker.utilities.InsetHandler
import com.fieldbook.tracker.utilities.export.ValueProcessorFormatAdapter

@AndroidEntryPoint
class FieldDetailFragment : Fragment(), FieldSyncController {

    companion object {
        const val PERMISSIONS_REQUEST_TRAIT_DATA = 9950
        private const val AUTO_SYNC_REQUESTED = "auto_sync_requested"
    }

    @Inject
    lateinit var database: DataHelper

    @Inject
    lateinit var preferences: SharedPreferences

    @Inject
    lateinit var exportUtil: ExportUtil

    @Inject
    lateinit var valueProcessor: ValueProcessorFormatAdapter

    @Inject
    lateinit var syncEngine: BrapiSyncEngine

    @Inject
    lateinit var autoSyncManager: BrapiAutoSyncManager

    private var toolbar: Toolbar? = null
    private var fieldId: Int? = null
    private var fieldObject: FieldObject? = null

    private lateinit var rootView: View
    private lateinit var fieldDisplayNameTextView: TextView
    private lateinit var importDateTextView: TextView
    private lateinit var lastEditTextView: TextView
    private lateinit var lastExportTextView: TextView
    private lateinit var lastSyncTextView: TextView
    private lateinit var syncIconImageView: ImageView
    private var syncIconAnimator: ObjectAnimator? = null
    private lateinit var cardViewCollect: CardView
    private lateinit var cardViewExport: CardView
    private lateinit var cardViewSync: CardView
    private lateinit var sourceChip: Chip
    private lateinit var originalNameChip: Chip
    private lateinit var entryCountChip: Chip
    private lateinit var attributeCountChip: Chip
    private lateinit var sortOrderChip: Chip
    private lateinit var editUniqueChip: Chip
    private lateinit var dataSummaryTextView: TextView
    private lateinit var trialNameChip: Chip
    private lateinit var studyGroupNameChip: Chip
    private lateinit var detailRecyclerView: RecyclerView
    private var adapter: FieldDetailAdapter? = null
    private var fieldDetailsJob: Job? = null

    // Observations not on the server yet, null until counted or when the field cannot sync here.
    private var unsyncedCounts: BrapiSyncEngine.UnsyncedCounts? = null

    // Sync on open fires once per visit, not on every resume or rotation.
    private var autoSyncRequested = false

    private val brapiSyncIntentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // The sync screen resolves what an automatic sync left for review, so drop its note.
        fieldId?.let { autoSyncManager.clearStatus(it) }
    }

    // Started from the sync card when the last automatic sync was rejected for authorization.
    private val brapiAuthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

        val id = fieldId ?: return@registerForActivityResult
        autoSyncManager.clearStatus(id)

        // The user tapped the card to sync, so carry on with that sync now they are logged in.
        if (autoSyncManager.canSync()) {
            autoSyncManager.requestSync(id)
        } else {
            renderSyncStatus()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        rootView = inflater.inflate(R.layout.fragment_field_detail, container, false)
        toolbar = rootView.findViewById(R.id.toolbar)
        fieldDisplayNameTextView = rootView.findViewById(R.id.fieldDisplayName)
        importDateTextView = rootView.findViewById(R.id.importDateTextView)
        lastEditTextView = rootView.findViewById(R.id.lastEditTextView)
        lastExportTextView = rootView.findViewById(R.id.lastExportTextView)
        cardViewSync = rootView.findViewById(R.id.cardViewSync)
        lastSyncTextView = rootView.findViewById(R.id.lastSyncTextView)
        syncIconImageView = rootView.findViewById(R.id.syncIconImageView)
        sourceChip = rootView.findViewById(R.id.sourceChip)
        originalNameChip = rootView.findViewById(R.id.originalNameChip)
        entryCountChip = rootView.findViewById(R.id.entryCountChip)
        attributeCountChip = rootView.findViewById(R.id.attributeCountChip)
        sortOrderChip = rootView.findViewById(R.id.sortOrderChip)
        editUniqueChip = rootView.findViewById(R.id.editUniqueChip)
        dataSummaryTextView = rootView.findViewById(R.id.dataSummaryTextView)
        detailRecyclerView = rootView.findViewById(R.id.fieldDetailRecyclerView)
        trialNameChip = rootView.findViewById(R.id.trialNameChip)
        studyGroupNameChip = rootView.findViewById(R.id.studyGroupName)

        // The details are not loaded here. onResume() always follows onCreateView() and loads them
        // itself, so doing it in both places ran the whole query and chart pipeline twice on open.
        fieldId = arguments?.getInt(GeneralKeys.FIELD_DETAIL_FIELD_ID)

        val overviewExpandCollapseIcon: ImageView = rootView.findViewById(R.id.overview_expand_collapse_icon)
        val overviewCollapsibleContent: LinearLayout = rootView.findViewById(R.id.overview_collapsible_content)
        val overviewCollapsibleHeader: LinearLayout = rootView.findViewById(R.id.overview_collapsible_header)

        // Set collapse state based on saved pref
        val overviewIsCollapsed = preferences.getBoolean(GeneralKeys.FIELD_DETAIL_OVERVIEW_COLLAPSED, false)
        overviewCollapsibleContent.visibility = if (overviewIsCollapsed) View.GONE else View.VISIBLE
        overviewExpandCollapseIcon.setImageResource(if (overviewIsCollapsed) R.drawable.ic_chevron_down else R.drawable.ic_chevron_up)

        overviewCollapsibleHeader.setOnClickListener { v: View? ->
            if (overviewCollapsibleContent.isGone) {
                overviewCollapsibleContent.visibility = View.VISIBLE
                overviewExpandCollapseIcon.setImageResource(R.drawable.ic_chevron_up)
                preferences.edit { putBoolean(GeneralKeys.FIELD_DETAIL_OVERVIEW_COLLAPSED, false) }
            } else {
                overviewCollapsibleContent.visibility = View.GONE
                overviewExpandCollapseIcon.setImageResource(R.drawable.ic_chevron_down)
                preferences.edit { putBoolean(GeneralKeys.FIELD_DETAIL_OVERVIEW_COLLAPSED, true) }
            }
        }

        cardViewCollect = rootView.findViewById(R.id.cardViewCollect)
        cardViewExport = rootView.findViewById(R.id.cardViewExport)

        cardViewCollect.setOnClickListener {
            fieldId?.let { id ->
                checkTraitsExist { result ->
                    if (result >= 0) {
                        if (fieldObject?.archived == true) {
                            showUnarchiveDialog() // give a warning for archived fields
                        } else {
                            setAsActiveField()
                        }
                    }
                }
            } ?: Log.e("FieldDetailFragment", "Field ID is null, cannot collect data")
        }

        cardViewExport.setOnClickListener {
            fieldId?.let { id ->
                checkTraitsExist { result ->
                    if (result >= 0) {
                        exportUtil.exportMultipleFields(listOf(id))
                    }
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
            if (dataCollapsibleContent.isGone) {
                dataCollapsibleContent.visibility = View.VISIBLE
                dataExpandCollapseIcon.setImageResource(R.drawable.ic_chevron_up)
                preferences.edit { putBoolean(GeneralKeys.FIELD_DETAIL_DATA_COLLAPSED, false) }
            } else {
                dataCollapsibleContent.visibility = View.GONE
                dataExpandCollapseIcon.setImageResource(R.drawable.ic_chevron_down)
                preferences.edit { putBoolean(GeneralKeys.FIELD_DETAIL_DATA_COLLAPSED, true) }
            }
        }

        originalNameChip.setOnClickListener {
            fieldObject?.let { field ->
                showEditDisplayNameDialog(field)
            }
        }

        sortOrderChip.setOnClickListener {
            fieldObject?.let { field ->
                (activity as? FieldSortController)?.showSortDialog(field)
            }
        }

        editUniqueChip.setOnClickListener {
            fieldObject?.let { field ->
                showChangeSearchAttributeDialog(field)
            }
        }

        InsetHandler.setupFragmentWithTopInsetsOnly(rootView, toolbar)
        return rootView
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        autoSyncRequested = savedInstanceState?.getBoolean(AUTO_SYNC_REQUESTED, false) ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(AUTO_SYNC_REQUESTED, autoSyncRequested)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rootView.setOnTouchListener { v, event ->
            // Consume touch event to prevent propagation to FieldEditor/FieldArchived RecyclerView
            true
        }

        // Follows automatic syncs of this field, including one still running from an earlier visit.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var previous: AutoSyncStatus? = null
                autoSyncManager.status
                    .map { statuses -> fieldId?.let { statuses[it] } }
                    .distinctUntilChanged()
                    .collect { status ->
                        // A sync that finished while this screen was stopped is picked up by the
                        // reload in onResume, only one seen finishing here needs its own reload.
                        if (status is AutoSyncStatus.Done && previous is AutoSyncStatus.Running) {
                            loadFieldDetails()
                        } else {
                            renderSyncStatus()
                        }
                        previous = status
                    }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadFieldDetails()
    }

    override fun onDestroyView() {
        setSyncIconSpinning(false)
        super.onDestroyView()
    }

    override fun onSyncComplete() {
        loadFieldDetails()
    }

    override fun startSync(field: FieldObject) {
        activity?.runOnUiThread {
            val fieldId = field.studyId
            brapiSyncIntentLauncher.launch(
                Intent(context, BrapiSyncActivity::class.java)
                    .putIntegerArrayListExtra(BrapiSyncActivity.FIELD_IDS, arrayListOf(fieldId))
            )
        }
    }

    /**
     * Loads the screen in two stages so the header card does not wait on the trait data.
     *
     * The study row and its counts are a cheap read, while the trait details carry every
     * observation value in the field. Loading both before drawing anything meant the name, dates
     * and chips appeared only once the expensive half had finished.
     */
    fun loadFieldDetails() {
        val id = fieldId
        if (id == null) {
            Log.e("FieldDetailFragment", "Field ID is null")
            return
        }

        // Both are resolved here rather than inside the coroutine: the load can outlive the
        // fragment's view, and requireContext()/viewLifecycleOwner throw once that happens.
        val context = context ?: return
        val lifecycleOwner = viewLifecycleOwnerLiveData.value ?: return

        // Overlapping loads must not interleave. onResume and a sync completing can both land
        // here, and without this the slow stage of the earlier load could arrive last and leave
        // stale traits on screen.
        fieldDetailsJob?.cancel()
        fieldDetailsJob = lifecycleOwner.lifecycleScope.launch {

            val (field, studyGroupName) = withContext(Dispatchers.IO) {
                val study = database.getFieldObject(id, false)
                study to study?.let { database.getStudyGroupNameById(it.groupId) }
            }

            fieldObject = field  // Store the field object
            if (field == null) return@launch

            // Stage one is on screen here: header, chips and toolbar are populated and the card
            // actions are usable while the observations are still being read.
            updateFieldData(field, studyGroupName)
            setupToolbar(field)
            requestAutoSync(field, context)

            // Counted alongside stage two, it reads the same observations the sync screen does.
            // A failed count only leaves the card without it, it must not cancel the whole load.
            val unsyncedDeferred = if (canShowSyncStatus(field, context)) {
                async(Dispatchers.IO) {
                    try {
                        syncEngine.countUnsynced(id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("FieldDetailFragment", "Failed to count unsynced observations", e)
                        null
                    }
                }
            } else {
                null
            }

            // Stage two: the observation values, and the per-trait processing they feed.
            val items = withContext(Dispatchers.IO) {
                createTraitDetailItems(database.getTraitDetailsForStudy(id), context)
            }

            if (detailRecyclerView.adapter == null) { // initial load
                detailRecyclerView.layoutManager = LinearLayoutManager(context)
                adapter = FieldDetailAdapter(items.toMutableList())
                detailRecyclerView.adapter = adapter
            } else { // reload after data change
                adapter?.updateItems(items)
            }

            unsyncedCounts = unsyncedDeferred?.await()
            renderSyncStatus()
        }
    }

    /**
     * The unsynced count only means something for a BrAPI field from the configured server. For
     * any other, every observation is categorized as from the wrong source and the count would be
     * a misleading zero.
     */
    private fun canShowSyncStatus(field: FieldObject, context: Context): Boolean =
        field.dataSourceFormat == ImportFormat.BRAPI
                && preferences.getBoolean(PreferenceKeys.BRAPI_ENABLED, false)
                && BrAPIService.checkMatchBrapiUrl(context, field.dataSource)

    private fun requestAutoSync(field: FieldObject, context: Context) {

        if (autoSyncRequested) return
        if (!preferences.getBoolean(PreferenceKeys.BRAPI_SYNC_ON_FIELD_OPEN, false)) return
        if (!canShowSyncStatus(field, context) || !autoSyncManager.canSync()) return

        autoSyncRequested = true
        autoSyncManager.requestSync(field.studyId)
    }

    /**
     * The sync card's details line and icon, as a single sentence. Something that needs the user,
     * a failed sync or observations left for review, takes priority over the unsynced count and
     * swaps the sync icon for an alert. While an automatic sync runs the icon spins instead.
     */
    private fun renderSyncStatus() {

        val context = context ?: return
        val field = fieldObject ?: return

        val lastSync = field.dateSync
        val date = if (!lastSync.isNullOrEmpty()) {
            SemanticDateUtil.getSemanticDate(context, lastSync)
        } else {
            getString(R.string.no_activity)
        }

        if (!canShowSyncStatus(field, context)) {
            setSyncIconSpinning(false)
            syncIconImageView.setImageResource(R.drawable.ic_field_sync)
            lastSyncTextView.text = date
            return
        }

        val status = autoSyncManager.status.value[field.studyId]
        val result = (status as? AutoSyncStatus.Done)?.result
        val count = unsyncedCounts?.sendable
        val unsupported = unsyncedCounts?.unsupported ?: 0

        val needsAttention = result != null && (result.error != null || result.needsReview > 0)

        setSyncIconSpinning(status is AutoSyncStatus.Running)
        syncIconImageView.setImageResource(
            if (needsAttention) R.drawable.ic_alert_rhombus else R.drawable.ic_field_sync
        )

        // What the last automatic sync left behind. It is a snapshot from that sync, so it is added
        // to the live count rather than replacing it, otherwise new data would never show up.
        val clause = when {
            result?.error != null -> getString(R.string.brapi_sync_clause_auto_failed)
            result != null && result.needsReview > 0 -> resources.getQuantityString(
                R.plurals.brapi_sync_clause_needs_review,
                result.needsReview,
                result.needsReview
            )
            else -> null
        }

        // Data the server does not accept is never synced, so it is named apart from the count of
        // what still can be rather than leaving the field looking unsynced forever.
        val server = preferences.getString(PreferenceKeys.BRAPI_DISPLAY_NAME, null) ?: "BrAPI"
        val unsupportedClause = if (unsupported > 0) {
            resources.getQuantityString(R.plurals.brapi_sync_clause_unsupported, unsupported, unsupported, server)
        } else {
            null
        }

        fun withUnsupported(text: String) = if (unsupportedClause == null) {
            text
        } else {
            getString(R.string.brapi_sync_status_with_clause, text, unsupportedClause)
        }

        lastSyncTextView.text = when {
            status is AutoSyncStatus.Running -> getString(R.string.brapi_sync_in_progress)
            // Tapping the card logs in while this shows, so it is never mixed with anything else.
            result?.error == BrapiSyncEngine.AutoSyncError.UNAUTHORIZED -> getString(R.string.brapi_error_unauthorized)
            count != null && count > 0 -> {
                val unsynced = resources.getQuantityString(R.plurals.brapi_sync_unsynced_count, count, count)
                withUnsupported(
                    if (clause == null) unsynced else getString(R.string.brapi_sync_status_with_clause, unsynced, clause)
                )
            }
            result?.error != null -> withUnsupported(getString(R.string.brapi_sync_auto_failed))
            result != null && result.needsReview > 0 -> withUnsupported(
                resources.getQuantityString(
                    R.plurals.brapi_sync_needs_review,
                    result.needsReview,
                    result.needsReview
                )
            )
            unsupported > 0 -> resources.getQuantityString(
                R.plurals.brapi_sync_unsupported_count,
                unsupported,
                unsupported,
                server
            )
            // Nothing left to upload only reads as synced once a sync has actually happened.
            count == 0 && !lastSync.isNullOrEmpty() -> allSyncedText(context, lastSync)
            else -> date
        }
    }

    /**
     * Whether the card is showing that the server rejected the last automatic sync's credentials.
     */
    private fun isAuthorizationRequired(field: FieldObject): Boolean {
        val status = autoSyncManager.status.value[field.studyId] as? AutoSyncStatus.Done
        return status?.result?.error == BrapiSyncEngine.AutoSyncError.UNAUTHORIZED
    }

    private fun allSyncedText(context: Context, lastSync: String): String {

        val semanticDate = SemanticDateUtil.calculateSemanticValue(lastSync)

        return when (semanticDate.result) {
            DateResult.TODAY -> getString(R.string.brapi_sync_all_synced_today)
            DateResult.YESTERDAY -> getString(R.string.brapi_sync_all_synced_yesterday)
            DateResult.THIS_YEAR -> getString(
                R.string.brapi_sync_all_synced_on,
                SemanticDateUtil.getSemanticDate(context, lastSync)
            )
            DateResult.YEARS_AGO -> resources.getQuantityString(
                R.plurals.brapi_sync_all_synced_years_ago,
                semanticDate.yearsAgo,
                semanticDate.yearsAgo
            )
            DateResult.INVALID_DATE -> getString(R.string.brapi_sync_all_synced)
        }
    }

    private fun setSyncIconSpinning(spinning: Boolean) {

        if (spinning) {

            if (syncIconAnimator?.isRunning == true) return

            // Counterclockwise, the direction the icon's arrows point.
            syncIconAnimator = ObjectAnimator.ofFloat(syncIconImageView, View.ROTATION, 0f, -360f).apply {
                duration = 1000L
                repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }

        } else {

            syncIconAnimator?.cancel()
            syncIconAnimator = null
            syncIconImageView.rotation = 0f
        }
    }

    private fun updateFieldData(field: FieldObject, studyGroupName: String?) {

        cardViewSync.visibility = View.GONE
        cardViewSync.setOnClickListener(null)

        fieldDisplayNameTextView.text = field.alias
        val importDate = field.dateImport
        if (!importDate.isNullOrEmpty()) {
            importDateTextView.text = SemanticDateUtil.getSemanticDate(requireContext(), importDate)
        }

        val expSource = field.dataSource ?: "${field.name}.csv"
        var importFormat: ImportFormat? = field.dataSourceFormat
        var entryCount = field.entryCount.toString()
        val attributeCount = field.attributeCount.toString()
        val searchAttribute = (field.searchAttribute ?: field.uniqueId).toString()

        if (importFormat == ImportFormat.BRAPI) {
            cardViewSync.visibility = View.VISIBLE
            cardViewSync.setOnClickListener {
                if (preferences.getBoolean(PreferenceKeys.BRAPI_ENABLED, false)) {
                    if (BrAPIService.checkMatchBrapiUrl(requireContext(), field.dataSource)) {
                        if (isAuthorizationRequired(field)) {
                            brapiAuthLauncher.launch(Intent(context, BrapiAuthActivity::class.java))
                        } else {
                            startSync(field)
                        }
                    } else {
                        showWrongSourceDialog(field)
                    }
                } else {
                    Toast.makeText(context, getString(R.string.brapi_enable_before_sync), Toast.LENGTH_LONG).show()
                }
            }
            entryCount = "$entryCount ${field.observationLevel}"

            trialNameChip.visibility = View.GONE
            trialNameChip.text = field.trialName
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
//        editUniqueChip.text = getString(R.string.field_edit_unique_id)
        editUniqueChip.text = searchAttribute

        val lastEdit = field.dateEdit
        lastEditTextView.text = if (!lastEdit.isNullOrEmpty()) {
            SemanticDateUtil.getSemanticDate(requireContext(), lastEdit)
        } else {
            getString(R.string.no_activity)
        }

        val lastExport = field.dateExport
        lastExportTextView.text = if (!lastExport.isNullOrEmpty()) {
            SemanticDateUtil.getSemanticDate(requireContext(), lastExport)
        } else {
            getString(R.string.no_activity)
        }

        renderSyncStatus()

        // Counts arrive as strings straight off the query, so they are parsed defensively rather
        // than with toInt(), which threw on a null or empty column.
        dataSummaryTextView.text = getString(
            R.string.field_data_summary,
            field.observationCount?.toIntOrNull() ?: 0,
            field.traitCount?.toIntOrNull() ?: 0
        )


        studyGroupNameChip.visibility = View.GONE
        if (!studyGroupName.isNullOrEmpty() && studyGroupName != field.trialName) {
            studyGroupNameChip.visibility = View.VISIBLE
            studyGroupNameChip.text = studyGroupName
        }
    }

    /**
     * Builds the recycler items for a field's traits. Runs off the main thread, so it takes the
     * context as a parameter instead of reaching for requireContext().
     */
    private fun createTraitDetailItems(
        traitDetails: List<FieldObject.TraitDetail>,
        context: Context
    ): List<FieldDetailItem> {
        return traitDetails.map { traitDetail ->
            val iconRes = Formats.entries
                .find { it.getDatabaseName() == traitDetail.format }?.getIcon()

            // Every observation in this group belongs to the same trait, so the trait is looked
            // up once instead of once per value. The lookup is not cheap either — it also loads
            // the trait's attribute values, making it two queries a call — so running it per
            // observation was what stalled fields with a lot of data.
            val trait = database.getTraitByName(traitDetail.traitName)

            val processedObservations =
                traitDetail.observations?.map { observation ->
                    trait?.let {
                        valueProcessor.processValue(observation, it)
                    } ?: observation
                }

            // Read from the stored values rather than the processed ones: the presenter reduces a
            // date to whichever single field the trait displays, so the day of year is gone by
            // then whenever the trait is set to show a formatted date.
            val dayOfYearValues = if (traitDetail.format == Formats.DATE.getDatabaseName()) {
                traitDetail.observations?.mapNotNull { DateJsonUtil.extractDayOfYear(it) }
            } else null

            FieldDetailItem(
                traitDetail.traitName,
                traitDetail.format,
                traitDetail.categories,
                context.getString(R.string.field_trait_observation_total, traitDetail.count),
                ContextCompat.getDrawable(context, iconRes ?: R.drawable.ic_trait_categorical),
                processedObservations,
                traitDetail.completeness,
                dayOfYearValues
            )
        }
    }


    private fun setupToolbar(field: FieldObject) {

        // Called on every load so the menu action closes over the current field rather than the
        // one from first load. Clearing first keeps the items from stacking up on each pass.
        toolbar?.menu?.clear()

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
                    (activity as? BaseFieldActivity)?.showDeleteConfirmationDialog(listOf(field.studyId), true)
                }
            }

            true
        }
    }

    private fun showWrongSourceDialog(field: FieldObject) {
        val builder = AlertDialog.Builder(requireContext(), R.style.AppAlertDialog)
            .setTitle(getString(R.string.brapi_field_non_matching_sources_title))
            .setMessage(String.format(getString(R.string.brapi_field_non_matching_sources), field.dataSource, BrAPIService.getHostUrl(context)))
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
        editText.setText(field.alias)

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
                        nameUniquenessCheck(newName, field.studyId) { result ->
                            if (result.isUnique) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    database.updateStudyAlias(field.studyId, newName)
                                    withContext(Dispatchers.Main) {
                                        fieldDisplayNameTextView.text = newName
                                        field.alias = newName
                                        (activity as? BaseFieldActivity)?.queryAndLoadFields()
                                        dialog.dismiss() // Only dismiss if everything is fine
                                    }
                                }
                            } else {
                                val conflictType = if (result.conflictType == "name") getString(R.string.name_conflict_import_name) else getString(R.string.name_conflict_display_name)
                                showErrorMessage(errorMessageView, getString(R.string.name_conflict_message, newName, conflictType))
                            }
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

    private fun showChangeSearchAttributeDialog(field: FieldObject) {
        (activity as? BaseFieldActivity)?.setActiveField(field.studyId)
        
        val dialog = SearchAttributeChooserDialog()
        dialog.setOnSearchAttributeSelectedListener(object : SearchAttributeChooserDialog.OnSearchAttributeSelectedListener {

            override fun onSearchAttributeSelected(label: String, applyToAll: Boolean) {
                CoroutineScope(Dispatchers.IO).launch {

                    val count = if (applyToAll) {
                        database.updateSearchAttributeForAllFields(label)
                    } else {
                        database.updateSearchAttribute(field.studyId, label)
                        -1 // Use -1 to indicate single field update
                    }
                    
                    withContext(Dispatchers.Main) {
                        if (applyToAll) {
                            Toast.makeText(
                                context,
                                getString(R.string.search_attribute_updated_all, count),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            // Update only the current field
                            Toast.makeText(
                                context,
                                getString(R.string.search_attribute_updated),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        
                        loadFieldDetails()
                        
                        // If apply to all was selected, refresh the parent activity's field list
                        if (applyToAll) {
                            (activity as? BaseFieldActivity)?.queryAndLoadFields()
                        }
                    }
                }
            }
        })
        
        dialog.show(parentFragmentManager, SearchAttributeChooserDialog.TAG)
    }

    /**
     * Checks if the given newName is unique among all fields, considering both import names and aliases.
     */

    private fun nameUniquenessCheck(newName: String, currentFieldId: Int, callback: (NameCheckResult) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = database.getAllFieldObjects().let { fields ->
                fields.firstOrNull { it.studyId != currentFieldId && (it.name == newName || it.alias == newName) }?.let { field ->
                    val conflictType = if (field.name == newName) "name" else "alias"
                    NameCheckResult(isUnique = false, conflictType = conflictType)
                } ?: NameCheckResult(isUnique = true)
            }
            withContext(Dispatchers.Main) {
                callback(result)
            }
        }
    }

    data class NameCheckResult(val isUnique: Boolean, val conflictType: String? = null)

    private fun showErrorMessage(messageView: TextView, message: String) {
        messageView.text = message
        messageView.visibility = View.VISIBLE
    }

    fun checkTraitsExist(callback: (Int) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {

            val traits = database.getVisibleTraits()
            val result = when {
                traits.isEmpty() -> {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.warning_traits_missing, Toast.LENGTH_SHORT).show()
                    }
                    -1
                }
                else -> 1
            }
            withContext(Dispatchers.Main) {
                callback(result)
            }
        }
    }

    fun startCollectActivity() {
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
            if (fieldObject?.dateImport?.isNotEmpty() == true) {
                val intent = Intent(context, CollectActivity::class.java)
                startActivity(intent)
            }
        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(
                this, getString(R.string.permission_rationale_trait_features),
                PERMISSIONS_REQUEST_TRAIT_DATA, *perms
            )
        }
    }

    private fun showUnarchiveDialog() {
        AlertDialog.Builder(requireContext(), R.style.AppAlertDialog)
            .setTitle(getString(R.string.dialog_unarchive_field_title))
            .setMessage(getString(R.string.dialog_unarchive_field_message))
            .setPositiveButton(getString(R.string.dialog_yes)) { d, _ ->
                fieldId?.let { database.setIsArchived(it, false) }
                fieldObject?.archived = false
                setAsActiveField()
            }
            .setNegativeButton(getString(R.string.dialog_no)) { d, _ ->
                d.dismiss()
            }
            .show()
    }

    private fun setAsActiveField() {
        fieldId?.let { id ->
            (activity as? BaseFieldActivity)?.apply {
                setActiveField(id)
                queryAndLoadFields()
            }
            (activity as? FieldArchivedActivity)?.finish()

            Handler(Looper.getMainLooper()).postDelayed({
                startCollectActivity()
            }, 100)
        }
    }
}
