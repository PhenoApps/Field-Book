package com.fieldbook.tracker.brapi.dialogs

import android.accounts.AccountAuthenticatorResponse
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.fieldbook.tracker.activities.CameraActivity
import com.fieldbook.tracker.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import org.phenoapps.brapi.ui.BrapiAccountConfig
import org.phenoapps.brapi.ui.BrapiManualAccountForm
import org.phenoapps.brapi.ui.parseBrapiConfig

/**
 * Dialog for manually entering a new BrAPI server configuration.
 * Can be pre-populated from a scanned BrAPI config QR code or an existing account in edit mode.
 *
 * All form state lives in [BrapiAccountViewModel], which is activity-scoped and survives
 * configuration changes. QR scan results and edit saves are handled here; navigation events
 * (authorize, cancel) are delegated to the base class via [observeEvents].
 */
@AndroidEntryPoint
class BrapiManualAccountDialogFragment : BaseBrapiAccountDialogFragment() {

    companion object {
        const val TAG = "BrapiManualAccountDialog"
        const val REQUEST_KEY_EDIT_SAVED = "brapi_edit_account_saved"

        private const val ARG_AUTH_RESPONSE = "auth_response"
        private const val ARG_START_SCAN = "start_scan"
        private const val ARG_EDIT_MODE = "edit_mode"
        private const val ARG_PREFILL_URL = "prefill_url"
        private const val ARG_PREFILL_NAME = "prefill_name"
        private const val ARG_PREFILL_VERSION = "prefill_version"
        private const val ARG_PREFILL_FLOW = "prefill_flow"
        private const val ARG_PREFILL_OIDC_URL = "prefill_oidc_url"
        private const val ARG_PREFILL_CLIENT_ID = "prefill_client_id"
        private const val ARG_PREFILL_SCOPE = "prefill_scope"

        fun newInstance(
            authResponse: AccountAuthenticatorResponse? = null,
            startScan: Boolean = false,
            prefillConfig: BrapiAccountConfig? = null,
            editMode: Boolean = false,
        ): BrapiManualAccountDialogFragment {
            return BrapiManualAccountDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_AUTH_RESPONSE, authResponse)
                    putBoolean(ARG_START_SCAN, startScan)
                    putBoolean(ARG_EDIT_MODE, editMode)
                    if (prefillConfig != null) {
                        putString(ARG_PREFILL_URL, prefillConfig.url)
                        putString(ARG_PREFILL_NAME, prefillConfig.name)
                        putString(ARG_PREFILL_VERSION, prefillConfig.version)
                        putString(ARG_PREFILL_FLOW, prefillConfig.authFlow)
                        putString(ARG_PREFILL_OIDC_URL, prefillConfig.oidcUrl)
                        putString(ARG_PREFILL_CLIENT_ID, prefillConfig.clientId)
                        putString(ARG_PREFILL_SCOPE, prefillConfig.scope)
                    }
                }
            }
        }
    }

    private var isEditMode = false

    private val scanLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val scanned = result.data?.getStringExtra(CameraActivity.EXTRA_BARCODE)
                ?: return@registerForActivityResult
            val config = parseBrapiConfig(scanned)
            if (config != null) {
                viewModel.applyConfig(config)
            } else {
                viewModel.updateUrl(scanned)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, org.phenoapps.brapi.R.style.PhenoBrapiComposeDialog)

        authResponse = arguments?.getParcelable(ARG_AUTH_RESPONSE)
        isEditMode = arguments?.getBoolean(ARG_EDIT_MODE, false) == true

        // On first creation: reset the ViewModel to a blank state (clears any leftover data from
        // a previous dialog session in the same activity), then apply any pre-fill config.
        // On rotation: savedInstanceState is non-null, so we skip this and keep existing state.
        if (savedInstanceState == null) {
            viewModel.reset()
            if (isEditMode || arguments?.containsKey(ARG_PREFILL_URL) == true) {
                val config = BrapiAccountConfig(
                    url = arguments?.getString(ARG_PREFILL_URL),
                    name = arguments?.getString(ARG_PREFILL_NAME),
                    version = arguments?.getString(ARG_PREFILL_VERSION),
                    authFlow = arguments?.getString(ARG_PREFILL_FLOW),
                    oidcUrl = arguments?.getString(ARG_PREFILL_OIDC_URL),
                    clientId = arguments?.getString(ARG_PREFILL_CLIENT_ID),
                    scope = arguments?.getString(ARG_PREFILL_SCOPE),
                )
                viewModel.applyConfig(config)
                if (isEditMode) {
                    config.url?.let { viewModel.setEditOriginalUrl(it) }
                }
            }
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
                    BrapiManualAccountForm(
                        title = getString(
                            if (isEditMode) org.phenoapps.brapi.R.string.pheno_brapi_edit_account_title
                            else org.phenoapps.brapi.R.string.pheno_brapi_add_account_title
                        ),
                        uiState = uiState,
                        onUrlChange = viewModel::updateUrl,
                        onDisplayNameChange = viewModel::updateDisplayName,
                        onOidcUrlChange = viewModel::updateOidcUrl,
                        onOidcClientIdChange = viewModel::updateOidcClientId,
                        onOidcScopeChange = viewModel::updateOidcScope,
                        onOidcFlowChange = viewModel::updateOidcFlow,
                        onBrapiVersionChange = viewModel::updateBrapiVersion,
                        isEditMode = isEditMode,
                        onCancel = { onCancelled() },
                        onConfirm = {
                            if (isEditMode) viewModel.onSaveEdit() else viewModel.onAuthorize()
                        },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeEvents()
    }

    override fun onResume() {
        super.onResume()
        // Start barcode scan immediately if requested (Scan Configuration Code flow).
        if (arguments?.getBoolean(ARG_START_SCAN, false) == true) {
            arguments?.putBoolean(ARG_START_SCAN, false)
            startScan(scanLauncher)
        }
    }

    override fun handleEvent(event: BrapiAccountEvent) {
        if (event is BrapiAccountEvent.EditSaved) {
            parentFragmentManager.setFragmentResult(REQUEST_KEY_EDIT_SAVED, Bundle.EMPTY)
            dismiss()
            return
        }
        super.handleEvent(event)
    }
}
