package com.fieldbook.tracker.brapi.dialogs

import android.accounts.AccountAuthenticatorResponse
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CameraActivity
import com.fieldbook.tracker.brapi.dialogs.composables.BrapiStepperAccountForm
import com.fieldbook.tracker.objects.BrAPIConfig
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.utilities.JsonUtil
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint

/**
 * Two-step guided dialog for adding a new BrAPI server configuration.
 *
 * Step 1: User enters the server base URL.
 * Step 2: Display name is auto-populated from the server; user can customise OIDC and version settings.
 *
 * On "Authorize", stores the account config in AccountManager and launches BrapiAuthActivity.
 */
@AndroidEntryPoint
class BrapiStepperAccountDialogFragment : BaseBrapiAccountDialogFragment() {

    companion object {
        const val TAG = "BrapiStepperAccountDialog"
        private const val ARG_AUTH_RESPONSE = "auth_response"
        private const val REQUEST_SCAN_CONFIG = 205

        fun newInstance(authResponse: AccountAuthenticatorResponse? = null): BrapiStepperAccountDialogFragment {
            return BrapiStepperAccountDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_AUTH_RESPONSE, authResponse)
                }
            }
        }
    }

    // ── Step state ────────────────────────────────────────────────────────────

    private val currentStepState = mutableStateOf(0)

    // ── Form state ────────────────────────────────────────────────────────────

    override val urlState = mutableStateOf("")
    override val displayNameState = mutableStateOf("")
    override val oidcUrlState = mutableStateOf("")
    override val oidcClientIdState = mutableStateOf("")
    override val oidcScopeState = mutableStateOf("")
    override val oidcFlowState = mutableStateOf("")
    override val brapiVersionState = mutableStateOf("V2")
    private val oidcUrlExplicitlySetState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.BrapiComposeDialog)

        authResponse = arguments?.getParcelable(ARG_AUTH_RESPONSE)

        initDefaultDropdownValues()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
            )
            setContent {
                AppTheme {
                    BrapiStepperAccountForm(
                        currentStepState = currentStepState,
                        urlState = urlState,
                        displayNameState = displayNameState,
                        oidcUrlState = oidcUrlState,
                        oidcClientIdState = oidcClientIdState,
                        oidcScopeState = oidcScopeState,
                        oidcFlowState = oidcFlowState,
                        brapiVersionState = brapiVersionState,
                        oidcUrlExplicitlySetState = oidcUrlExplicitlySetState,
                        onScanBaseUrl = { startScan(REQUEST_SCAN_BASE_URL) },
                        onScanConfig = { startScan(REQUEST_SCAN_CONFIG) },
                        onNext = { onNext() },
                        onBack = { currentStepState.value-- },
                        onCancel = { onCancelled() },
                        onAuthorize = { onAuthorize() },
                    )
                }
            }
        }
    }

    private fun onNext() {
        when (currentStepState.value) {
            0 -> currentStepState.value = 1
            1 -> {
                val normalized = try { accountHelper.normalizeUrl(urlState.value.trim()) } catch (e: Exception) { "" }
                if (normalized.isEmpty() || !isValidUrl(normalized)) {
                    Toast.makeText(requireContext(), R.string.brapi_invalid_url, Toast.LENGTH_LONG).show()
                    return
                }
                urlState.value = normalized
                fetchDisplayName(normalized)
                currentStepState.value = 2
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data) // handles REQUEST_AUTH
        if (resultCode != Activity.RESULT_OK) return

        when (requestCode) {
            REQUEST_SCAN_BASE_URL -> {
                urlState.value = data?.getStringExtra(CameraActivity.EXTRA_BARCODE) ?: return
            }
            REQUEST_SCAN_CONFIG -> {
                val scanned = data?.getStringExtra(CameraActivity.EXTRA_BARCODE) ?: return
                if (JsonUtil.isJsonValid(scanned)) {
                    try {
                        val config = Gson().fromJson(scanned, BrAPIConfig::class.java)
                        applyConfig(config)
                        // Full config scanned — skip URL step and go straight to config details
                        currentStepState.value = 2
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), R.string.preferences_brapi_server_scan_error, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Plain URL scanned — pre-fill URL and go to URL entry step
                    urlState.value = scanned
                    currentStepState.value = 1
                }
            }
        }
    }

    private fun applyConfig(config: BrAPIConfig) {
        if (!config.oidcUrl.isNullOrEmpty()) {
            oidcUrlExplicitlySetState.value = true
        }
        config.url?.let { urlState.value = it }
        config.name?.let { displayNameState.value = it }
        config.oidcUrl?.let { oidcUrlState.value = it }
        config.clientId?.let { oidcClientIdState.value = it }
        config.scope?.let { oidcScopeState.value = it }

        val oidcFlowOptions = resources.getStringArray(R.array.pref_brapi_oidc_flow)
        val flowMatch = oidcFlowOptions.indexOfFirst { it.equals(config.authFlow, ignoreCase = true) }
        if (flowMatch >= 0) oidcFlowState.value = oidcFlowOptions[flowMatch]

        val versionOptions = resources.getStringArray(R.array.pref_brapi_version)
        val versionStr = when {
            "v1".equals(config.version, ignoreCase = true) -> "V1"
            else -> "V2"
        }
        val versionMatch = versionOptions.indexOfFirst { it.equals(versionStr, ignoreCase = true) }
        if (versionMatch >= 0) brapiVersionState.value = versionOptions[versionMatch]
    }
}
