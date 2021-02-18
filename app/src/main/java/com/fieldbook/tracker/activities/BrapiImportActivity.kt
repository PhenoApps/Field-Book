package com.fieldbook.tracker.activities

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.*
import androidx.core.os.postDelayed
import com.fieldbook.tracker.R
import com.fieldbook.tracker.brapi.BrapiLoadDialog
import com.fieldbook.tracker.brapi.model.BrapiProgram
import com.fieldbook.tracker.brapi.model.BrapiStudyDetails
import com.fieldbook.tracker.brapi.model.BrapiTrial
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory
import com.fieldbook.tracker.brapi.service.BrapiPaginationManager
import com.fieldbook.tracker.utilities.Utils
import com.fieldbook.tracker.views.PageControllerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.ArrayList

class BrapiImportActivity : Activity() {

    private companion object {

        val TAG = BrapiImportActivity::class.simpleName

        const val PROGRAMS = 0
        const val TRIALS = 1
        const val STUDIES = 2
    }

    /**
     * mFilterState is a state variable that represents which table we are filtering for.
     */
    private var mFilterState: Int = PROGRAMS

    /**
     * Used to search for studies across multiple programs and trials.
     */
    private data class ProgramTrialPair(val program: BrapiProgram, val trial: BrapiTrial)

    /**
     * Whenever a list row is chosen, it is added to a set of ids (or removed if already chosen).
     * When the next table is chosen, all the ids in the respective set will be used to query.
     */
    private var mProgramIds = HashSet<BrapiProgram>()
    private var mStudyIds = HashSet<BrapiStudyDetails>()
    private var mTrialIds = HashSet<ProgramTrialPair>()

    private val mScope by lazy {
        CoroutineScope(Dispatchers.IO)
    }

    private val mPageControllerView: PageControllerView by lazy {
        findViewById(R.id.pageControllerView)
    }

    private val mService by lazy {

        BrAPIServiceFactory.getBrAPIService(this)

    }

    private val mPaginationManager by lazy {

        BrapiPaginationManager(this)

    }

    private fun setupPageController() {

        mPageControllerView.setNextClick {

            mPaginationManager.setNewPage(mPageControllerView.nextButton.id)

            loadBrAPIData()

        }

        mPageControllerView.setPrevClick {

            mPaginationManager.setNewPage(mPageControllerView.prevButton.id)

            loadBrAPIData()

        }
    }

    /**
     * Recursive function that uses the BrapiLoadDialog to display information about the brapi study details
     * the user has selected. Each recursive call happens after a dialog is dismissed, as long as there are more studyDbIds.
     * Each recursive call removes a chosen studyDbId from the global set.
     */
    private fun importSelected() {

        val studyDbId = mStudyIds.firstOrNull()

        if (studyDbId != null) {

            mStudyIds.remove(studyDbId)

            mService.getPlotDetails(studyDbId.studyDbId, { details ->

                BrapiStudyDetails.merge(studyDbId, details)

                mService.getTraits(studyDbId.studyDbId, { traits ->

                    BrapiStudyDetails.merge(studyDbId, traits)

                    runOnUiThread {

                        BrapiLoadDialog(this).apply {

                            this.setOnDismissListener {

                                if (mStudyIds.isNotEmpty()) {

                                    importSelected()

                                } else Handler().postDelayed(1500) {
                                    //allow the async task some time before finishing activity
                                    //otherwise field editor activity won't populate the new study
                                    finish() //finish the activity
                                }
                            }

                            setSelectedStudy(studyDbId)

                            show()
                        }
                    }

                    null

                }) { fail -> handleFailure(fail) }

                null

            }) { fail -> handleFailure(fail) }

        } else runOnUiThread {

            Toast.makeText(applicationContext, R.string.no_studies_selected_warning, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * For this import activity, the top bar shows the current table we are filtering for,
     * which navigates to the previous table on click (like a back button).
     * Similarly, the bottom bar moves to the next table but is like a select all query if no fields are chosen.
     */
    private fun setupTopAndBottomButtons() {

        //query for the top and bottom text views
        val backButton = findViewById<Button>(R.id.currentButton)
        val forwardButton = findViewById<Button>(R.id.nextButton)

        backButton.setOnClickListener {
            onBackPressed()
        }

        forwardButton.setOnClickListener {
            //update the state (go forward one)
            when(mFilterState) {
                PROGRAMS -> mFilterState = TRIALS
                TRIALS -> mFilterState = STUDIES
                else -> importSelected()

            }

           loadBrAPIData()
        }

    }

    override fun onBackPressed() {
        //update the state (go back one)
        //reset respective sets
        when(mFilterState) {
            PROGRAMS -> super.onBackPressed()
            TRIALS -> {
                mTrialIds = HashSet<ProgramTrialPair>()
                mProgramIds = HashSet<BrapiProgram>()
                mFilterState = PROGRAMS
            }
            STUDIES -> {
                mTrialIds = HashSet<ProgramTrialPair>()
                mStudyIds = HashSet<BrapiStudyDetails>()
                mFilterState = TRIALS
            }
        }

        //update UI with the new state
        loadBrAPIData()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //check if device is connected to a network
        if (Utils.isConnected(this)) {

            //checks that the preference brapi url matches a web url
            if (BrAPIService.hasValidBaseUrl(this)) {

                //load ui
                setContentView(R.layout.activity_brapi_import)

                findViewById<TextView>(R.id.serverTextView).text = BrAPIService.getBrapiUrl(this)

                setupPageController()

                setupTopAndBottomButtons()

                loadBrAPIData()

            } else {

                Toast.makeText(applicationContext, R.string.brapi_must_configure_url, Toast.LENGTH_SHORT).show()

                finish()
            }

        } else {

            Toast.makeText(applicationContext, R.string.device_offline_warning, Toast.LENGTH_SHORT).show()

            finish()
        }
    }

    /**
     * Updates the top/bottom bar text fields with the current state.
     */
    private fun updateUi(state: Int) {

        mFilterState = state

        val pageTextView = findViewById<TextView>(R.id.currentButton)
        pageTextView.text = when (state) {
            PROGRAMS -> "Programs"
            TRIALS -> "Trials"
            else -> "Studies"
        }

        val nextTextView = findViewById<TextView>(R.id.nextButton)
        nextTextView.text = when (state) {
            PROGRAMS -> "Trials"
            TRIALS -> "Studies"
            else -> "Import Selected"
        }

    }

    //simply load all programs on the brapi server given a page
    private fun loadPrograms() {

        val listView = findViewById<ListView>(R.id.listView)

        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        mService.getPrograms(mPaginationManager, { programs ->

            buildArrayAdapter(programs)

            null

        }) { fail -> handleFailure(fail) }
    }

    /**
     * If no programs were chosen, this function loads all trials in the brapi server.
     * Otherwise, load the trials within the chosen programs.
     */
    private fun loadTrials() {

        mPaginationManager.reset()

        val brapiTrials = ArrayList<ProgramTrialPair>()

        if (mProgramIds.isEmpty()) {

            mService.getPrograms(mPaginationManager, { programs ->

                programs.forEach { program ->

                    callGetTrials(brapiTrials, program)
                }

                null

            }) { fail -> handleFailure(fail) }

        } else mProgramIds.forEach { program ->//iterate over all programs selected by the user

           callGetTrials(brapiTrials, program)

        }
    }

    private fun callGetTrials(programTrials: ArrayList<ProgramTrialPair>, program: BrapiProgram) {

        //query for all trials within all selected programs and add them to a list
        mService.getTrials(program.programDbId, mPaginationManager, { trials ->

            programTrials.addAll(trials.mapNotNull { ProgramTrialPair(program, it) }.toTypedArray())

            buildArrayAdapter(programTrials)

            null // brapi calls require a return type of Void

        }) { fail -> handleFailure(fail) }
    }

    private fun callGetStudies(studies: ArrayList<BrapiStudyDetails>, program: BrapiProgram, trial: BrapiTrial) {

        //query for all trials within all selected programs and add them to a list
        mService.getStudies(program.programDbId, trial.trialDbId, mPaginationManager, { data ->

            studies.addAll(data.mapNotNull { it }.toTypedArray())

            buildArrayAdapter(studies)

            null // brapi calls require a return type of Void

        }) { fail -> handleFailure(fail) }
    }

    /**
     * Loads all studies in the chosen trials or loads all studies in the database for a given page.
     */
    private fun loadStudies() {

        mPaginationManager.reset()

        val brapiStudies = ArrayList<BrapiStudyDetails>()

        //check if both sets are empty, otherwise the user has chosen programs and no trials exist for that program
        when {

            mProgramIds.isEmpty() -> {

                //check all combinations or program/trials
                mService.getPrograms(mPaginationManager, { programs ->

                    programs.forEach { program ->

                        mService.getTrials(program.programDbId, mPaginationManager, { trials ->

                            trials.forEach { trial ->

                                callGetStudies(brapiStudies, program, trial)
                            }

                            null

                        }) { fail -> handleFailure(fail) }
                    }

                    null

                }) { fail -> handleFailure(fail) }
            }

            mTrialIds.isEmpty() -> {

                //check all trials in the chosen programs
                mProgramIds.forEach { program ->

                    mService.getTrials(program.programDbId, mPaginationManager, { trials ->

                        trials.forEach { trial ->

                            callGetStudies(brapiStudies, program, trial)
                        }

                        null

                    }) { fail -> handleFailure(fail) }
                }

            }

            else -> mTrialIds.forEach { pair -> //iterate over all program/trials selected by the user

                callGetStudies(brapiStudies, pair.program, pair.trial)

            }
        }
    }

    /**
     * Logs each time a failure api callback occurs
     */
    private fun handleFailure(fail: Int): Void? {

        Log.d(TAG, "BrAPI callback failed. $fail")

        return null //default fail callback return type for brapi

    }

    //async loads the brapi calls using coroutines
    //updates the top/bottom bar text fields depending on the state
    private fun loadBrAPIData() {

        updateUi(mFilterState)

        mScope.launch { //uses Dispatchers.IO for network background processing

            try {

                when (mFilterState) {
                    PROGRAMS -> loadPrograms()
                    TRIALS -> loadTrials()
                    STUDIES -> loadStudies()
                }

            } catch (cme: ConcurrentModificationException) {

                Log.d(TAG, cme.localizedMessage ?: "Async update error.")

                cme.printStackTrace()

            }
        }
    }

    //function used to toggle existence of an item in a set
    private fun <T> HashSet<T>.addOrRemove(item: T) {
        if (this.contains(item)) {
            this.remove(item)
        } else this.add(item)
    }

    /**
     * Updates the UI with the data parameter. Type T can be BrapiProgram, BrapiStudyDetails,
     * or ProgramTrialPair. All of which contain information to reconstruct the filter tree
     * from user input.
     */
    private fun <T> buildArrayAdapter(data: List<T>) {

        val listView = findViewById<ListView>(R.id.listView)

        //set up list item click event listener
        listView.setOnItemClickListener { _, _, position, _ ->

            when (val item = data[position]) {

                is BrapiProgram -> mProgramIds.addOrRemove(item)
                is ProgramTrialPair -> mTrialIds.addOrRemove(item)
                is BrapiStudyDetails -> mStudyIds.addOrRemove(item)

            }
        }

        val itemDataList: MutableList<Any?> = ArrayList()

        //load data into adapter
        data.forEach {

            when (it) {

                is BrapiProgram -> it.programName?.let { name -> itemDataList.add(name)  }
                is ProgramTrialPair -> it.trial.trialName?.let { name -> itemDataList.add(name) }
                is BrapiStudyDetails -> it.studyName?.let { name -> itemDataList.add(name) }

            }
        }

        runOnUiThread {

            listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, itemDataList)

        }
    }
}