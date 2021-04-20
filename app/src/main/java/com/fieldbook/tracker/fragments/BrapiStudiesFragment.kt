package com.fieldbook.tracker.fragments

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.os.postDelayed
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ConfigActivity
import com.fieldbook.tracker.brapi.BrapiLoadDialog
import com.fieldbook.tracker.brapi.model.BrapiStudyDetails
import com.fieldbook.tracker.brapi.model.BrapiTrial
import com.fieldbook.tracker.brapi.service.BrAPIService
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import io.swagger.client.model.Metadata
import io.swagger.client.model.StudyResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.brapi.v2.model.core.response.BrAPIStudyListResponse
import org.brapi.v2.model.core.response.BrAPIStudyListResponseResult
import org.brapi.v2.model.core.response.BrAPITrialListResponse
import java.net.URLEncoder
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

@KtorExperimentalAPI
class BrapiStudiesFragment: BaseBrapiFragment() {

    private var mStudies = HashSet<BrapiStudyDetails>()

    companion object {

        val TAG = BrapiStudiesFragment::class.simpleName

        const val CONFIG_REQUEST = 110
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

    private val studiesCache = HashMap<Int, BrAPIStudyListResponse?>()
    private fun callSearchStudies(names: List<String>?, trials: List<String>?, page: Int = 0) {

        val pageSize = mPaginationManager.pageSize

        switchProgress()

        mScope.launch {

            for (p in page until page+pageSize) {

                if (p !in studiesCache.keys) {
                    studiesCache[p] = withContext(mScope.coroutineContext) {

                        httpClient.get<BrAPIStudyListResponse> {

                            val currentPage = page?.toString() ?: ""
                            val baseUrl = BrAPIService.getBrapiUrl(applicationContext)

                            val urlParams = (names?.joinToString("&")
                            { "studyName=${URLEncoder.encode(it, "UTF-8")}" }) ?: ""

                            val trialUrlParams = (trials?.joinToString("&") { "trialDbId=$it" }) ?: ""

                            url("$baseUrl/studies?$urlParams&$trialUrlParams&page=$p")
                        }
                    }
                }
            }

            switchProgress()

            runOnUiThread {
                mPaginationManager.updatePageInfo(studiesCache[page]?.metadata?.pagination?.totalPages ?: 1)

                buildArrayAdapter(studiesCache[page]?.result?.data?.map {
                    BrapiStudyDetails().apply {
                        this.studyDbId = it.studyDbId
                        this.studyName = it.studyName
                        this.studyLocation = it.locationName
                        this.commonCropName = it.commonCropName
                        this.studyDescription = it.studyDescription
                        //this.attributes = it.additionalInfo
                    }
                })
            }
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
                                    val i = Intent().apply {
                                        setClassName(this@BrapiStudiesFragment, ConfigActivity::class.java.name)
                                    }

                                    startActivityForResult(i, CONFIG_REQUEST)                                }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findViewById<Button>(R.id.currentButton)?.text = getString(R.string.studies)

        findViewById<Button>(R.id.nextButton)?.text = getString(R.string.import_title)

        setupTopAndBottomButtons()

    }

    //load and display programs
    override fun loadBrAPIData(names: List<String>?) {

        mScope.launch { //uses Dispatchers.IO for network background processing

            try {

                val trialIds = intent.getStringArrayListExtra(BrapiTrialsFragment.KEY_TRIALS_ARRAY)

                trialIds?.let {

                    callSearchStudies(names, it, mPaginationManager.page)

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
    private fun <T> buildArrayAdapter(data: List<T>?) {

        if (data == null || data.isEmpty()) {
            Toast.makeText(applicationContext, R.string.import_error_or_empty_response, Toast.LENGTH_SHORT).show()
            return
        }

        val listView = findViewById<ListView>(R.id.listView)

        //set up list item click event listener
        listView.setOnItemClickListener { _, _, position, _ ->

            when (val item = data.get(position)) {

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