package com.fieldbook.shared.brapi

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.fieldbook.shared.AndroidAppContextHolder
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val EXTRA_AUTH_URL = "com.fieldbook.shared.brapi.AUTH_URL"

actual suspend fun openBrapiAuthorizationUrl(authUrl: String, redirectUri: String): String? {
    return BrapiOAuthRedirectBridge.open(authUrl)
}

private object BrapiOAuthRedirectBridge {
    private var continuation: (String?) -> Unit = {}

    suspend fun open(authUrl: String): String? = suspendCancellableCoroutine { cont ->
        continuation = { value ->
            continuation = {}
            if (cont.isActive) {
                cont.resume(value)
            }
        }

        cont.invokeOnCancellation {
            continuation = {}
        }

        val intent = Intent(AndroidAppContextHolder.context, BrapiOAuthRedirectActivity::class.java)
            .putExtra(EXTRA_AUTH_URL, authUrl)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        AndroidAppContextHolder.context.startActivity(intent)
    }

    fun complete(callbackUrl: String?) {
        continuation(callbackUrl)
    }
}

class BrapiOAuthRedirectActivity : Activity() {
    private var browserLaunched = false
    private var pausedAfterLaunch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (browserLaunched && pausedAfterLaunch && intent?.data == null) {
            BrapiOAuthRedirectBridge.complete(null)
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        if (browserLaunched) {
            pausedAfterLaunch = true
        }
    }

    private fun handleIntent(intent: Intent?) {
        val callbackUri = intent?.data
        if (callbackUri != null) {
            BrapiOAuthRedirectBridge.complete(callbackUri.toString())
            finish()
            return
        }

        if (!browserLaunched) {
            val authUrl = intent?.getStringExtra(EXTRA_AUTH_URL)
            if (authUrl.isNullOrBlank()) {
                BrapiOAuthRedirectBridge.complete(null)
                finish()
                return
            }
            browserLaunched = true
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
            } catch (_: ActivityNotFoundException) {
                BrapiOAuthRedirectBridge.complete(null)
                finish()
            }
        }
    }
}
