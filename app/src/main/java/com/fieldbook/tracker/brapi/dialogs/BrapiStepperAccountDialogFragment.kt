package com.fieldbook.tracker.brapi.dialogs

import android.accounts.AccountAuthenticatorResponse
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CameraActivity
import com.fieldbook.tracker.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import org.phenoapps.brapi.ui.BrapiStepperAccountForm
import org.phenoapps.brapi.ui.parseBrapiConfig

/**
 * Guided multi-step dialog for adding a new BrAPI server configuration.
 *
 * Step 0: Choose input method (guided setup or scan QR config).
 * Step 1: Enter server base URL (with optional barcode scan).
 * Step 2: Review/edit display name, BrAPI version, and OIDC settings.
 *
 * All form state and step progression live in [BrapiAccountViewModel], which survives
 * configuration changes. Navigation events (authorize, cancel) are handled by the base
 * class via [observeEvents].
 */
@AndroidEntryPoint
class BrapiStepperAccountDialogFragment : BaseBrapiAccountDialogFragment() {

    companion object {
        const val TAG = "BrapiStepperAccountDialog"
        private const val ARG_AUTH_RESPONSE = "auth_response"

        fun newInstance(
            authResponse: AccountAuthenticatorResponse? = null,
        ): BrapiStepperAccountDialogFragment {
            return BrapiStepperAccountDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_AUTH_RESPONSE, authResponse)
                }
            }
        }
    }

    // Scan for the base URL only (plain URL scan on step 1).
    private val scanBaseUrlLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val scanned = result.data?.getStringExtra(CameraActivity.EXTRA_BARCODE)
                ?: return@registerForActivityResult
            viewModel.updateUrl(scanned)
        }

    // Scan for a full BrAPI config QR code (from step 0 "Scan Config" option).
    private val scanConfigLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val scanned = result.data?.getStringExtra(CameraActivity.EXTRA_BARCODE)
                ?: return@registerForActivityResult
            val config = parseBrapiConfig(scanned)
            if (config != null) {
                viewModel.applyConfig(config)
                // Full config scanned — skip URL step and go straight to config details.
                viewModel.setStep(2)
            } else {
                // Plain URL scanned — pre-fill and go to URL entry step.
                viewModel.updateUrl(scanned)
                viewModel.setStep(1)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, org.phenoapps.brapi.R.style.PhenoBrapiComposeDialog)
        authResponse = arguments?.getParcelable(ARG_AUTH_RESPONSE)

        // On first creation: reset the ViewModel to a blank state (clears any leftover data from
        // a previous dialog session in the same activity).
        // On rotation: savedInstanceState is non-null, so we skip this and keep existing state.
        if (savedInstanceState == null) {
            viewModel.reset()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
            )
            setContent {
                AppTheme {
                    val uiState by viewModel.uiState.collectAsState()
                    BrapiStepperAccountForm(
                        uiState = uiState,
                        onUrlChange = viewModel::updateUrl,
                        onDisplayNameChange = viewModel::updateDisplayName,
                        onOidcUrlChange = viewModel::updateOidcUrl,
                        onOidcClientIdChange = viewModel::updateOidcClientId,
                        onOidcScopeChange = viewModel::updateOidcScope,
                        onOidcFlowChange = viewModel::updateOidcFlow,
                        onBrapiVersionChange = viewModel::updateBrapiVersion,
                        onScanBaseUrl = { startScan(scanBaseUrlLauncher) },
                        onScanConfig = { startScan(scanConfigLauncher) },
                        onNext = viewModel::onNext,
                        onBack = viewModel::onBack,
                        onCancel = { onCancelled() },
                        onAuthorize = viewModel::onAuthorize,
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeEvents()
    }
}
