package com.fieldbook.tracker.fragments

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.fragment.app.*
import com.fieldbook.tracker.R
import com.fieldbook.tracker.brapi.model.BrapiProgram
import com.fieldbook.tracker.brapi.model.BrapiTrial
import com.fieldbook.tracker.brapi.service.BrAPIService
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import io.swagger.client.model.Metadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.brapi.v2.model.core.response.BrAPIProgramListResponse
import org.brapi.v2.model.core.response.BrAPIProgramListResponseResult
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

@KtorExperimentalAPI
class BrapiProgramsFragment: BaseBrapiFragment() {

    private var mPrograms = HashSet<BrapiProgram>()

    companion object {

        val TAG = BrapiProgramsFragment::class.simpleName

        const val TRIALS_REQUEST = 801
        const val KEY_PROGRAMS_ARRAY = "org.phenoapps.brapi.programs_bundle"
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
                setClassName(this@BrapiProgramsFragment, BrapiTrialsFragment::class.java.name)
                putStringArrayListExtra(KEY_PROGRAMS_ARRAY, ArrayList(mPrograms.map { it.programDbId }))
            }

            startActivityForResult(i, TRIALS_REQUEST)

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findViewById<Button>(R.id.currentButton)?.text = getString(R.string.programs)

        findViewById<Button>(R.id.nextButton)?.text = getString(R.string.trials)

        setupTopAndBottomButtons()
    }

    private fun loadPrograms(names: List<String>?, page: Int? = null) {

        switchProgress()

        mScope.launch {

            val response = withContext(Dispatchers.Default) {

                httpClient.get<BrAPIProgramListResponse> {

                    val baseUrl = BrAPIService.getBrapiUrl(applicationContext)
                    val urlParams = (names?.joinToString("&")
                        { "programName=${URLEncoder.encode(it, "UTF-8")}" }) ?: ""
                    if (page == null) {
                        url("$baseUrl/programs?$urlParams")
                    } else url("$baseUrl/programs$urlParams&page=$page")
                }
            }

            switchProgress()

            runOnUiThread {
                mPaginationManager.updatePageInfo(response.metadata?.pagination?.totalPages)

                buildArrayAdapter(response.result.data.map {
                    BrapiProgram().apply {
                        this.programDbId = it.programDbId
                        this.programName = it.programName
                    }
                })
            }
        }
    }

    //load and display programs
    override fun loadBrAPIData(names: List<String>?) {

        mScope.launch { //uses Dispatchers.IO for network background processing

            try {

                loadPrograms(names)

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
        listView?.setOnItemClickListener { _, _, position, _ ->

            when (val item = data[position]) {

                is BrapiProgram -> mPrograms.addOrRemove(item)

            }
        }

        val itemDataList: MutableList<Any?> = ArrayList()

        //load data into adapter
        data.forEach {

            when (it) {

                is BrapiProgram -> it.programName?.let { name -> itemDataList.add(name) }

            }
        }

        this.let { ctx ->

            this@BrapiProgramsFragment.runOnUiThread {

                listView?.adapter = ArrayAdapter(ctx, android.R.layout.simple_list_item_multiple_choice, itemDataList)

            }
        }
    }
}