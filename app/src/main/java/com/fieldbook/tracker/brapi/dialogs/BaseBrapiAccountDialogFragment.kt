package com.fieldbook.tracker.brapi.dialogs

import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.core.view.WindowCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CameraActivity
import com.fieldbook.tracker.activities.brapi.BrapiAuthActivity
import com.fieldbook.tracker.brapi.BrapiAuthenticator
import com.fieldbook.tracker.utilities.BrapiAccountHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Shared base for BrAPI account dialog fragments.
 *
 * Owns the injected [BrapiAccountHelper], the [authResponse] passed from the system
 * AccountManager, and all logic that is identical between the manual-entry and
 * guided-stepper flows:
 *  - full-screen window / IME setup
 *  - default dropdown initialisation
 *  - URL validation and display-name fetch
 *  - the common "Authorize" flow (save config → launch BrapiAuthActivity)
 *  - cancellation and AUTH_REQUEST result handling
 *
 * Concrete subclasses must implement the abstract state properties so this base
 * class can read and write form values without knowing the composable layout.
 * Subclasses retain @AndroidEntryPoint; Hilt's generated code for the concrete
 * class injects fields declared here as well.
 */
abstract class BaseBrapiAccountDialogFragment : DialogFragment() {

    companion object {
        const val REQUEST_SCAN_BASE_URL = 201
        const val REQUEST_AUTH = 204
    }

    @Inject
    lateinit var accountHelper: BrapiAccountHelper

    protected var authResponse: AccountAuthenticatorResponse? = null

    // ── Abstract state ────────────────────────────────────────────────────────
    // Subclasses expose their MutableState fields so shared methods can access them.

    protected abstract val urlState: MutableState<String>
    protected abstract val displayNameState: MutableState<String>
    protected abstract val oidcUrlState: MutableState<String>
    protected abstract val oidcClientIdState: MutableState<String>
    protected abstract val oidcScopeState: MutableState<String>
    protected abstract val oidcFlowState: MutableState<String>
    protected abstract val brapiVersionState: MutableState<String>

    // ── Window / IME ──────────────────────────────────────────────────────────

    override fun onStart() {
        super.onStart()
        // MATCH_PARENT positions the window at (0,0), making the Compose coordinate space
        // match the screen coordinate space exactly — which fixes cursor handle alignment.
        // Let Compose handle IME insets via imePadding() so the card shifts above the keyboard.
        // SOFT_INPUT_ADJUST_RESIZE is deprecated on API 30+ and unreliable with edge-to-edge.
        dialog?.window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
            WindowCompat.setDecorFitsSystemWindows(this, false)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
    }

    // ── Defaults ──────────────────────────────────────────────────────────────

    /**
     * Initialises the OIDC flow and BrAPI version dropdowns to their default values.
     * Call from [onCreate] after resources are available.
     */
    protected fun initDefaultDropdownValues() {
        val oidcFlowOptions = resources.getStringArray(R.array.pref_brapi_oidc_flow)
        val implicitIndex = oidcFlowOptions.indexOfFirst {
            it.equals(getString(R.string.preferences_brapi_oidc_flow_oauth_implicit), ignoreCase = true)
        }
        oidcFlowState.value = if (implicitIndex >= 0) oidcFlowOptions[implicitIndex]
        else oidcFlowOptions.firstOrNull() ?: ""

        val versionOptions = resources.getStringArray(R.array.pref_brapi_version)
        val v2Index = versionOptions.indexOfFirst { it.equals("V2", ignoreCase = true) }
        if (v2Index >= 0) brapiVersionState.value = versionOptions[v2Index]
    }

    // ── Shared actions ────────────────────────────────────────────────────────

    protected fun onCancelled() {
        // Only finish the host activity when launched from the system AccountManager.
        // When shown from within BrapiPreferencesFragment, just dismiss.
        if (authResponse != null) {
            activity?.setResult(Activity.RESULT_CANCELED)
            activity?.finish()
        }
        dismiss()
    }

    protected fun startScan(requestCode: Int) {
        val intent = Intent(requireContext(), CameraActivity::class.java)
        intent.putExtra(CameraActivity.EXTRA_MODE, CameraActivity.MODE_BARCODE)
        startActivityForResult(intent, requestCode)
    }

    /**
     * Saves the form config to AccountManager and launches [BrapiAuthActivity].
     * On successful auth, [onActivityResult] finishes the host activity if needed.
     */
    protected fun onAuthorize() {
        val url = try { accountHelper.normalizeUrl(urlState.value.trim()) } catch (e: Exception) { "" }
        if (url.isEmpty() || !isValidUrl(url)) {
            Toast.makeText(requireContext(), R.string.brapi_invalid_url, Toast.LENGTH_LONG).show()
            return
        }

        val displayName = displayNameState.value.trim().takeIf { it.isNotEmpty() } ?: url
        val oidcFlow = oidcFlowState.value
        val oidcUrl = oidcUrlState.value.trim()
        val oidcClientId = oidcClientIdState.value.trim()
        val oidcScope = oidcScopeState.value.trim()
        val brapiVersion = brapiVersionState.value

        accountHelper.addAccountConfig(
            serverUrl = url,
            displayName = displayName,
            oidcUrl = oidcUrl,
            oidcFlow = oidcFlow,
            oidcClientId = oidcClientId,
            oidcScope = oidcScope,
            brapiVersion = brapiVersion,
        )

        accountHelper.setActiveAccount(url)

        val intent = Intent(requireContext(), BrapiAuthActivity::class.java).apply {
            putExtra(BrapiAuthActivity.EXTRA_SERVER_URL, url)
            putExtra(BrapiAuthActivity.EXTRA_OIDC_URL, oidcUrl)
            putExtra(BrapiAuthActivity.EXTRA_OIDC_FLOW, oidcFlow)
            putExtra(BrapiAuthActivity.EXTRA_OIDC_CLIENT_ID, oidcClientId)
            putExtra(BrapiAuthActivity.EXTRA_OIDC_SCOPE, oidcScope)
            putExtra(BrapiAuthActivity.EXTRA_BRAPI_VERSION, brapiVersion)
            if (authResponse != null) {
                putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, authResponse)
            }
        }
        startActivityForResult(intent, REQUEST_AUTH)
        // Do NOT dismiss here — the fragment must stay attached so onActivityResult is delivered.
        // The dialog will be invisible behind BrapiAuthActivity; dismiss happens in onActivityResult.
    }

    // ── Result handling ───────────────────────────────────────────────────────

    /**
     * Handles the AUTH result. Subclasses that override [onActivityResult] must call
     * `super.onActivityResult(...)` so this runs.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_AUTH) return

        if (resultCode != Activity.RESULT_OK) {
            dismiss()
            if (authResponse != null) activity?.finish()
            return
        }

        authResponse?.onResult(Bundle().apply {
            putString(AccountManager.KEY_ACCOUNT_NAME, urlState.value)
            putString(AccountManager.KEY_ACCOUNT_TYPE, BrapiAuthenticator.ACCOUNT_TYPE)
        })
        dismiss()
        activity?.setResult(Activity.RESULT_OK)
        activity?.finish()
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    protected fun isValidUrl(url: String): Boolean {
        if (url.contains(' ')) return false
        return try {
            val parsed = java.net.URL(url)
            val scheme = parsed.protocol
            if (scheme != "http" && scheme != "https") return false
            val host = parsed.host ?: return false
            if (host.isEmpty()) return false
            // Accept dotted hostnames (e.g. example.org, 192.168.1.1) or bracketed IPv6.
            // Reject bare single-word hosts like "https://foo" with no TLD/dot.
            host.contains('.') || host.startsWith('[') // IPv6 like [::1]
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Fetches the server's display name from the BrAPI v2 /serverinfo endpoint.
     * Falls back to the URL hostname on failure. Updates [displayNameState] on success.
     */
    protected fun fetchDisplayName(baseUrl: String) {
        lifecycleScope.launch {
            val serverName = withContext(Dispatchers.IO) {
                try {
                    val serverinfoUrl = baseUrl.trimEnd('/') + "/brapi/v2/serverinfo"
                    val conn = java.net.URL(serverinfoUrl).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    if (conn.responseCode == 200) {
                        val body = conn.inputStream.bufferedReader().readText()
                        org.json.JSONObject(body)
                            .optJSONObject("result")
                            ?.optString("serverName")
                            ?.takeIf { it.isNotEmpty() }
                    } else null
                } catch (e: Exception) { null }
            }

            val displayText = serverName
                ?: try { java.net.URL(baseUrl).host } catch (e: Exception) { "" }

            if (isAdded && displayText.isNotEmpty()) {
                displayNameState.value = displayText
            }
        }
    }
}
