package com.fieldbook.tracker.activities.brapi.io

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedDispatcher
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.activities.brapi.io.mapper.isAtObservationLevel
import com.fieldbook.tracker.activities.brapi.io.mapper.toTraitObject
import com.fieldbook.tracker.activities.brapi.io.sync.BrapiObservationDownloader
import com.fieldbook.tracker.adapters.StudyAdapter
import com.fieldbook.tracker.adapters.StudyAdapter.Model
import com.fieldbook.tracker.brapi.BrapiControllerResponse
import com.fieldbook.tracker.brapi.model.BrapiObservationLevel
import com.fieldbook.tracker.brapi.model.BrapiStudyDetails
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory
import com.fieldbook.tracker.brapi.service.BrAPIServiceV1
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2
import com.fieldbook.tracker.brapi.service.pheno.ObservationService
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.utilities.InsetHandler
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.brapi.client.v2.JSON
import org.brapi.client.v2.model.queryParams.germplasm.GermplasmQueryParams
import org.brapi.client.v2.model.queryParams.phenotype.ObservationQueryParams
import org.brapi.client.v2.model.queryParams.phenotype.ObservationUnitQueryParams
import org.brapi.client.v2.model.queryParams.phenotype.VariableQueryParams
import org.brapi.v2.model.core.BrAPIStudy
import org.brapi.v2.model.germ.BrAPIGermplasm
import org.brapi.v2.model.pheno.BrAPIObservationUnit
import org.brapi.v2.model.pheno.BrAPIObservationVariable
import org.brapi.v2.model.pheno.BrAPIPositionCoordinateTypeEnum
import org.json.JSONArray
import java.util.Locale
import javax.inject.Inject
import kotlin.collections.set
import kotlin.coroutines.resume

/**
 * receive study information including trial
 * from trial get program
 * get obs. levels
 * get observation units, and traits, germplasm
 */
@AndroidEntryPoint
class BrapiStudyImportActivity : ThemedActivity(), CoroutineScope by MainScope() {

    companion object {

        const val EXTRA_PROGRAM_DB_ID = "programDbId"
        const val EXTRA_STUDY_DB_IDS = "studyDbIds"

        //values of PreferenceKeys.BRAPI_IMPORT_OBSERVATIONS, see pref_brapi_import_observations_values
        private const val IMPORT_OBSERVATIONS_ALWAYS = "always"
        private const val IMPORT_OBSERVATIONS_ASK = "ask"
        private const val IMPORT_OBSERVATIONS_NEVER = "never"

        //study card progress, see getLoadProgress
        private const val PROGRESS_MAX = 1000
        private const val COUNT_PROGRESS_SHARE = 0.1f

        fun getIntent(context: Context): Intent {
            return Intent(context, BrapiStudyImportActivity::class.java)
        }
    }

    /**
     * Lazy-instantiate the brapi service, but cancel the activity if its v1
     */
    private val brapiService by lazy {
        BrAPIServiceFactory.getBrAPIService(this).also { service ->
            if (service is BrAPIServiceV1) {
                launch(Dispatchers.Main) {
                    Toast.makeText(
                        this@BrapiStudyImportActivity,
                        getString(R.string.brapi_v1_is_not_compatible),
                        Toast.LENGTH_SHORT
                    ).show()
                    setResult(RESULT_CANCELED)
                    finish()
                }
            }
        }
    }

    private lateinit var studyList: RecyclerView
    private lateinit var loadingIndicator: LinearProgressIndicator
    private lateinit var importButton: MaterialButton

    //fetched models are only written on the main thread, once each fetch completes
    private val studies = arrayListOf<BrAPIStudy>()
    private val observationLevels = linkedSetOf<String>()
    private val observationVariables = hashMapOf<String, HashSet<BrAPIObservationVariable>>()
    private val observationUnits = hashMapOf<String, HashSet<BrAPIObservationUnit>>()
    private val germplasms = hashMapOf<String, HashSet<BrAPIGermplasm>>()

    //per study, cleared whenever levels, units or variables change, see getLevels()
    private val levelsCache = hashMapOf<String, List<StudyAdapter.Level>>()

    //studies whose units and variables have finished fetching, successfully or not
    private val loadedStudies = hashSetOf<String>()

    //unit download progress per study, (received, total) per unit request keyed by level, null for all levels
    private val unitProgress = hashMapOf<String, HashMap<String?, Pair<Int, Int>>>()

    //checked level names per study
    private val selectedLevels = hashMapOf<String, MutableSet<String>>()

    /**
     * Observations on the server for a study, levels is null when the server couldn't count by level,
     * then the study total is shown on the location and trial row instead.
     */
    private data class ObservationCounts(val study: Int?, val levels: Map<String, Int>?)

    //filled in after each study's units load, see fetchObservationCounts
    private val observationCounts = hashMapOf<String, ObservationCounts>()

    //observation count requests per study, (finished, total), the last part of the study's loading progress
    private val countProgress = hashMapOf<String, Pair<Int, Int>>()

    //levels of the selected studies that already exist as fields, loaded off the main thread in fetchStudyData
    private var importedLevels = BrapiImportedLevels(emptyList())

    private var programDbId = ""
    private var studyModels: List<Model> = emptyList()

    //fetching study data, the refresh action is disabled meanwhile
    private var loading = false

    //saving fields, level selection and refresh are ignored meanwhile
    private var importing = false

    //set by the refresh action to fetch every level of each study instead of only the remaining recorded ones
    private var fullRefresh = false

    private var attributesTable: HashMap<String, Map<String, Map<String, String>>>? = null

    @Inject
    lateinit var db: DataHelper

    @Inject
    lateinit var observationDownloader: BrapiObservationDownloader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_study_importer)

        studyList = findViewById(R.id.act_list_filter_rv)
        loadingIndicator = findViewById(R.id.act_study_importer_pb)
        importButton = findViewById(R.id.act_study_importer_import_button)

        val toolbar = findViewById<Toolbar>(R.id.act_list_filter_tb)
        setSupportActionBar(toolbar)

        supportActionBar?.setTitle(R.string.act_brapi_study_import_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val rootView = findViewById<View>(android.R.id.content)
        InsetHandler.setupStandardInsets(rootView, toolbar)

        parseIntentExtras()

        OnBackPressedDispatcher().addCallback(this, standardBackCallback())
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            android.R.id.home -> {
                setResult(RESULT_CANCELED)
                finish()
                return true
            }

            R.id.action_refresh_study_data -> {
                refreshStudyData()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun parseIntentExtras() {
        val programDbId = intent.getStringExtra(EXTRA_PROGRAM_DB_ID)
        val studyDbIds =
            intent.getStringArrayExtra(EXTRA_STUDY_DB_IDS)?.toList()
                ?: listOf()

        if (studyDbIds.isEmpty()) {
            // fetch study info
            Toast.makeText(this, getString(R.string.no_studydbids_provided), Toast.LENGTH_SHORT).show()
            setResult(RESULT_CANCELED)
            finish()
        } else {

            studies.addAll(BrapiFilterCache.getStoredModels(this).studies.map { it.study }
                .filter { it.studyDbId in studyDbIds })

            fetchStudyInfo(programDbId ?: "", studyDbIds)
        }
    }

    /**
     * Observation levels are fetched alongside the study data rather than before it,
     * see loadStudyList
     */
    private fun fetchStudyInfo(programDbId: String, studyDbIds: List<String>) {

        importButton.isEnabled = false

        loadStudyList(programDbId, studyDbIds)
        setupImportButton(studyDbIds)
    }

    private fun BrAPIObservationUnit.levelName(): String? = observationUnitPosition?.observationLevel?.levelName

    /**
     * Levels used by the study's units that haven't been imported yet,
     * in server level order, followed by any levels the server didn't list.
     */
    private fun importableLevels(studyDbId: String): List<String> {

        val unitLevels = observationUnits[studyDbId].orEmpty()
            .mapNotNullTo(linkedSetOf()) { it.levelName() }
            .filterNot { importedLevels.isImported(studyDbId, it) }

        return observationLevels.filter { it in unitLevels } + unitLevels.filterNot { it in observationLevels }
    }

    /**
     * Importable levels of the study with their unit and trait counts, for the study card.
     */
    private fun getLevels(studyDbId: String): List<StudyAdapter.Level> {

        val levels = levelsCache.getOrPut(studyDbId) {

            val units = observationUnits[studyDbId].orEmpty()
            val variables = observationVariables[studyDbId].orEmpty()

            importableLevels(studyDbId).map { level ->
                StudyAdapter.Level(
                    name = level,
                    unitCount = units.count { it.levelName() == level },
                    traitCount = variables.count { it.isAtObservationLevel(level) },
                    checked = false
                )
            }
        }

        val selected = selectedLevels[studyDbId].orEmpty()
        val levelCounts = observationCounts[studyDbId]?.levels

        return levels.map { it.copy(checked = it.name in selected, observationCount = levelCounts?.get(it.name)) }
    }

    /**
     * Checks plot by default, or the first level if the study has no plots,
     * unless the user has already made a choice for the study.
     */
    private fun selectDefaultLevel(studyDbId: String) {

        if (studyDbId in selectedLevels) return

        val levels = importableLevels(studyDbId)

        (levels.firstOrNull { it.equals("plot", ignoreCase = true) } ?: levels.firstOrNull())?.let {
            selectedLevels[studyDbId] = mutableSetOf(it)
        }
    }

    private fun hasSelectedLevels() = selectedLevels.values.any { it.isNotEmpty() }

    /**
     * The study's loading progress out of PROGRESS_MAX, or null until a unit request has reported a total.
     * Units fill most of it, by units received out of units expected across the unit requests so far,
     * and observation count requests fill the last COUNT_PROGRESS_SHARE as they finish.
     * When only the remaining levels are fetched, each level's total is added as its request starts.
     */
    private fun getLoadProgress(studyDbId: String): Pair<Int, Int>? {

        val requests = unitProgress[studyDbId]?.values ?: return null

        val unitTotal = requests.sumOf { it.second }

        if (unitTotal <= 0) return null

        val units = requests.sumOf { it.first }.coerceAtMost(unitTotal).toFloat() / unitTotal

        val counts = countProgress[studyDbId]
            ?.let { (finished, total) -> if (total > 0) finished.toFloat() / total else 0f }
            ?: 0f

        val progress = units * (1 - COUNT_PROGRESS_SHARE) + counts * COUNT_PROGRESS_SHARE

        return (progress * PROGRESS_MAX).toInt() to PROGRESS_MAX
    }

    private fun onUnitProgress(studyDbId: String, levelName: String?, received: Int, total: Int) {

        unitProgress.getOrPut(studyDbId) { hashMapOf() }[levelName] = received to total

        notifyProgress(studyDbId)
    }

    /**
     * Sets how many observation count requests the study makes: the study total from the start,
     * then one per level once the units show the study has several importable levels.
     */
    private fun setCountRequests(studyDbId: String, total: Int) {

        countProgress[studyDbId] = (countProgress[studyDbId]?.first ?: 0) to total

        notifyProgress(studyDbId)
    }

    private fun onCountRequestFinished(studyDbId: String) {

        val (finished, total) = countProgress[studyDbId] ?: (0 to 1)

        countProgress[studyDbId] = (finished + 1) to total

        notifyProgress(studyDbId)
    }

    private fun notifyProgress(studyDbId: String) {

        val position = (studyList.adapter as? StudyAdapter)?.currentList?.indexOfFirst { it.id == studyDbId } ?: -1
        if (position >= 0) studyList.adapter?.notifyItemChanged(position, StudyAdapter.PAYLOAD_PROGRESS)
    }

    private fun onLevelsChanged() {
        levelsCache.clear()
    }

    private fun getAttributes(studyDbId: String): Map<String, Map<String, String>> {

        val unitAttributes = hashMapOf<String, Map<String, String>>()
        val germs = germplasms[studyDbId].orEmpty().associateBy { it.germplasmDbId }

        observationUnits[studyDbId]?.forEach { unit ->

            val attributes = hashMapOf<String, String>()

            if (unit.germplasmName != null) {
                attributes["Germplasm"] = unit.germplasmName
            }

            unit.locationName?.takeIf { it.isNotEmpty() }?.let {
                attributes["Location"] = it
            }

            unit.additionalInfo?.entrySet()?.forEach { (key, value) ->
                try {
                    value?.takeIf { it.isJsonPrimitive || it.isJsonArray }
                        ?.asString
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { attributes[key] = it }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed loading additionalInfo $key, $value", e)
                }
            }

            val position = unit.observationUnitPosition
            if (position != null) {
                position.observationLevelRelationships?.forEach { level ->
                    if (level.levelName != null) {
                        attributes[level.levelName.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(
                                Locale.getDefault()
                            ) else it.toString()
                        }] = level.levelCode
                    }
                }

                position.positionCoordinateX?.let { x ->
                    attributes[getRowColStr(position.positionCoordinateXType) ?: "Row"] = x
                }

                position.positionCoordinateY?.let { y ->
                    attributes[getRowColStr(position.positionCoordinateYType) ?: "Column"] = y
                }

                if (position.entryType != null && position.entryType.brapiValue != null) {
                    attributes["EntryType"] = position.entryType.brapiValue
                }
            }

            if (germplasms.isNotEmpty() && unit.germplasmDbId != null) {

                germs[unit.germplasmDbId]?.let { germ ->

                    germ.accessionNumber?.let { accession ->
                        attributes["AccessionNumber"] = accession
                    }

                    germ.pedigree?.let { pedigree ->
                        attributes["Pedigree"] = pedigree
                    }

                    germ.synonyms?.let { synonyms ->
                        attributes["Synonyms"] =
                            synonyms.mapNotNull { it.synonym?.replace("\"", "\"\"") }
                                .joinToString("; ")
                    }
                }
            }

            if (unit.observationUnitDbId != null) {
                attributes["ObservationUnitDbId"] = unit.observationUnitDbId
            }

            if (unit.observationUnitName != null) {
                attributes["ObservationUnitName"] = unit.observationUnitName
            }

            unitAttributes[unit.observationUnitDbId] = attributes
        }

        return unitAttributes

    }

    private fun getRowColStr(type: BrAPIPositionCoordinateTypeEnum?): String? {
        if (null != type) {
            return when (type) {
                BrAPIPositionCoordinateTypeEnum.PLANTED_INDIVIDUAL,
                BrAPIPositionCoordinateTypeEnum.GRID_COL,
                BrAPIPositionCoordinateTypeEnum.MEASURED_COL,
                BrAPIPositionCoordinateTypeEnum.LATITUDE -> "Column"

                BrAPIPositionCoordinateTypeEnum.PLANTED_ROW,
                BrAPIPositionCoordinateTypeEnum.GRID_ROW,
                BrAPIPositionCoordinateTypeEnum.MEASURED_ROW,
                BrAPIPositionCoordinateTypeEnum.LONGITUDE -> "Row"
            }
        }
        return null
    }

    private fun loadStudyList(programDbId: String, studyDbIds: List<String>) {

        studyList.layoutManager = LinearLayoutManager(this).also {
            it.orientation = LinearLayoutManager.VERTICAL
        }

        val cacheModels = BrapiFilterCache.getStoredModels(this@BrapiStudyImportActivity)

        val studyModels = studyDbIds.map { id ->

            val studyName =
                cacheModels.studies.firstOrNull { it.study.studyDbId == id }?.study?.studyName ?: id

            Model(
                id = id,
                title = studyName,
            )
        }

        studyList.adapter = StudyAdapter(object : StudyAdapter.StudyLoader {

            override fun isLoading(id: String) = id !in loadedStudies

            override fun getProgress(id: String) = getLoadProgress(id)

            override fun getLevels(id: String) = this@BrapiStudyImportActivity.getLevels(id)

            override fun onLevelChecked(id: String, levelName: String, checked: Boolean) {

                selectedLevels.getOrPut(id) { mutableSetOf() }.apply {
                    if (checked) add(levelName) else remove(levelName)
                }

                //attributes are built once everything has loaded, saving needs them,
                //and the levels to save are taken when import starts so the button stays off while saving
                importButton.isEnabled = !importing && attributesTable != null && hasSelectedLevels()
            }

            override fun getLocation(id: String): String {
               return cacheModels.studies.firstOrNull { it.study.studyDbId == id }?.study?.locationName ?: ""
            }

            override fun getTrialName(id: String): String {
                return cacheModels.studies.firstOrNull { it.study.studyDbId == id }?.study?.trialName ?: ""
            }

            override fun getObservationCount(id: String): Int? =
                observationCounts[id]?.takeIf { it.levels == null }?.study
        })

        (studyList.adapter as StudyAdapter).submitList(studyModels)

        this.programDbId = programDbId
        this.studyModels = studyModels

        fetchStudyData()
    }

    /**
     * Fetches levels, variables, units and germplasm for the studies, then builds the unit attributes.
     * Also used by the refresh action, which sets fullRefresh to fetch every level of each study
     * rather than only the levels recorded by earlier imports.
     */
    private fun fetchStudyData() {

        val studyDbIds = studyModels.map { it.id }

        loading = true
        invalidateOptionsMenu()

        launch {

            if (brapiService !is BrAPIServiceV2) return@launch

            //hidden once the attributes are built and the study can be imported,
            //a failed load closes the screen instead
            loadingIndicator.visibility = View.VISIBLE

            //keep the screen on so the device doesn't sleep and drop the connection mid-download
            setKeepScreenOn(true)

            try {

                //imported levels decide which levels are fetched and shown, the query is too slow for the main thread
                importedLevels = withContext(Dispatchers.IO) { BrapiImportedLevels(db.allFieldObjects) }

                //fetch levels, variables, units, and germs for all studies concurrently,
                //the http client limits how many requests are sent to the server at once
                coroutineScope {

                    launch {
                        observationLevels.addAll(fetchObservationLevels(programDbId))
                        onLevelsChanged()
                        studyList.adapter?.notifyDataSetChanged()
                    }

                    studyModels.forEachIndexed { index, model ->

                        launch {

                            //the study total doesn't need the units, so it's counted while they download
                            setCountRequests(model.id, 1)
                            val studyCount = async { countWithProgress(model.id, null) }

                            coroutineScope {
                                launch { fetchObservationVariables(model.id) }
                                launch { fetchObservationUnits(model.id) }
                            }

                            //level counts need the units' levels, and the levels show with their counts
                            observationCounts[model.id] = fetchObservationCounts(model.id, studyCount)

                            loadedStudies.add(model.id)
                            onLevelsChanged()
                            selectDefaultLevel(model.id)
                            studyList.adapter?.notifyItemChanged(index)
                        }

                        launch { fetchGermplasm(model.id) }
                    }
                }

            } finally {

                //let the screen sleep again while the user chooses a level
                setKeepScreenOn(false)

                loading = false
                invalidateOptionsMenu()
            }

            if (fullRefresh) {
                saveRefreshedLevels(studyDbIds)
                fullRefresh = false
            }

            if ((studyList.adapter as StudyAdapter).currentList.any {
                    observationUnits[it.id]?.isEmpty() != false
                }) {

                Toast.makeText(this@BrapiStudyImportActivity,
                    getString(R.string.failed_to_fetch_observation_units), Toast.LENGTH_SHORT).show()

                onBackPressedDispatcher.onBackPressed()

                return@launch
            }

            withContext(Dispatchers.Default) {
                attributesTable = HashMap(studyDbIds.associateWith { getAttributes(it) })
            }

            studyList.adapter?.notifyDataSetChanged()

            loadingIndicator.visibility = View.GONE

            //nothing to import until at least one level is checked
            importButton.isEnabled = hasSelectedLevels()
        }
    }

    /**
     * Records every level found on each study's units on the study's existing fields,
     * so studies with levels added on the server since they were imported show in the study list again.
     */
    private suspend fun saveRefreshedLevels(studyDbIds: List<String>) {

        val levelsByStudy = studyDbIds.mapNotNull { id ->
            observationUnits[id]?.takeIf { it.isNotEmpty() }?.let { units ->
                id to units.mapNotNull { it.levelName() }.distinct()
            }
        }

        importedLevels = withContext(Dispatchers.IO) {
            levelsByStudy.forEach { (id, levels) ->
                db.updateStudyDbLevels(id, JSONArray(levels).toString())
            }
            BrapiImportedLevels(db.allFieldObjects)
        }

        onLevelsChanged()
    }

    /**
     * Clears the fetched data and fetches it again, including every level of each study.
     */
    private fun refreshStudyData() {

        if (loading || importing) return

        observationLevels.clear()
        observationVariables.clear()
        observationUnits.clear()
        germplasms.clear()
        loadedStudies.clear()
        unitProgress.clear()
        countProgress.clear()
        observationCounts.clear()
        onLevelsChanged()

        //selected levels are kept, levels that are no longer importable are skipped when saving
        attributesTable = null
        importButton.isEnabled = false

        fullRefresh = true

        studyList.adapter?.notifyDataSetChanged()

        fetchStudyData()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_brapi_study_import, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.action_refresh_study_data)?.isEnabled = !loading && !importing
        return super.onPrepareOptionsMenu(menu)
    }

    private fun setupImportButton(studyDbIds: List<String>) {

        importButton.visibility = View.VISIBLE
        importButton.setOnClickListener {

            if (importing) return@setOnClickListener

            //nothing to download, skip the prompt
            if (selectedObservationCount(studyDbIds) == 0) {
                importStudies(studyDbIds, downloadObservations = false)
                return@setOnClickListener
            }

            when (prefs.getString(PreferenceKeys.BRAPI_IMPORT_OBSERVATIONS, IMPORT_OBSERVATIONS_ASK)) {
                IMPORT_OBSERVATIONS_ALWAYS -> importStudies(studyDbIds, downloadObservations = true)
                IMPORT_OBSERVATIONS_NEVER -> importStudies(studyDbIds, downloadObservations = false)
                else -> askToDownloadObservations { download -> importStudies(studyDbIds, download) }
            }
        }
    }

    /**
     * Dismissing the dialog leaves the screen as it was, so the user can change their selection.
     * Never skips the download and stops asking, the setting can be changed back in BrAPI settings.
     */
    private fun askToDownloadObservations(onChoice: (Boolean) -> Unit) {

        AlertDialog.Builder(this, R.style.AppAlertDialog)
            .setTitle(R.string.act_brapi_study_import_observations_title)
            .setMessage(R.string.act_brapi_study_import_observations_message)
            .setPositiveButton(R.string.act_brapi_study_import_observations_download) { _, _ -> onChoice(true) }
            .setNegativeButton(R.string.act_brapi_study_import_observations_skip) { _, _ -> onChoice(false) }
            .setNeutralButton(R.string.act_brapi_study_import_observations_never) { _, _ ->
                prefs.edit { putString(PreferenceKeys.BRAPI_IMPORT_OBSERVATIONS, IMPORT_OBSERVATIONS_NEVER) }
                onChoice(false)
            }
            .show()
    }

    private fun importStudies(studyDbIds: List<String>, downloadObservations: Boolean) {

        if (importing) return

        //the activity finishes once saving is done, so this is never cleared
        importing = true
        invalidateOptionsMenu()

        importButton.isEnabled = false

        //the activity finishes once saving is done, which clears the flag
        setKeepScreenOn(true)

        //each checked level of each study becomes its own field, in the order shown on the cards
        val studyLevels = studyDbIds.flatMap { id ->
            val selected = selectedLevels[id].orEmpty()
            importableLevels(id).filter { it in selected }.map { level -> id to level }
        }

        val dialog = ImportProgressDialog()

        launch(Dispatchers.IO) {

            //(studyDbId, level) of each field that was created
            val successfulImports = mutableListOf<Pair<String, String>>()

            studyLevels.forEachIndexed { index, (id, levelName) ->

                runOnUiThread { dialog.setSaving(index + 1, studyLevels.size) }

                val level = BrapiObservationLevel().also { it.observationLevelName = levelName }

                try {

                    studies.firstOrNull { it.studyDbId == id }?.let {

                        val response = saveStudy(it, level)

                        if (response?.status == true) {

                            successfulImports.add(id to levelName) // track the successfully imported fields

                        } else if (response != null) {

                            Log.e(TAG, "Failed to save study $id at $levelName: ${response.message}")

                            runOnUiThread {

                                Toast.makeText(this@BrapiStudyImportActivity, getString(R.string.failed_to_save_study), Toast.LENGTH_SHORT).show()

                            }
                        }
                    }

                } catch (e: Exception) {

                    Log.e(TAG, "Failed to save study", e)

                    runOnUiThread {

                        Toast.makeText(this@BrapiStudyImportActivity, getString(R.string.failed_to_save_study), Toast.LENGTH_SHORT).show()

                    }
                }
            }

            val resultIntent = Intent()
            if (successfulImports.size == 1) { // switch active field if only one field was imported
                //a study can have one field per level, so look up the field for the imported level
                val (studyDbId, levelName) = successfulImports.first()
                resultIntent.putExtra("fieldId", db.checkBrapiStudyUnique(levelName, studyDbId))
            }
            //set before downloading, the fields are kept if the user leaves while observations download
            setResult(RESULT_OK, resultIntent)

            if (downloadObservations && successfulImports.isNotEmpty()) {
                downloadImportedObservations(successfulImports, dialog)
            }

            withContext(Dispatchers.Main) {
                dialog.dismiss()
                finish()
            }
        }
    }

    /**
     * Downloads the server's observations into the fields that were just created, reporting progress in the import dialog.
     * A failed or cancelled download leaves the fields in place, the user can sync each field later.
     * Observations of studies that finished before a cancel are kept.
     *
     * @param imports (studyDbId, level) of each created field
     */
    private suspend fun downloadImportedObservations(imports: List<Pair<String, String>>, dialog: ImportProgressDialog) {

        val fields = imports.mapNotNull { (studyDbId, levelName) ->
            db.checkBrapiStudyUnique(levelName, studyDbId)
                .takeIf { it > 0 }
                ?.let { db.getFieldObject(it) }
        }

        val studyCount = fields.mapNotNull { it.studyDbId }.distinct().size

        withContext(Dispatchers.Main) {
            dialog.setDownloadProgress(BrapiObservationDownloader.Progress(0, studyCount, 0, 0))
        }

        //a supervisor scope so a failed or cancelled download doesn't cancel the import that finishes the activity
        supervisorScope {

            val download = async(Dispatchers.IO) {
                observationDownloader.downloadInto(brapiService, fields) { progress ->
                    runOnUiThread { dialog.setDownloadProgress(progress) }
                }
            }

            withContext(Dispatchers.Main) {
                dialog.showCancel { download.cancel() }
            }

            val message = try {

                val count = download.await().values.sum()

                resources.getQuantityString(R.plurals.act_brapi_study_import_observations_downloaded, count, count)

            } catch (e: CancellationException) {

                //rethrows if the import itself was cancelled because the activity closed,
                //otherwise only the download was cancelled from the dialog
                currentCoroutineContext().ensureActive()

                getString(R.string.act_brapi_study_import_observations_cancelled)

            } catch (e: Exception) {

                Log.e(TAG, "Failed to download observations for imported fields", e)

                getString(R.string.act_brapi_study_import_observations_failed)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Status of the import, saving the fields and then downloading their observations.
     * Not cancelable by back or outside taps. The cancel button is only shown while observations download,
     * stopping partway through saving would leave some of the selected fields imported and others not.
     */
    private inner class ImportProgressDialog {

        private val view = layoutInflater.inflate(R.layout.dialog_loading, null)

        private val messageTextView = view.findViewById<TextView>(R.id.loading_message).also {
            it.text = ""
        }

        private val dialog = AlertDialog.Builder(this@BrapiStudyImportActivity, R.style.AppAlertDialog)
            .setTitle(R.string.act_brapi_study_import_saving)
            .setView(view)
            .setCancelable(false)
            //only creates the button, showCancel sets a click listener that doesn't dismiss the dialog
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        private val cancelButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE).also {
            it.visibility = View.GONE
        }

        fun setSaving(field: Int, fieldCount: Int) {
            messageTextView.text = getString(R.string.act_brapi_study_import_saving_field, field, fieldCount)
        }

        fun setDownloadProgress(progress: BrapiObservationDownloader.Progress) {

            dialog.setTitle(R.string.act_brapi_study_import_downloading_title)

            messageTextView.text = if (progress.totalPages > 1) {
                getString(
                    R.string.act_brapi_study_import_downloading_page,
                    progress.studyIndex + 1,
                    progress.studyCount,
                    progress.page,
                    progress.totalPages
                )
            } else {
                getString(
                    R.string.act_brapi_study_import_downloading,
                    progress.studyIndex + 1,
                    progress.studyCount
                )
            }
        }

        fun showCancel(onCancel: () -> Unit) {
            cancelButton.visibility = View.VISIBLE
            cancelButton.setOnClickListener {
                cancelButton.isEnabled = false
                onCancel()
            }
        }

        fun dismiss() {
            if (dialog.isShowing && !isDestroyed) dialog.dismiss()
        }
    }

    /**
     * @return the save result, or null if the study was skipped because it has no units at this level
     * or the level is already imported
     */
    private fun saveStudy(
        study: BrAPIStudy,
        level: BrapiObservationLevel
    ): BrapiControllerResponse<*>? {

        if (importedLevels.isImported(study.studyDbId, level.observationLevelName)) return null

        var maxVariableIndex = db.maxPositionFromTraits + 1

        return attributesTable?.get(study.studyDbId)?.let { studyAttributes ->

            val studyUnits = observationUnits[study.studyDbId].orEmpty()

            studyUnits.filter {
                it.levelName().equals(level.observationLevelName, ignoreCase = true)
            }
                .takeIf { it.isNotEmpty() }
                ?.let { units ->

                    val details = BrapiStudyDetails()
                    details.studyDbId = study.studyDbId
                    details.studyName = study.studyName
                    details.commonCropName = study.commonCropName
                    details.numberOfPlots = units.size
                    details.trialName = study.trialName

                    //every level of the study's units, so the study stays importable until all are imported,
                    //including levels recorded earlier that weren't fetched this time
                    details.studyDbLevels = (importedLevels.knownLevels(study.studyDbId) +
                            studyUnits.mapNotNull { it.levelName() }).distinctBy { it.lowercase() }

                    //BMS specific, only import the variables used at the selected level
                    details.traits = observationVariables[study.studyDbId]
                        ?.filter { it.isAtObservationLevel(level.observationLevelName) }
                        ?.map { it.toTraitObject(this@BrapiStudyImportActivity).also {
                            it.realPosition = maxVariableIndex++
                        } } ?: listOf()

                    val geoCoordinateColumnName = "geo_coordinates"

                    val attributes = (studyAttributes.values.flatMap { it.keys } + geoCoordinateColumnName).distinct()

                    details.attributes = attributes

                    val unitAttributes = ArrayList<List<String>>()
                    units.forEach { unit ->

                        val row = ArrayList<String>()

                        attributes.forEach { attr ->
                            if (attr != geoCoordinateColumnName) {
                                row.add(studyAttributes[unit.observationUnitDbId]?.get(attr) ?: "")
                            }
                        }

                        //add geo json as json string
                        if (geoCoordinateColumnName in attributes) {
                            unit.observationUnitPosition?.geoCoordinates?.let { coordString ->
                                try {
                                    row.add(JSON().serialize(coordString))
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to serialize geo coordinates", e)
                                }
                            }
                        }

                        unitAttributes.add(row)

                    }

                    details.values = mutableListOf()
                    details.values.addAll(unitAttributes)

                    //primary/secondary and sort are no longer chosen at import
                    brapiService.saveStudyDetails(
                        details,
                        level,
                        "",
                        "",
                        "",
                    )
                }
        }
    }

    /**
     * @return the server's observation level names in level order, or empty if they could not be fetched,
     * in which case the levels found on the units are used instead
     */
    private suspend fun fetchObservationLevels(programDbId: String): List<String> {

        Log.d(TAG, "Fetching levels for $programDbId")

        val timeoutMillis = BrAPIService.getTimeoutValue(this) * 1000L * 2

        return withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                brapiService.getObservationLevels(programDbId.ifEmpty { null }, { levels ->

                    Log.d(TAG, "${levels.size} levels fetched")

                    if (continuation.isActive) {
                        continuation.resume(levels.mapNotNull { it.observationLevelName })
                    }

                }) { _ ->

                    Log.e(TAG, "Failed to fetch observation levels")

                    if (continuation.isActive) continuation.resume(emptyList())
                }
            }
        } ?: emptyList()
    }

    /**
     * Collects every page of a Fetcher flow, or returns null if any page failed
     */
    /**
     * @param onPage called on the main thread after each page with the models received so far and the server's total
     */
    private suspend inline fun <reified M> fetchAllPages(
        name: String,
        crossinline onPage: (received: Int, total: Int) -> Unit = { _, _ -> },
        crossinline flow: () -> Flow<Any>
    ): HashSet<M>? =
        withContext(Dispatchers.IO) {
            try {
                val models = hashSetOf<M>()
                flow().collect { response ->
                    val (total, page) = response as Pair<*, *>
                    models.addAll((page as List<*>).filterIsInstance<M>())
                    Log.d(TAG, "Fetched $name ${models.size}/$total")
                    val received = models.size
                    withContext(Dispatchers.Main) { onPage(received, total as? Int ?: 0) }
                }
                models
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.e(TAG, "Failed to fetch $name", e)
                null
            }
        }

    private fun pageSize() = BrAPIService.getPageSize(this)

    private suspend fun fetchGermplasm(studyDbId: String) {

        val service = brapiService as BrAPIServiceV2

        fetchAllPages<BrAPIGermplasm>("germplasm for $studyDbId") {
            service.germplasmService.fetchAll(GermplasmQueryParams().also {
                it.studyDbId(studyDbId)
                it.pageSize(pageSize())
            })
        }?.let { germplasms[studyDbId] = it }
    }

    private suspend fun fetchObservationVariables(studyDbId: String) {

        val service = brapiService as BrAPIServiceV2

        fetchAllPages<BrAPIObservationVariable>("variables for $studyDbId") {
            service.observationVariableService.fetchAll(VariableQueryParams().also {
                it.studyDbId(studyDbId)
                it.pageSize(pageSize())
            })
        }?.let { observationVariables[studyDbId] = it }
    }

    /**
     * If an earlier import recorded the study's levels, only the levels not yet imported are fetched,
     * one request per level. Otherwise every unit of the study is fetched.
     */
    private suspend fun fetchObservationUnits(studyDbId: String) {

        //a refresh fetches every level so levels added on the server since the last import are found
        val remainingLevels = if (fullRefresh) null else importedLevels.remainingLevels(studyDbId)

        val units = if (remainingLevels == null) fetchObservationUnits(studyDbId, null) else {

            val levelUnits = hashSetOf<BrAPIObservationUnit>()

            for (level in remainingLevels) {

                val units = fetchObservationUnits(studyDbId, level) ?: return

                levelUnits.addAll(units)

                //the server ignored the level filter and sent every unit, so the other levels are already here
                if (units.any { !it.levelName().equals(level, ignoreCase = true) }) break
            }

            //nothing came back for the remaining levels, fall back to fetching the whole study
            levelUnits.ifEmpty { fetchObservationUnits(studyDbId, null) }
        }

        units?.let {
            observationUnits[studyDbId] = it
            onLevelsChanged()
        }
    }

    private suspend fun fetchObservationUnits(studyDbId: String, levelName: String?): HashSet<BrAPIObservationUnit>? {

        val service = brapiService as BrAPIServiceV2

        return fetchAllPages<BrAPIObservationUnit>(
            "units for $studyDbId at ${levelName ?: "all levels"}",
            onPage = { received, total -> onUnitProgress(studyDbId, levelName, received, total) }
        ) {
            service.observationUnitService.fetchAll(ObservationUnitQueryParams().also {
                it.studyDbId(studyDbId)
                it.pageSize(pageSize())
                levelName?.let { level -> it.observationUnitLevelName(level) }
            })
        }
    }

    /**
     * Counts the study's observations at each importable level, once the study's units have loaded,
     * and combines them with the study total that was counted alongside the units.
     * A study with a single importable level isn't counted by level, its study total is shown on
     * the level row if that's the study's only level, or on the location and trial row otherwise.
     * Called on the main thread.
     */
    private suspend fun fetchObservationCounts(
        studyDbId: String,
        studyCount: Deferred<ObservationService.Count?>
    ): ObservationCounts {

        val levels = importableLevels(studyDbId)

        val unitLevels = observationUnits[studyDbId].orEmpty()
            .filter { it.observationUnitDbId != null }
            .associate { it.observationUnitDbId to it.levelName() }

        if (levels.size <= 1) {

            val studyTotal = studyCount.await()?.total

            //levels imported earlier have observations the study total would include
            val allLevels = (importedLevels.knownLevels(studyDbId) + unitLevels.values.filterNotNull())
                .distinctBy { it.lowercase() }

            val onlyLevel = levels.singleOrNull()
                ?.takeIf { allLevels.size == 1 && !importedLevels.hasImports(studyDbId) }

            return ObservationCounts(
                study = studyTotal,
                levels = if (onlyLevel != null && studyTotal != null) mapOf(onlyLevel to studyTotal) else null
            )
        }

        setCountRequests(studyDbId, levels.size + 1)

        return coroutineScope {

            val byLevel = levels.map { level -> level to async { countWithProgress(studyDbId, level) } }

            val studyTotal = studyCount.await()?.total

            ObservationCounts(
                study = studyTotal,
                levels = levelTotals(studyTotal, byLevel.map { (level, count) -> level to count.await() }, unitLevels)
            )
        }
    }

    /**
     * Counts on the io dispatcher, then records the finished request for the study's progress on the main thread.
     */
    private suspend fun countWithProgress(studyDbId: String, levelName: String?): ObservationService.Count? {

        val service = brapiService as? BrAPIServiceV2 ?: return null

        return withContext(Dispatchers.IO) { countObservations(service, studyDbId, levelName) }
            .also { onCountRequestFinished(studyDbId) }
    }

    /**
     * @param levelName filters by the unit's level, BrAPI 2.1 servers may ignore it
     * @return null if the server failed to answer
     */
    private suspend fun countObservations(
        service: BrAPIServiceV2,
        studyDbId: String,
        levelName: String?
    ): ObservationService.Count? = try {

        service.observationService.count(ObservationQueryParams().also {
            it.studyDbId(studyDbId)
            levelName?.let { level -> it.observationUnitLevelName(level) }
        })

    } catch (e: Exception) {

        currentCoroutineContext().ensureActive()

        Log.e(TAG, "Failed to count observations for $studyDbId at ${levelName ?: "all levels"}", e)

        null
    }

    /**
     * The level totals if the server filtered by level, or null to show the study total instead.
     * Servers that ignore the level filter return the whole study's observations for each level,
     * which shows as a returned observation on a unit at another level, or level totals that don't add up.
     *
     * @param unitLevels level of each of the study's fetched units, by unit id
     */
    private fun levelTotals(
        studyTotal: Int?,
        levelCounts: List<Pair<String, ObservationService.Count?>>,
        unitLevels: Map<String, String?>
    ): Map<String, Int>? {

        if (levelCounts.isEmpty()) return null

        //a level the server couldn't count
        val totals = levelCounts.associate { (level, count) -> level to (count?.total ?: return null) }

        //every importable level's units are fetched, so a unit that isn't among them is at another level
        val sampleAtOtherLevel = levelCounts.any { (level, count) ->
            val unitDbId = count?.sample?.observationUnitDbId ?: return@any false
            unitLevels[unitDbId]?.equals(level, ignoreCase = true) != true
        }

        if (sampleAtOtherLevel) return null

        if (studyTotal != null) {

            //each level returned the whole study
            if (totals.size > 1 && studyTotal > 0 && totals.values.all { it == studyTotal }) return null

            //the levels can't hold more observations than the study
            if (totals.values.sum() > studyTotal) return null
        }

        return totals
    }

    /**
     * Observations on the server for the checked levels of the selected studies,
     * or null if any of them hasn't been counted.
     */
    private fun selectedObservationCount(studyDbIds: List<String>): Int? =
        studyDbIds.filter { selectedLevels[it].orEmpty().isNotEmpty() }.sumOf { id ->

            val counts = observationCounts[id] ?: return null

            counts.levels?.let { levels ->
                selectedLevels[id].orEmpty().sumOf { level -> levels[level] ?: return null }
            } ?: counts.study ?: return null
        }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }
}
