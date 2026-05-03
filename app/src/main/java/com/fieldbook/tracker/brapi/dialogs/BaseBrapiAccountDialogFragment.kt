package com.fieldbook.tracker.brapi.dialogs

import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CameraActivity
import com.fieldbook.tracker.activities.brapi.BrapiAuthActivity
import com.fieldbook.tracker.brapi.BrapiAuthenticator
import kotlinx.coroutines.launch

/**
 * Shared base for BrAPI account dialog fragments.
 *
 * Owns the shared [BrapiAccountViewModel] (activity-scoped so state survives rotation),
 * the [authResponse] from the system AccountManager, the auth [ActivityResultLauncher],
 * and shared window/IME setup.
 *
 * Subclasses register their own scan launchers and call [startScan] with them.
 * Event observation is started by calling [observeEvents] from [onViewCreated]; subclasses
 * that need [BrapiAccountEvent.EditSaved] override [handleEvent] and call super.
 */
abstract class BaseBrapiAccountDialogFragment : DialogFragment() {

    protected val viewModel: BrapiAccountViewModel by activityViewModels()

    protected var authResponse: AccountAuthenticatorResponse? = null

    // Replaces startActivityForResult(BrapiAuthActivity, REQUEST_AUTH).
    // Must be registered as a property (before onStart) per the Activity Result API contract.
    private val authLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                if (viewModel.accountWasNew) {
                    val state = viewModel.uiState.value
                    val serverName = state.displayName.trim().ifEmpty { state.url }
                    AlertDialog.Builder(requireContext(), R.style.AppAlertDialog)
                        .setTitle(R.string.brapi_auth_failed_keep_title)
                        .setMessage(getString(R.string.brapi_auth_failed_keep_message, serverName))
                        .setPositiveButton(R.string.brapi_auth_failed_keep) { _: DialogInterface, _: Int ->
                            authResponse?.onError(AccountManager.ERROR_CODE_CANCELED, "cancelled")
                            dismiss()
                            if (authResponse != null) activity?.finish()
                        }
                        .setNegativeButton(R.string.brapi_auth_failed_remove) { _: DialogInterface, _: Int ->
                            viewModel.removeNewAccount()
                            authResponse?.onError(AccountManager.ERROR_CODE_CANCELED, "cancelled")
                            dismiss()
                            if (authResponse != null) activity?.finish()
                        }
                        .setCancelable(false)
                        .show()
                } else {
                    authResponse?.onError(AccountManager.ERROR_CODE_CANCELED, "cancelled")
                    dismiss()
                    if (authResponse != null) activity?.finish()
                }
                return@registerForActivityResult
            }
            authResponse?.onResult(Bundle().apply {
                putString(AccountManager.KEY_ACCOUNT_NAME, viewModel.uiState.value.url)
                putString(AccountManager.KEY_ACCOUNT_TYPE, BrapiAuthenticator.ACCOUNT_TYPE)
            })
            dismiss()
            if (authResponse != null) {
                activity?.setResult(Activity.RESULT_OK)
                activity?.finish()
            }
        }

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

    // ── Shared actions ────────────────────────────────────────────────────────

    protected fun onCancelled() = viewModel.onCancelled()

    /** Launches [CameraActivity] in barcode mode using the given [launcher]. */
    protected fun startScan(launcher: ActivityResultLauncher<Intent>) {
        launcher.launch(
            Intent(requireContext(), CameraActivity::class.java).apply {
                putExtra(CameraActivity.EXTRA_MODE, CameraActivity.MODE_BARCODE)
            }
        )
    }

    // ── Event observation ─────────────────────────────────────────────────────

    /**
     * Starts collecting [BrapiAccountEvent]s from the ViewModel. Call from [onViewCreated].
     * Events are only consumed while the view is at least STARTED, preventing delivery
     * to a partially-destroyed view.
     */
    protected fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event -> handleEvent(event) }
            }
        }
    }

    /**
     * Handles a [BrapiAccountEvent]. Subclasses override this to intercept additional
     * event types (e.g. [BrapiAccountEvent.EditSaved]) before calling super.
     */
    protected open fun handleEvent(event: BrapiAccountEvent) {
        when (event) {
            is BrapiAccountEvent.LaunchAuth -> {
                authLauncher.launch(
                    Intent(requireContext(), BrapiAuthActivity::class.java).apply {
                        putExtra(BrapiAuthActivity.EXTRA_SERVER_URL, event.url)
                        putExtra(BrapiAuthActivity.EXTRA_OIDC_URL, event.oidcUrl)
                        putExtra(BrapiAuthActivity.EXTRA_OIDC_FLOW, event.oidcFlow)
                        putExtra(BrapiAuthActivity.EXTRA_OIDC_CLIENT_ID, event.oidcClientId)
                        putExtra(BrapiAuthActivity.EXTRA_OIDC_SCOPE, event.oidcScope)
                        putExtra(BrapiAuthActivity.EXTRA_BRAPI_VERSION, event.brapiVersion)
                        if (authResponse != null) {
                            putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, authResponse)
                        }
                    }
                )
                // Do NOT dismiss here — the fragment must stay attached to receive the
                // authLauncher result. The dialog is invisible behind BrapiAuthActivity;
                // dismissal happens in the authLauncher callback above.
            }
            is BrapiAccountEvent.ShowError -> {
                Toast.makeText(requireContext(), event.messageRes, Toast.LENGTH_LONG).show()
            }
            is BrapiAccountEvent.Dismissed -> {
                if (authResponse != null) {
                    authResponse?.onError(AccountManager.ERROR_CODE_CANCELED, "cancelled")
                    activity?.setResult(Activity.RESULT_CANCELED)
                    activity?.finish()
                }
                dismiss()
            }
            is BrapiAccountEvent.EditSaved -> {
                // Handled by BrapiManualAccountDialogFragment.handleEvent override.
            }
        }
    }
}
