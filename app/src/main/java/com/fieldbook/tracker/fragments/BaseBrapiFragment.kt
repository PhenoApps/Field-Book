package com.fieldbook.tracker.fragments

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.transition.Explode
import android.transition.TransitionInflater
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.*
import androidx.fragment.app.*
import com.fieldbook.tracker.R
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2
import com.fieldbook.tracker.brapi.service.BrapiPaginationManager
import com.fieldbook.tracker.views.PageControllerView
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.*
import kotlin.collections.HashSet

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

    @KtorExperimentalAPI
    protected val httpClient = HttpClient(CIO) {

        install(JsonFeature) {
            serializer = GsonSerializer {
                serializeNulls()
                disableHtmlEscaping()
            }
        }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //check if device is connected to a network
        if (isConnected(this)) {

            //checks that the preference brapi url matches a web url
            if (BrAPIService.hasValidBaseUrl(this)) {

//                // inside your activity (if you did not enable transitions in your theme)
//                with(window) {
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//
//                        this?.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
//
//                        val inflater = TransitionInflater.from(context)
//                        exitTransition = inflater.inflateTransition(R.transition.slide_in)
//                        enterTransition = inflater.inflateTransition(R.transition.slide_out)
//                    }
//                }

                overridePendingTransition(R.anim.slide_in_bottom, R.anim.slide_out_top)

                setContentView(R.layout.activity_brapi_import)

                findViewById<TextView>(R.id.serverTextView)?.text = BrAPIService.getBrapiUrl(this)

                findViewById<ListView>(R.id.listView)?.choiceMode = ListView.CHOICE_MODE_MULTIPLE

                setupPageController()

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

    //load and display programs
    abstract fun loadBrAPIData()

    //function used to toggle existence of an item in a set
    protected fun <T> HashSet<T>.addOrRemove(item: T) {
        if (this.contains(item)) {
            this.remove(item)
        } else this.add(item)
    }
}