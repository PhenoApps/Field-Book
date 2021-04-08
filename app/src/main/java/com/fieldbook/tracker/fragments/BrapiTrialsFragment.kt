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
import io.swagger.client.model.Metadata
import io.swagger.client.model.MetadataPagination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.brapi.v2.model.BrAPIAcceptedSearchResponse
import org.brapi.v2.model.core.BrAPITrial
import org.brapi.v2.model.core.response.BrAPIListResponse
import org.brapi.v2.model.core.response.BrAPITrialListResponse
import org.brapi.v2.model.core.response.BrAPITrialListResponseResult
import java.net.URLEncoder
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

@KtorExperimentalAPI
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
    private fun setupTopAndBottomButtons() {

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

    private fun callSearchTrials(names: List<String>?, programs: List<String>?, page: Int? = null) {

        switchProgress()

        mScope.launch {

            val response = withContext(mScope.coroutineContext) {

                httpClient.get<BrAPITrialListResponse> {

                    val currentPage = page?.toString() ?: ""
                    val baseUrl = BrAPIService.getBrapiUrl(applicationContext)

                    val urlParams = (names?.joinToString("&")
                        { "trialName=${URLEncoder.encode(it, "UTF-8")}" }) ?: ""

                    val programsUrlParams = (programs?.joinToString("&") { "programDbId=$it" }) ?: ""

                    url("$baseUrl/trials?$urlParams&$programsUrlParams&page=$currentPage")
                }
            }

            switchProgress()

            runOnUiThread {
                mPaginationManager.updatePageInfo(response.metadata?.pagination?.totalPages)

                buildArrayAdapter(response.result.data.map {
                    BrapiTrial().apply {
                        this.trialName = it.trialName
                        this.trialDbId = it.trialDbId
                    }
                })
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findViewById<Button>(R.id.currentButton)?.text = getString(R.string.trials)

        findViewById<Button>(R.id.nextButton)?.text = getString(R.string.studies)

        setupTopAndBottomButtons()

    }

    override fun loadBrAPIData(names: List<String>?) {

        mScope.launch { //uses Dispatchers.IO for network background processing

            try {

                val programIds = intent.getStringArrayListExtra(BrapiProgramsFragment.KEY_PROGRAMS_ARRAY)

                programIds?.let {

                    callSearchTrials(names, it, mPaginationManager.page)

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

            listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, itemDataList)

        }
    }
}