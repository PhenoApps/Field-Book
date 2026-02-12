package com.fieldbook.tracker.activities.brapi.io

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
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
import com.fieldbook.tracker.activities.brapi.io.mapper.toTraitObject
import com.fieldbook.tracker.adapters.StudyAdapter
import com.fieldbook.tracker.adapters.StudyAdapter.Model
import com.fieldbook.tracker.brapi.model.BrapiObservationLevel
import com.fieldbook.tracker.brapi.model.BrapiStudyDetails
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory
import com.fieldbook.tracker.brapi.service.BrAPIServiceV1
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.utilities.InsetHandler
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private lateinit var tabLayout: TabLayout
    private lateinit var listView: ListView
    private lateinit var studyList: RecyclerView
    private lateinit var importButton: MaterialButton

    private val studies = arrayListOf<BrAPIStudy>()
    private val observationLevels = hashSetOf<String>()
    private val observationVariables =
        hashMapOf<String, HashSet<BrAPIObservationVariable>>().withDefault { hashSetOf() }
    private val observationUnits =
        hashMapOf<String, HashSet<BrAPIObservationUnit>>().withDefault { hashSetOf() }
    private val germplasms =
        hashMapOf<String, HashSet<BrAPIGermplasm>>().withDefault { hashSetOf() }

    private var selectedLevel: Int = -1
    private var selectedSort: Int = -1

    private var attributesTable: HashMap<String, Map<String, Map<String, String>>>? = null

    @Inject
    lateinit var db: DataHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_study_importer)

        loadingTextView = findViewById(R.id.act_brapi_importer_tv)
        progressBar = findViewById(R.id.act_list_filter_pb)
        tabLayout = findViewById(R.id.brapi_importer_tl)
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

    private suspend fun setLoadingText(text: String) {
        withContext(Dispatchers.Main) {
            loadingTextView.text = text
        }
    }

    private fun fetchStudyInfo(programDbId: String, studyDbIds: List<String>) {

        launch {

            setLoadingText(getString(R.string.act_brapi_study_import_fetch_levels))

            try {

                fetchObservationLevels(programDbId).join()

            } catch (e: Exception) {

                e.printStackTrace()

                FirebaseCrashlytics.getInstance().recordException(e)

                withContext(Dispatchers.Main) {

                    Toast.makeText(this@BrapiStudyImportActivity, getString(R.string.failed_to_fetch_observation_levels), Toast.LENGTH_SHORT).show()

                    setResult(RESULT_CANCELED)

                    finish()
                }
            }

            withContext(Dispatchers.Main) {
                importButton.isEnabled = false
                loadingTextView.visibility = View.GONE
                progressBar.visibility = View.INVISIBLE
                tabLayout.visibility = View.GONE

                loadStudyList(studyDbIds)
                setupImportButton(studyDbIds)
            }
        }
    }

    enum class Tab {
        LEVELS, SORT
    }

    private fun loadTabLayout(studyDbIds: List<String>) {

        tabLayout.visibility = View.VISIBLE

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    Tab.LEVELS.ordinal -> setLevelListOptions()
                    Tab.SORT.ordinal -> setSortListOptions()
                }

                listView.visibility = View.VISIBLE

            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {

            }

            override fun onTabReselected(tab: TabLayout.Tab?) {

            }
        })

        listView.visibility = View.VISIBLE

        attributesTable = hashMapOf()
        attributesTable?.let { table ->
            for (study in studyDbIds) {
                table[study] = getAttributes(study)
            }
        }

        setDefaultAttributeIdentifiers()

        setLevelListOptions()
    }

    private fun existingLevels() = observationLevels.toList().intersect(observationUnits.flatMap { it.value }
        .map { it.observationUnitPosition.observationLevel.levelName }
        .toSet()).toTypedArray()

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

    private fun getAttributeKeys() = attributesTable?.values?.flatMap { it.values }?.flatMap { it.keys }?.distinct() ?: listOf()

    private fun setSortListOptions() {

        listView.visibility = View.VISIBLE

        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_single_choice,
            getAttributeKeys()
        )

        listView.setItemChecked(selectedSort, true)

        listView.smoothScrollToPosition(selectedSort)

        listView.setOnItemClickListener { _, _, position, _ ->

            selectedSort = if (selectedSort == position) {

                listView.setItemChecked(selectedSort, false)

                -1

            } else position
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
        val germs = germplasms[studyDbId] ?: listOf()

        val gson = Gson()

        observationUnits[studyDbId]?.forEach { unit ->

            val attributes = hashMapOf<String, String>()

            if (unit.germplasmName != null) {
                attributes["Germplasm"] = unit.germplasmName
            }

            unit.locationName?.takeIf { it.isNotEmpty() }?.let {
                attributes["Location"] = it
            }

            // Additional info is a Json object that can contain primitives or JSON objs/arrays.
            // In the case of the latter, add the String version of the JSON to attributes.

            unit.additionalInfo?.entrySet()?.forEach { (key, value) ->
                try {
                    val atr = when {
                        value.isJsonPrimitive -> value.asString
                        value.isJsonArray || value.isJsonObject -> gson.toJson(value)
                        else -> null
                        }
                    atr?.takeIf { it.isNotEmpty() }?.let {
                        attributes[key] = it
                        }
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

                germs.firstOrNull { it.germplasmDbId == unit.germplasmDbId }?.let { germ ->

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

    private fun loadStudyList(studyDbIds: List<String>) {

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
                return observationVariables[id]?.toHashSet()
            }

            override fun getObservationUnits(
                id: String,
                position: Int
            ): HashSet<BrAPIObservationUnit>? {
                return try {
                    val levels = existingLevels()
                    if (selectedLevel >= 0 && levels.isNotEmpty()) observationUnits[id]?.toHashSet()
                        ?.filter { it.observationUnitPosition.observationLevel.levelName == levels.elementAt(selectedLevel) }?.toHashSet()
                    else observationUnits[id]?.toHashSet()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get observation units", e)
                    hashSetOf()
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

            async {

                //fetch variables, units, and germs for all studies asynchronously
                studyModels.forEachIndexed { index, model ->

                    launch {
                        val job = fetchObservationVariables(model.id)
                        job.join()
                        withContext(Dispatchers.Main) {
                            studyList.adapter?.notifyItemChanged(index)
                        }
                    }
                }

            }.await()

            async {

                //fetch variables, units, and germs for all studies asynchronously
                studyModels.forEachIndexed { index, model ->

                    launch {
                        val job = fetchObservationUnits(model.id)
                        job.join()
                    }
                }

            }.await()

            async {

                //fetch variables, units, and germs for all studies asynchronously
                studyModels.forEachIndexed { index, model ->

                    launch {
                        val job = fetchGermplasm(model.id)
                        job.join()
                        withContext(Dispatchers.Main) {
                            studyList.adapter?.notifyItemChanged(index)
                            loadTabLayout(studyDbIds)
                        }
                    }
                }

            }.await()

            if ((studyList.adapter as StudyAdapter).currentList.any {
                    observationUnits[it.id]?.isEmpty() != false
                }) {

                Toast.makeText(this@BrapiStudyImportActivity,
                    getString(R.string.failed_to_fetch_observation_units), Toast.LENGTH_SHORT).show()

                onBackPressedDispatcher.onBackPressed()

            }

            importButton.isEnabled = true
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

                val allAttributes = getAttributeKeys()
                val sortOrder = if (selectedSort == -1) "" else allAttributes[selectedSort]

                studyDbIds.forEach { id ->

                    try {

                        studies.firstOrNull { it.studyDbId == id }?.let {

                            saveStudy(it, level, sortOrder)

                            successfullyImportedStudies.add(id) // track the successfully imported fields

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
                    val studyModel = db.getStudyByDbId(successfullyImportedStudies.first())
                    val fieldId = studyModel?.internal_id_study
                    resultIntent.putExtra("fieldId", fieldId ?: -1)
                }
                setResult(RESULT_OK, resultIntent)
                finish()

            }
        }
    }

    private fun saveStudy(
        study: BrAPIStudy,
        level: BrapiObservationLevel,
        sortId: String
    ) {

        var maxVariableIndex = db.maxPositionFromTraits + 1

        attributesTable?.get(study.studyDbId)?.let { studyAttributes ->

            observationUnits[study.studyDbId]?.filter {
                it.observationUnitPosition.observationLevel.levelName.equals(level.observationLevelName, ignoreCase = true)
            }
                ?.let { units ->

                    val details = BrapiStudyDetails()
                    details.studyDbId = study.studyDbId
                    details.studyName = study.studyName
                    details.commonCropName = study.commonCropName
                    details.numberOfPlots = units.size
                    details.trialName = study.trialName

                    details.traits = observationVariables[study.studyDbId]?.toList()
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

                    //primary/secondary no longer required
                    brapiService.saveStudyDetails(
                        details,
                        level,
                        "",
                        "",
                        sortId,
                    )
                }
        }
    }

    private suspend fun fetchObservationLevels(programDbId: String) = coroutineScope {

        Log.d(TAG, "Fetching levels for $programDbId")

        launch(Dispatchers.IO) {

            brapiService.getObservationLevels(null, { levels ->

                Log.d(TAG, "${levels.size} levels fetched")

                //log the levels
                levels.forEach { level ->
                    observationLevels.add(level.observationLevelName)
                    Log.d(TAG, "Level: ${level.observationLevelName}")
                }

            }) { _ ->
                //Toast.makeText(this, "Failed to fetch observation levels", Toast.LENGTH_SHORT).show()
                setResult(RESULT_CANCELED)
                finish()
            }

            while (observationLevels.isEmpty()) {
                ensureActive()
            }

            cancel()
        }
    }

    private suspend fun setProgress(progress: Int, progressMax: Int) {
        withContext(Dispatchers.Main) {
            progressBar.isIndeterminate = false
            progressBar.progress = progress
            progressBar.max = progressMax
        }
    }

    private suspend fun fetchGermplasm(studyDbId: String) = coroutineScope {

        val germs = arrayListOf<BrAPIGermplasm>()

        val pageSize = prefs.getString(PreferenceKeys.BRAPI_PAGE_SIZE, "512")?.toInt() ?: 512

        Log.d(TAG, "Fetching germplasm for $studyDbId")

        launch(Dispatchers.IO) {

            (brapiService as BrAPIServiceV2).germplasmService.fetchAll(GermplasmQueryParams()
                .also {
                    it.studyDbId(studyDbId)
                    it.pageSize(pageSize)
                })
                .catch {
                    Log.e(TAG, "Failed to fetch germplasm")
                    cancel()
                }
                .collect { result ->

                    val data = result as Pair<*, *>
                    val total = data.first as Int
                    val models = data.second as List<*>
                    models.forEach { unit ->

                        (unit as? BrAPIGermplasm)?.let { g ->
                            Log.d("Unit", g.germplasmName ?: "No name")
                            germs.add(g)
                            if (total == germs.size) {
                                germplasms.getOrPut(studyDbId) { hashSetOf() }
                                    .addAll(germs)
                                cancel()
                            }
                        }
                    }

                    if (models.isEmpty() && total == 0) {
                        cancel()
                    }
                }
        }
    }


    private suspend fun fetchObservationVariables(studyDbId: String) =
        coroutineScope {

            val models = arrayListOf<BrAPIObservationVariable>()

            val pageSize = prefs.getString(PreferenceKeys.BRAPI_PAGE_SIZE, "512")?.toInt() ?: 512

            launch(Dispatchers.IO) {
                (brapiService as BrAPIServiceV2).observationVariableService.fetchAll(
                    VariableQueryParams().also {
                        it.studyDbId(studyDbId)
                        it.pageSize(pageSize)
                    })
                    .catch {
                        Log.e(TAG, "Failed to fetch observation variables")
                        cancel()
                    }
                    .collect { variables ->

                    val data = variables as Pair<*, *>
                    val total = data.first as Int
                    val variableModels = data.second as List<*>
                    variableModels.forEach { model ->

                        (model as? BrAPIObservationVariable)?.let { variable ->

                            Log.d(TAG, "Variable: ${variable.observationVariableName}")

                            models.add(variable)
                            Log.d(TAG, "Variable: ${models.size}/$total")

                            if (total == models.size) {
                                observationVariables.getOrPut(studyDbId) { hashSetOf() }
                                    .addAll(models)

                                cancel()
                            }
                        }
                    }

                    if (models.isEmpty() && total == 0) {
                        cancel()
                    }
                }
            }
        }

    private suspend fun fetchObservationUnits(studyDbId: String) = coroutineScope {

        val units = arrayListOf<BrAPIObservationUnit>()

        val pageSize = prefs.getString(PreferenceKeys.BRAPI_PAGE_SIZE, "512")?.toInt() ?: 512

        launch(Dispatchers.IO) {

            (brapiService as BrAPIServiceV2).observationUnitService.fetchAll(
                ObservationUnitQueryParams().also {
                    it.studyDbId(studyDbId)
                    it.pageSize(pageSize)
                    //it.observationUnitLevelName("plot")
                }
            )
                .catch {
                    Log.e(TAG, "Failed to fetch observation units")
                    cancel()
                }
                .collect { response ->
                    Log.d("Unit", "Response")

                    val data = response as Pair<*, *>
                    val total = data.first as Int
                    val models = data.second as List<*>
                    models.forEach { unit ->

                        (unit as? BrAPIObservationUnit)?.let { u ->
                            Log.d("Unit", u.observationUnitName)
                            units.add(u)
                            if (total == units.size) {
                                observationUnits.getOrPut(studyDbId) { hashSetOf() }
                                    .addAll(units)
                                cancel()
                            }
                        }
                    }

                    if (models.isEmpty() && total == 0) {

                        cancel()
                    }
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }
}