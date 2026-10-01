package com.fieldbook.tracker.activities.brapi.io

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedDispatcher
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.activities.brapi.io.mapper.isAtObservationLevel
import com.fieldbook.tracker.activities.brapi.io.mapper.toTraitObject
import com.fieldbook.tracker.adapters.StudyAdapter
import com.fieldbook.tracker.adapters.StudyAdapter.Model
import com.fieldbook.tracker.brapi.BrapiControllerResponse
import com.fieldbook.tracker.brapi.model.BrapiObservationLevel
import com.fieldbook.tracker.brapi.model.BrapiStudyDetails
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory
import com.fieldbook.tracker.brapi.service.BrAPIServiceV1
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.utilities.InsetHandler
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.brapi.client.v2.JSON
import org.brapi.client.v2.model.queryParams.germplasm.GermplasmQueryParams
import org.brapi.client.v2.model.queryParams.phenotype.ObservationUnitQueryParams
import org.brapi.client.v2.model.queryParams.phenotype.VariableQueryParams
import org.brapi.v2.model.core.BrAPIStudy
import org.brapi.v2.model.germ.BrAPIGermplasm
import org.brapi.v2.model.pheno.BrAPIObservationUnit
import org.brapi.v2.model.pheno.BrAPIObservationVariable
import org.brapi.v2.model.pheno.BrAPIPositionCoordinateTypeEnum
import java.util.Locale
import javax.inject.Inject
import kotlin.collections.set
import kotlin.coroutines.resume
import kotlin.math.max

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

    private lateinit var loadingTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var listView: ListView
    private lateinit var studyList: RecyclerView
    private lateinit var importButton: MaterialButton

    //fetched models are only written on the main thread, once each fetch completes
    private val studies = arrayListOf<BrAPIStudy>()
    private val observationLevels = linkedSetOf<String>()
    private val observationVariables = hashMapOf<String, HashSet<BrAPIObservationVariable>>()
    private val observationUnits = hashMapOf<String, HashSet<BrAPIObservationUnit>>()
    private val germplasms = hashMapOf<String, HashSet<BrAPIGermplasm>>()

    //cleared whenever levels or units change, see existingLevels()
    private var existingLevelsCache: Array<String>? = null

    //levels of the selected studies that already exist as fields
    private val importedLevels by lazy { BrapiImportedLevels(db.allFieldObjects) }

    private var selectedLevel: Int = -1

    private var attributesTable: HashMap<String, Map<String, Map<String, String>>>? = null

    @Inject
    lateinit var db: DataHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_study_importer)

        loadingTextView = findViewById(R.id.act_brapi_importer_tv)
        progressBar = findViewById(R.id.act_list_filter_pb)
        listView = findViewById(R.id.act_study_importer_lv)
        studyList = findViewById(R.id.act_list_filter_rv)
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
        loadingTextView.visibility = View.GONE
        progressBar.visibility = View.INVISIBLE

        loadStudyList(programDbId, studyDbIds)
        setupImportButton(studyDbIds)
    }

    private fun loadLevelList() {

        listView.visibility = View.VISIBLE

        setDefaultAttributeIdentifiers()

        setLevelListOptions()
    }

    private fun BrAPIObservationUnit.levelName(): String? = observationUnitPosition?.observationLevel?.levelName

    /**
     * Server levels (in server order) that are used by at least one unit.
     * Falls back to the levels found on the units if the server levels are unavailable or don't match.
     * Levels already imported for every selected study that has them are left out.
     */
    private fun existingLevels(): Array<String> = existingLevelsCache ?: run {

        val importableLevels = linkedSetOf<String>()
        observationUnits.forEach { (studyDbId, units) ->
            units.mapNotNullTo(linkedSetOf()) { it.levelName() }
                .filterNotTo(importableLevels) { importedLevels.isImported(studyDbId, it) }
        }

        observationLevels.filter { it in importableLevels }
            .ifEmpty { importableLevels.toList() }
            .toTypedArray()

    }.also { existingLevelsCache = it }

    /**
     * The observation level the user has chosen, or null while no choice has been made yet.
     */
    private fun selectedLevelName() = existingLevels().let { levels ->
        if (selectedLevel in levels.indices) levels[selectedLevel] else null
    }

    private fun setLevelListOptions() {

        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_single_choice,
            existingLevels()
        )

        listView.setItemChecked(selectedLevel, true)

        listView.smoothScrollToPosition(selectedLevel)

        listView.setOnItemClickListener { _, _, position, _ ->

            selectedLevel = if (selectedLevel == position) {

                listView.setItemChecked(selectedLevel, false)

                -1

            } else position

            studyList.adapter?.notifyDataSetChanged()

        }

        if (existingLevels().isEmpty()) {
            listView.visibility = View.GONE
        }
    }

    private fun setDefaultAttributeIdentifiers() {

        val levels = existingLevels()

        if (selectedLevel == -1) {
            selectedLevel = if (levels.contains("plot")) {
                levels.indexOf("plot")
            } else {
                0
            }
        }
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
            override fun getObservationVariables(
                id: String,
                position: Int
            ): HashSet<BrAPIObservationVariable>? {
                val levelName = selectedLevelName()
                if (importedLevels.isImported(id, levelName)) return hashSetOf()
                return observationVariables[id]?.filter { it.isAtObservationLevel(levelName) }
                    ?.toHashSet()
            }

            override fun getObservationUnits(
                id: String,
                position: Int
            ): HashSet<BrAPIObservationUnit>? {
                val levelName = selectedLevelName()
                return observationUnits[id]?.let { units ->
                    when {
                        levelName == null -> units
                        importedLevels.isImported(id, levelName) -> hashSetOf()
                        else -> units.filterTo(hashSetOf()) { it.levelName() == levelName }
                    }
                }
            }

            override fun getGermplasm(id: String, position: Int): HashSet<BrAPIGermplasm>? {
                return germplasms[id]?.toHashSet()
            }

            override fun getLocation(id: String): String {
               return cacheModels.studies.firstOrNull { it.study.studyDbId == id }?.study?.locationName ?: ""
            }

            override fun getTrialName(id: String): String {
                return cacheModels.studies.firstOrNull { it.study.studyDbId == id }?.study?.trialName ?: ""
            }
        })

        (studyList.adapter as StudyAdapter).submitList(studyModels)

        launch {

            if (brapiService !is BrAPIServiceV2) return@launch

            //keep the screen on so the device doesn't sleep and drop the connection mid-download
            setKeepScreenOn(true)

            try {

                //fetch levels, variables, units, and germs for all studies concurrently,
                //the http client limits how many requests are sent to the server at once
                coroutineScope {

                    launch {
                        observationLevels.addAll(fetchObservationLevels(programDbId))
                        existingLevelsCache = null
                    }

                    studyModels.forEachIndexed { index, model ->

                        launch {
                            coroutineScope {
                                launch { fetchObservationVariables(model.id) }
                                launch { fetchObservationUnits(model.id) }
                            }
                            studyList.adapter?.notifyItemChanged(index)
                        }

                        launch { fetchGermplasm(model.id) }
                    }
                }

            } finally {

                //let the screen sleep again while the user chooses a level
                setKeepScreenOn(false)
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

            loadLevelList()

            studyList.adapter?.notifyDataSetChanged()

            //nothing left to import when every level of the selected studies already exists
            importButton.isEnabled = existingLevels().isNotEmpty()
        }
    }

    private fun setupImportButton(studyDbIds: List<String>) {

        importButton.visibility = View.VISIBLE
        importButton.setOnClickListener {

            progressBar.visibility = View.VISIBLE
            progressBar.isIndeterminate = true
            loadingTextView.text = getString(R.string.act_brapi_study_import_saving)
            loadingTextView.visibility = View.VISIBLE
            importButton.isEnabled = false

            //the activity finishes once saving is done, which clears the flag
            setKeepScreenOn(true)

            launch(Dispatchers.IO) {
                val successfullyImportedStudies = mutableListOf<String>()

                val level = BrapiObservationLevel().also {
                    it.observationLevelName = try {
                        if (selectedLevel in existingLevels().indices) {
                            existingLevels().elementAt(selectedLevel)
                        } else {
                            "plot"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to get observation level", e)
                        finish()
                        ""
                    }
                }

                studyDbIds.forEach { id ->

                    try {

                        studies.firstOrNull { it.studyDbId == id }?.let {

                            val response = saveStudy(it, level)

                            if (response?.status == true) {

                                successfullyImportedStudies.add(id) // track the successfully imported fields

                            } else if (response != null) {

                                Log.e(TAG, "Failed to save study $id: ${response.message}")

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
                if (successfullyImportedStudies.size == 1) { // switch active field if only one study was imported
                    //a study can have one field per level, so look up the field for the imported level
                    val fieldId = db.checkBrapiStudyUnique(level.observationLevelName, successfullyImportedStudies.first())
                    resultIntent.putExtra("fieldId", fieldId)
                }
                setResult(RESULT_OK, resultIntent)
                finish()

            }
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
     * Window flag only, so no wake lock permission is needed.
     */
    private fun setKeepScreenOn(keepOn: Boolean) {
        if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private suspend fun setProgress(progress: Int, progressMax: Int) {
        withContext(Dispatchers.Main) {
            progressBar.isIndeterminate = false
            progressBar.progress = progress
            progressBar.max = progressMax
        }
    }

    /**
     * Collects every page of a Fetcher flow, or returns null if any page failed
     */
    private suspend inline fun <reified M> fetchAllPages(name: String, crossinline flow: () -> Flow<Any>): HashSet<M>? =
        withContext(Dispatchers.IO) {
            try {
                val models = hashSetOf<M>()
                flow().collect { response ->
                    val (total, page) = response as Pair<*, *>
                    models.addAll((page as List<*>).filterIsInstance<M>())
                    Log.d(TAG, "Fetched $name ${models.size}/$total")
                }
                models
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.e(TAG, "Failed to fetch $name", e)
                null
            }
        }

    private fun pageSize() = prefs.getString(PreferenceKeys.BRAPI_PAGE_SIZE, "512")?.toIntOrNull() ?: 512

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

        val remainingLevels = importedLevels.remainingLevels(studyDbId)

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
            existingLevelsCache = null
        }
    }

    private suspend fun fetchObservationUnits(studyDbId: String, levelName: String?): HashSet<BrAPIObservationUnit>? {

        val service = brapiService as BrAPIServiceV2

        return fetchAllPages<BrAPIObservationUnit>("units for $studyDbId at ${levelName ?: "all levels"}") {
            service.observationUnitService.fetchAll(ObservationUnitQueryParams().also {
                it.studyDbId(studyDbId)
                it.pageSize(pageSize())
                levelName?.let { level -> it.observationUnitLevelName(level) }
            })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }
}
