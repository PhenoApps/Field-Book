package com.fieldbook.tracker.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.fragment.app.*
import com.fieldbook.tracker.R
import com.fieldbook.tracker.brapi.model.BrapiTrial
import com.fieldbook.tracker.brapi.service.BrAPIService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.auth.*
import io.ktor.client.features.auth.providers.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.brapi.v2.model.core.response.BrAPITrialListResponse
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

class BrapiTrialsFragment: BaseBrapiFragment() {

    private var mTrials = HashSet<BrapiTrial>()

    companion object {

        val TAG = BrapiTrialsFragment::class.simpleName

        const val STUDIES_REQUEST = 802
        const val KEY_TRIALS_ARRAY = "org.phenoapps.brapi.trials_bundle"
    }
    /**
     * For this import activity, the top bar shows the current table we are filtering for,
     * which navigates to the previous table on click (like a back button).
     * Similarly, the bottom bar moves to the next table but is like a select all query if no fields are chosen.
     */
    @KtorExperimentalAPI
    private fun setupTopAndBottomButtons() {

        mPaginationManager.reset()

        findViewById<Button>(R.id.currentButton)?.setOnClickListener {
            //update UI with the new state
            loadBrAPIData()
        }

        findViewById<Button>(R.id.nextButton)?.setOnClickListener {

            val i = Intent().apply {
                setClassName(this@BrapiTrialsFragment, BrapiStudiesFragment::class.java.name)
                putStringArrayListExtra(KEY_TRIALS_ARRAY, ArrayList(mTrials.map { it.trialDbId }))
            }

            startActivityForResult(i, STUDIES_REQUEST)
        }
    }

    data class SearchResult(val searchResultDbId: String? = null)
    data class TrialSearchRequest(val page: Int, val pageSize: Int, val result: SearchResult? = null, val programDbIds: Array<String>? = null) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TrialSearchRequest

            if (page != other.page) return false
            if (pageSize != other.pageSize) return false
            if (result != other.result) return false
            if (programDbIds != null) {
                if (other.programDbIds == null) return false
                if (!programDbIds.contentEquals(other.programDbIds)) return false
            } else if (other.programDbIds != null) return false

            return true
        }

        override fun hashCode(): Int {
            var result1 = page
            result1 = 31 * result1 + pageSize
            result1 = 31 * result1 + (result?.hashCode() ?: 0)
            result1 = 31 * result1 + (programDbIds?.contentHashCode() ?: 0)
            return result1
        }
    }

    @KtorExperimentalAPI
    private fun callSearchTrials(programs: List<String>) {

        switchProgress()

        mScope.launch {

            val response = withContext(Dispatchers.Default) {

                httpClient.post<TrialSearchRequest> {

                    url("${BrAPIService.getBrapiUrl(applicationContext)}/search/trials/")

                    contentType(ContentType.Application.Json)

                    body = TrialSearchRequest(mPaginationManager.page, mPaginationManager.pageSize,
                            programDbIds = programs.toTypedArray())

                }

            }

            switchProgress()

            if (response.result?.searchResultDbId == null) {

                fastSearchTrials(programs)

            } else {

                getTrialSearchRequestData(response.result.searchResultDbId)
            }
        }
    }

    @KtorExperimentalAPI
    private fun getTrialSearchRequestData(searchResultDbId: String) {

        switchProgress()

        mScope.launch {

            val response = withContext(Dispatchers.Default) {

                httpClient.get<BrAPITrialListResponse> {

                    url("${BrAPIService.getBrapiUrl(applicationContext)}/search/trials/$searchResultDbId")

                }
            }

            switchProgress()

            buildArrayAdapter(response.result.data.map {
                BrapiTrial().apply {
                    trialDbId = it.trialDbId
                    trialName = it.trialName
                }
            })
        }
    }

    /**
     * Uses brapi client to do a fast search on trials. Works on test-server but not guaranteed for other servers.
     */
    private fun fastSearchTrials(programs: List<String>) {

        mService.searchTrials(programs, mPaginationManager, { trials ->

            buildArrayAdapter(trials)

            null

        }) { fail -> handleFailure(fail) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findViewById<Button>(R.id.currentButton)?.text = "Trials"

        findViewById<Button>(R.id.nextButton)?.text = "Studies"

        setupTopAndBottomButtons()

    }

    //load and display programs
    @KtorExperimentalAPI
    override fun loadBrAPIData() {

        mScope.launch { //uses Dispatchers.IO for network background processing

            try {

                val programIds = intent.getStringArrayListExtra(BrapiProgramsFragment.KEY_PROGRAMS_ARRAY)

                programIds?.let {

                    callSearchTrials(it)

                }

            } catch (cme: ConcurrentModificationException) {

                Log.d(TAG, cme.localizedMessage ?: "Async update error.")

                cme.printStackTrace()

            }
        }
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

                is BrapiTrial -> mTrials.addOrRemove(item)
            }
        }

        val itemDataList: MutableList<Any?> = ArrayList()

        //load data into adapter
        data.forEach {

            when (it) {

                is BrapiTrial -> it.trialName?.let { name -> itemDataList.add(name) }

            }
        }

        runOnUiThread {

            listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, itemDataList)

        }
    }
}