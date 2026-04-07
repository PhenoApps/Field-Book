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
import com.fieldbook.tracker.ui.theme.AppTheme
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
        val normalized = try { accountHelper.normalizeUrl(urlState.value.trim()) } catch (e: Exception) { "" }
        if (normalized.isEmpty() || !isValidUrl(normalized)) {
            Toast.makeText(requireContext(), R.string.brapi_invalid_url, Toast.LENGTH_LONG).show()
            return
        }
        urlState.value = normalized
        fetchDisplayName(normalized)
        currentStepState.value = 1
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data) // handles REQUEST_AUTH
        if (resultCode != Activity.RESULT_OK) return

        if (requestCode == REQUEST_SCAN_BASE_URL) {
            urlState.value = data?.getStringExtra(CameraActivity.EXTRA_BARCODE) ?: return
        }
    }
}
