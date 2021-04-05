package com.fieldbook.tracker.fragments

import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.core.os.postDelayed
import com.fieldbook.tracker.R
import com.fieldbook.tracker.brapi.BrapiLoadDialog
import com.fieldbook.tracker.brapi.model.BrapiStudyDetails
import com.fieldbook.tracker.brapi.service.BrAPIService
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.brapi.v2.model.core.response.BrAPIStudyListResponse
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

@KtorExperimentalAPI
class BrapiStudiesFragment: BaseBrapiFragment() {

    private var mStudies = HashSet<BrapiStudyDetails>()

    companion object {

        val TAG = BrapiStudiesFragment::class.simpleName
    }

    /**
     * For this import activity, the top bar shows the current table we are filtering for,
     * which navigates to the previous table on click (like a back button).
     * Similarly, the bottom bar moves to the next table but is like a select all query if no fields are chosen.
     */
    private fun setupTopAndBottomButtons() {

        mPaginationManager.reset()

        findViewById<Button>(R.id.currentButton)?.setOnClickListener {
            //update UI with the new state
            loadBrAPIData()
        }

        findViewById<Button>(R.id.nextButton)?.setOnClickListener {

          importSelected()
        }
    }

    data class SearchResult(val searchResultDbId: String? = null)
    data class SearchRequest(val page: Int, val pageSize: Int, val result: SearchResult? = null, val trialDbIds: Array<String>? = null) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as SearchRequest

            if (page != other.page) return false
            if (pageSize != other.pageSize) return false
            if (result != other.result) return false
            if (trialDbIds != null) {
                if (other.trialDbIds == null) return false
                if (!trialDbIds.contentEquals(other.trialDbIds)) return false
            } else if (other.trialDbIds != null) return false

            return true
        }

        override fun hashCode(): Int {
            var result1 = page
            result1 = 31 * result1 + pageSize
            result1 = 31 * result1 + (result?.hashCode() ?: 0)
            result1 = 31 * result1 + (trialDbIds?.contentHashCode() ?: 0)
            return result1
        }
    }

    private fun callSearchStudies(trials: List<String>) {

        switchProgress()

        mScope.launch {

            val response = withContext(Dispatchers.Default) {

                httpClient.post<SearchRequest> {

                    url("${BrAPIService.getBrapiUrl(applicationContext)}/search/studies/")

                    contentType(ContentType.Application.Json)

                    body = SearchRequest(mPaginationManager.page, mPaginationManager.pageSize,
                            trialDbIds = trials.toTypedArray())

                }

            }

            switchProgress()

            if (response.result?.searchResultDbId == null) {

                fastSearchTrials(trials)

            } else {

                getTrialSearchRequestData(response.result.searchResultDbId)
            }
        }
    }

    private fun getTrialSearchRequestData(searchResultDbId: String) {

        switchProgress()

        mScope.launch {

            val response = withContext(Dispatchers.Default) {

                httpClient.get<BrAPIStudyListResponse> {

                    url("${BrAPIService.getBrapiUrl(applicationContext)}/search/studies/$searchResultDbId")

                }
            }

            switchProgress()

            buildArrayAdapter(response.result.data.map {
                BrapiStudyDetails().apply {
                    studyDbId = it.studyDbId
                    studyName = it.studyName
                    studyDescription = it.studyDescription
                    studyLocation = it.locationName
                    commonCropName = it.commonCropName
                }
            })
        }
    }

    /**
     * Recursive function that uses the BrapiLoadDialog to display information about the brapi study details
     * the user has selected. Each recursive call happens after a dialog is dismissed, as long as there are more studyDbIds.
     * Each recursive call removes a chosen studyDbId from the global set.
     */
    private fun importSelected() {

        switchProgress()

        val studyDbId = mStudies.firstOrNull()

        if (studyDbId != null) {

            mStudies.remove(studyDbId)

            mService.getPlotDetails(studyDbId.studyDbId, { details ->

                BrapiStudyDetails.merge(studyDbId, details)

                mService.getTraits(studyDbId.studyDbId, { traits ->

                    BrapiStudyDetails.merge(studyDbId, traits)

                    runOnUiThread {

                        switchProgress()

                        BrapiLoadDialog(this).apply {

                            this.setOnDismissListener {

                                if (mStudies.isNotEmpty()) {

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
     * Uses brapi client to do a fast search on trials. Works on test-server but not guaranteed for other servers.
     */
    private fun fastSearchTrials(trials: List<String>) {

        mService.searchStudies(listOf(), trials, mPaginationManager, { it ->

            buildArrayAdapter(it)

            null

        }) { fail -> handleFailure(fail) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findViewById<Button>(R.id.currentButton)?.text = "Studies"

        findViewById<Button>(R.id.nextButton)?.text = "Import"

        setupTopAndBottomButtons()

    }

    //load and display programs
    override fun loadBrAPIData() {

        mScope.launch { //uses Dispatchers.IO for network background processing

            try {

                val trialIds = intent.getStringArrayListExtra(BrapiTrialsFragment.KEY_TRIALS_ARRAY)

                trialIds?.let {

                    callSearchStudies(it)

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

                is BrapiStudyDetails -> mStudies.addOrRemove(item)
            }
        }

        val itemDataList: MutableList<Any?> = ArrayList()

        //load data into adapter
        data.forEach {

            when (it) {

                is BrapiStudyDetails -> it.studyName?.let { name -> itemDataList.add(name) }

            }
        }

        runOnUiThread {

            listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, itemDataList)

        }
    }
}