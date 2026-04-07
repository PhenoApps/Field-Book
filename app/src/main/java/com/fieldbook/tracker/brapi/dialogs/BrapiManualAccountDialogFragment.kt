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
import com.fieldbook.tracker.brapi.dialogs.composables.BrapiManualAccountForm
import com.fieldbook.tracker.objects.BrAPIConfig
import com.fieldbook.tracker.utilities.JsonUtil
import com.google.gson.Gson
import com.fieldbook.tracker.activities.CameraActivity
import dagger.hilt.android.AndroidEntryPoint
import com.fieldbook.tracker.ui.theme.AppTheme

/**
 * Dialog for manually entering a new BrAPI server configuration.
 * Can be pre-populated from a scanned BrAPIConfig QR code.
 *
 * On "Authorize", stores the account config in AccountManager and launches BrapiAuthActivity.
 */
@AndroidEntryPoint
class BrapiManualAccountDialogFragment : BaseBrapiAccountDialogFragment() {

    companion object {
        const val TAG = "BrapiManualAccountDialog"
        const val REQUEST_KEY_EDIT_SAVED = "brapi_edit_account_saved"

        private const val ARG_AUTH_RESPONSE = "auth_response"
        private const val ARG_START_SCAN = "start_scan"
        private const val ARG_PREFILL_CONFIG = "prefill_config"
        private const val ARG_EDIT_MODE = "edit_mode"

        private const val REQUEST_SCAN_CONFIG = 203

        fun newInstance(
            authResponse: AccountAuthenticatorResponse? = null,
            startScan: Boolean = false,
            prefillConfig: BrAPIConfig? = null,
            editMode: Boolean = false
        ): BrapiManualAccountDialogFragment {
            return BrapiManualAccountDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_AUTH_RESPONSE, authResponse)
                    putBoolean(ARG_START_SCAN, startScan)
                    putBoolean(ARG_EDIT_MODE, editMode)
                    if (prefillConfig != null) {
                        putString(ARG_PREFILL_CONFIG, Gson().toJson(prefillConfig))
                    }
                }
            }
        }
    }

    private var isEditMode = false

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
        // Use a clean dialog style that does NOT have windowFullscreen=true or
        // transparent background — both of which break Compose keyboard input
        // and popup positioning.
        setStyle(STYLE_NO_FRAME, R.style.BrapiComposeDialog)

        authResponse = arguments?.getParcelable(ARG_AUTH_RESPONSE)
        isEditMode = arguments?.getBoolean(ARG_EDIT_MODE, false) == true

        initDefaultDropdownValues()

        // Pre-fill if a BrAPIConfig was passed
        val prefillJson = arguments?.getString(ARG_PREFILL_CONFIG)
        if (prefillJson != null) {
            try {
                val config = Gson().fromJson(prefillJson, BrAPIConfig::class.java)
                applyConfig(config)
            } catch (e: Exception) { /* ignore */ }
        }
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
                    BrapiManualAccountForm(
                        title = getString(
                            if (isEditMode) R.string.brapi_edit_account_title
                            else R.string.brapi_add_account_title
                        ),
                        urlState = urlState,
                        displayNameState = displayNameState,
                        oidcUrlState = oidcUrlState,
                        oidcClientIdState = oidcClientIdState,
                        oidcScopeState = oidcScopeState,
                        oidcFlowState = oidcFlowState,
                        brapiVersionState = brapiVersionState,
                        oidcUrlExplicitlySetState = oidcUrlExplicitlySetState,
                        isEditMode = isEditMode,
                        onFetchDisplayName = { url ->
                            val normalized = try { accountHelper.normalizeUrl(url) } catch (e: Exception) { "" }
                            if (normalized.isNotEmpty() && normalized != "https://") fetchDisplayName(normalized)
                        },
                        onCancel = { onCancelled() },
                        onConfirm = { if (isEditMode) onSaveEdit() else onAuthorize() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Start barcode scan immediately if requested (Scan Configuration Code flow)
        if (arguments?.getBoolean(ARG_START_SCAN, false) == true) {
            arguments?.putBoolean(ARG_START_SCAN, false)
            startScan(REQUEST_SCAN_CONFIG)
        }
    }

    /**
     * Edit-mode save: persists config changes to AccountManager without launching re-auth.
     * Notifies the parent fragment via FragmentResult so it can refresh the server cards.
     */
    private fun onSaveEdit() {
        val url = try { accountHelper.normalizeUrl(urlState.value.trim()) } catch (e: Exception) { "" }
        if (url.isEmpty() || !isValidUrl(url)) {
            Toast.makeText(requireContext(), R.string.brapi_invalid_url, Toast.LENGTH_LONG).show()
            return
        }

        val displayName = displayNameState.value.trim().takeIf { it.isNotEmpty() } ?: url

        accountHelper.addAccountConfig(
            serverUrl = url,
            displayName = displayName,
            oidcUrl = oidcUrlState.value.trim(),
            oidcFlow = oidcFlowState.value,
            oidcClientId = oidcClientIdState.value.trim(),
            oidcScope = oidcScopeState.value.trim(),
            brapiVersion = brapiVersionState.value,
        )

        parentFragmentManager.setFragmentResult(REQUEST_KEY_EDIT_SAVED, Bundle.EMPTY)
        dismiss()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data) // handles REQUEST_AUTH
        if (resultCode != Activity.RESULT_OK) return

        if (requestCode == REQUEST_SCAN_CONFIG) {
            val scanned = data?.getStringExtra(CameraActivity.EXTRA_BARCODE) ?: return
            if (JsonUtil.isJsonValid(scanned)) {
                try {
                    val config = Gson().fromJson(scanned, BrAPIConfig::class.java)
                    applyConfig(config)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), R.string.preferences_brapi_server_scan_error, Toast.LENGTH_SHORT).show()
                }
            } else {
                urlState.value = scanned
            }
        }
    }

    private fun applyConfig(config: BrAPIConfig) {
        // If config provides an explicit OIDC URL, lock it in before setting the base URL
        // (so the auto-derive LaunchedEffect doesn't overwrite it).
        if (!config.oidcUrl.isNullOrEmpty()) {
            oidcUrlExplicitlySetState.value = true
        }
        config.url?.let { urlState.value = it }
        config.name?.let { displayNameState.value = it }
        config.oidcUrl?.let { oidcUrlState.value = it }
        config.clientId?.let { oidcClientIdState.value = it }
        config.scope?.let { oidcScopeState.value = it }

        // Set OIDC flow
        val oidcFlowOptions = resources.getStringArray(R.array.pref_brapi_oidc_flow)
        val flowMatch = oidcFlowOptions.indexOfFirst {
            it.equals(config.authFlow, ignoreCase = true)
        }
        if (flowMatch >= 0) oidcFlowState.value = oidcFlowOptions[flowMatch]

        // Set version
        val versionOptions = resources.getStringArray(R.array.pref_brapi_version)
        val versionStr = when {
            "v1".equals(config.version, ignoreCase = true) -> "V1"
            else -> "V2"
        }
        val versionMatch = versionOptions.indexOfFirst { it.equals(versionStr, ignoreCase = true) }
        if (versionMatch >= 0) brapiVersionState.value = versionOptions[versionMatch]
    }
}
