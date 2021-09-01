package com.fieldbook.tracker.activities

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.cropontology.CropontologyDatabase
import com.fieldbook.tracker.cropontology.viewmodels.VariableViewModel
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.SimpleListAdapter
import com.fieldbook.tracker.cropontology.models.RankedVariable
import com.fieldbook.tracker.cropontology.tables.Variable
import com.fieldbook.tracker.database.dao.ObservationVariableDao
import com.fieldbook.tracker.interfaces.OnOntologyVariableClicked
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.utilities.CropontologyImporter.Companion.getAll
import com.google.gson.Gson
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CropontologyActivity : AppCompatActivity(), CoroutineScope by MainScope(),
    OnOntologyVariableClicked {

    private val mDatabase by lazy {
        CropontologyDatabase.getInstance(this)
    }

    private val mViewModel by lazy {
        VariableViewModel(mDatabase.variablesFtsDao())
    }

    private val mRecycler by lazy {
        findViewById<RecyclerView>(R.id.act_main_results_rv)
    }

    private val mSearchText by lazy {
        findViewById<EditText>(R.id.act_main_search_et)
    }

    private val mMatchedColumnsText by lazy {
        findViewById<TextView>(R.id.act_main_search_tv)
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_cropontology)

        launch(Dispatchers.IO) {

            val client = HttpClient(CIO) {
                install(JsonFeature)
                install(HttpTimeout) {
                    requestTimeoutMillis = 60000
                }
            }

            mDatabase.variablesFtsDao().rebuild()

            val url = "https://www.cropontology.org/brapi/v1/variables"

            val jsonResponse = client.getAll(url, 1)
            //val response: HttpResponse = client.get("https://test-server.brapi.org/brapi/v2/observations")

            //println(response.readText())
            jsonResponse.forEach {
                println("Page: ${it.metadata?.pagination?.currentPage}")
                it.result?.data?.map { Gson().fromJson(it.toString(), Variable::class.java) }?.forEach {
                    mDatabase.variablesDao().insert(it)
                }
            }

            client.close()

            runOnUiThread {

                startSearchViews()

            }
        }
    }

    //https://stackoverflow.com/questions/63654824/how-to-fix-the-error-wrong-number-of-arguments-to-function-rank-on-sqlite-an
    private fun rank(matchInfo: IntArray): Double {
        val numPhrases = matchInfo[0]
        val numColumns = matchInfo[1]

        var score = 0.0
        for (phrase in 0 until numPhrases) {
            val offset = 2 + phrase * numColumns * 3
            for (column in 0 until numColumns) {
                val numHitsInRow = matchInfo[offset + 3 * column]
                val numHitsInAllRows = matchInfo[offset + 3 * column + 1]
                if (numHitsInAllRows > 0) {
                    score += numHitsInRow.toDouble() / numHitsInAllRows.toDouble()
                }
            }
        }

        return score
    }

    fun ByteArray.skip(skipSize: Int): IntArray {
        val cleanedArr = IntArray(this.size / skipSize)
        var pointer = 0
        for (i in this.indices step skipSize) {
            cleanedArr[pointer] = this[i].toInt()
            pointer++
        }

        return cleanedArr
    }

    private fun startSearch(query: String) {

        mViewModel.searchResult(query).observe(this) {

            val obs: List<RankedVariable> = it.sortedByDescending { result -> rank(result.matchInfo.skip(4)) }

            (mRecycler.adapter as SimpleListAdapter).submitList(
                obs.mapIndexed { index, x ->

                    val intBuffer = ByteBuffer.wrap(x.matchInfo)
                        .order(ByteOrder.nativeOrder())
                        .asIntBuffer()
                    val intArray = IntArray(intBuffer.remaining())
                    intBuffer.get(intArray)

                    //println(intArray.map { it })
                    //https://www.sqlite.org/fts3.html#matchinfo
                    //last of the array is splits into 3*cols*phrases chunks
                    //matchinfo is called with 'pcx' which means
                    //p = phrases is the first returned integer shows #matched phrases
                    //c = columns is the second returned integer shows defined columns in fts
                    val phrases = intArray[0]
                    val columns = intArray[1]

                    //println("Matchable phrases: $phrases")
                    //println("Columns: $columns")

                    //user defined columns
                    //array of readable column names to show user which ones matched their query
                    val columnNames = arrayOf(
                        "Name", "Ontology",
                        "Scale", "Method",
                        "Institution", "Scientist",
                        "Crop", "Description", "Method Class",
                        "traitName", "traitClass", "traitDescription",
                        "entity", "attribute")

                    val matchedColumns = arrayListOf<String>() //save matched columns to display

                    var i = 2 //start index past p and c
                    for (p in 0 until phrases) { //loop through each phrase

                        for (c in 0 until columns) { //loop through all columns for each phrase

                            val name = columnNames[c]
                            //matchinfo 'x' prints three values per phrase/column combination
                            val hitsThisRow = intArray[i]
                            val hitsAllRows = intArray[i + 1]
                            val docsWithHits = intArray[i + 2]

                            if (hitsThisRow > 0) {

                                matchedColumns.add(name)
                                //println("$name matched query and found $docsWithHits documents.")
                            }

                            i += 3
                        }
                    }

                    mMatchedColumnsText.text = "Matched columns: ${matchedColumns.joinToString(", ")}"

                    x.variable //end to map that returns the variable model
                }
            )

            mRecycler.adapter?.notifyDataSetChanged()
            mRecycler.scheduleLayoutAnimation()
        }
    }

    private fun startSearchViews() {

        mRecycler.adapter = SimpleListAdapter(this@CropontologyActivity)
        mRecycler.layoutManager = LinearLayoutManager(this@CropontologyActivity)
        mSearchText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

                val text = s?.toString() ?: ""

                startSearch(text)
            }

            override fun afterTextChanged(s: Editable?) {}

        })
    }

    override fun onDestroy() {
        super.onDestroy()
        mDatabase.close()
    }

    override fun onItemClicked(ontology: Variable) {

        ObservationVariableDao.insertTraits(TraitObject().apply {
            trait = ontology.name
            format = when (ontology.scale?.dataType) {
                "Numerical" -> "numeric"
                "Nominal", "Ordinal" -> "categorical"
                else -> "text"
            }
            defaultValue = ontology.defaultValue
            minimum = ontology.scale?.validValues?.min
            maximum = ontology.scale?.validValues?.max
            details = ontology.toString()
            categories = ontology.scale?.validValues?.categories?.joinToString("/")
            visible = true
            externalDbId = ontology.ontologyDbId
            traitDataSource = "Cropontology"
        })
    }
}
