package com.fieldbook.tracker.fragments

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.fragment.app.*
import com.fieldbook.tracker.R
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2
import com.fieldbook.tracker.brapi.service.BrapiPaginationManager
import com.fieldbook.tracker.brapi.typeadapters.LocalDateTypeAdapter
import com.fieldbook.tracker.brapi.typeadapters.OffsetDateTimeTypeAdapter
import com.fieldbook.tracker.views.PageControllerView
import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.JsonSerializer
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.threeten.bp.LocalDateTime
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*

@KtorExperimentalAPI
abstract class BaseBrapiFragment: FragmentActivity() {

    companion object {

        val TAG = BaseBrapiFragment::class.simpleName

    }

    protected val mScope by lazy {
        CoroutineScope(Dispatchers.IO)
    }

    protected val mService: BrAPIServiceV2 by lazy {

        BrAPIServiceV2(this@BaseBrapiFragment)

    }

    protected val mPaginationManager by lazy {

        BrapiPaginationManager(this)

    }

    //uses brapi v2 client json serializer
    //if the client receives malformed json it will cancel the response
    @RequiresApi(Build.VERSION_CODES.O)
    class Serializer : JsonSerializer {

        private val backend = org.brapi.client.v2.JSON().apply {
            this.gson = this.gson.newBuilder()
                    .registerTypeAdapter(LocalDateTime::class.java,
                            LocalDateTypeAdapter(DateTimeFormatter.ISO_LOCAL_DATE))
                    .registerTypeAdapter(OffsetDateTime::class.java,
                            OffsetDateTimeTypeAdapter(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    .create()
        }


        override fun write(data: Any, contentType: ContentType): OutgoingContent =
                TextContent(backend.serialize(data), contentType)

        override fun read(type: TypeInfo, body: Input): Any {
            val text = body.readText()
            return backend.deserialize(text, type.reifiedType)
        }
    }

    protected var httpClient = HttpClient(CIO) {

        install(JsonFeature) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                serializer = Serializer()
            } else serializer = GsonSerializer()
        }
        expectSuccess = false
        engine {
            maxConnectionsCount = 32
            requestTimeout = 1000000
        }
    }

    private fun setupPageController() {

        with(findViewById<PageControllerView>(R.id.pageControllerView)) {

            this?.setNextClick {

                mPaginationManager.setNewPage(nextButton.id)

                loadBrAPIData()

            }

            this?.setPrevClick {

                mPaginationManager.setNewPage(prevButton.id)

                loadBrAPIData()

            }
        }
    }

    //allow user to type ',' delimited names and search for them
    private fun setupSearch() {

        val searchEditText = findViewById<EditText>(R.id.nameEditText)
        val searchButton = findViewById<Button>(R.id.searchButton)

        searchButton.setOnClickListener {

            mPaginationManager.reset()

            searchEditText.text?.toString()?.let { namesDelimited ->
                val tokens = namesDelimited.split(",")
                if (tokens.isNotEmpty()) {
                    loadBrAPIData(tokens)
                } else loadBrAPIData(listOf(namesDelimited))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        httpClient = HttpClient(CIO) {

            install(JsonFeature) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    serializer = Serializer()
                } else serializer = GsonSerializer()
            }
            expectSuccess = false
            engine {
                maxConnectionsCount = 32
                requestTimeout = 1000000
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        httpClient.close()
    }

    override fun onPause() {
        super.onPause()
        httpClient.close()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //check if device is connected to a network
        if (isConnected(this)) {

            //checks that the preference brapi url matches a web url
            if (BrAPIService.hasValidBaseUrl(this)) {

                //set transitions between fragments
                overridePendingTransition(R.anim.slide_in_bottom, R.anim.slide_out_top)

                setContentView(R.layout.activity_brapi_import)

                findViewById<TextView>(R.id.serverTextView)?.text = BrAPIService.getBrapiUrl(this)

                findViewById<ListView>(R.id.listView)?.choiceMode = ListView.CHOICE_MODE_MULTIPLE

                setupPageController()

                setupSearch()

                loadBrAPIData()

            } else {

                Toast.makeText(this, R.string.brapi_must_configure_url, Toast.LENGTH_SHORT).show()

                finish()
            }

        } else {

            Toast.makeText(this, R.string.device_offline_warning, Toast.LENGTH_SHORT).show()

            finish()
        }

    }

    /**
     * TODO: This is what field book uses, might need to be updated
     */
    private fun isConnected(context: Context): Boolean {

        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkInfo = connMgr.activeNetworkInfo

        return networkInfo != null && networkInfo.isConnected
    }

    /**
     * Logs each time a failure api callback occurs
     */
    protected fun handleFailure(fail: Int): Void? {

        Log.d(TAG, "BrAPI callback failed. $fail")

        return null //default fail callback return type for brapi

    }

    protected fun switchProgress() = runOnUiThread {

        with(findViewById<ProgressBar>(R.id.progress)) {
            visibility = when(visibility) {
                View.GONE -> View.VISIBLE
                else -> View.GONE
            }
        }
    }

    //load and display data given a list of searchable names, an empty list searches for all data
    abstract fun loadBrAPIData(names: List<String>? = null)

    //function used to toggle existence of an item in a set
    protected fun <T> HashSet<T>.addOrRemove(item: T) {
        if (this.contains(item)) {
            this.remove(item)
        } else this.add(item)
    }
}