package com.fieldbook.tracker.brapi.dialogs

import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.fieldbook.tracker.R

/**
 * Dialog shown when the user initiates "Add Account" — either from within the BrAPI preferences
 * screen or from the system Accounts manager.
 *
 * Presents two options:
 *   1. Manual Entry — opens BrapiManualAccountDialogFragment with empty fields
 *   2. Scan Configuration Code — launches barcode scanner; on result opens the manual dialog
 *      pre-populated with the scanned BrAPIConfig
 */
class BrapiAddAccountDialogFragment : DialogFragment() {

    companion object {
        const val TAG = "BrapiAddAccountDialog"
        private const val ARG_AUTH_RESPONSE = "auth_response"

        fun newInstance(authenticatorResponse: AccountAuthenticatorResponse? = null): BrapiAddAccountDialogFragment {
            return BrapiAddAccountDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_AUTH_RESPONSE, authenticatorResponse)
                }
            }
        }
    }

    private var listener: Listener? = null

    interface Listener {
        fun onGuidedSetup(authResponse: AccountAuthenticatorResponse?)
        fun onScanConfig(authResponse: AccountAuthenticatorResponse?)
    }

    fun setListener(l: Listener) {
        listener = l
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val authResponse: AccountAuthenticatorResponse? =
            arguments?.getParcelable(ARG_AUTH_RESPONSE)

        val options = arrayOf(
            getString(R.string.brapi_add_account_guided_setup),
            getString(R.string.brapi_add_account_scan_config)
        )

        return AlertDialog.Builder(requireContext(), R.style.AppAlertDialog)
            .setTitle(R.string.brapi_add_account_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        listener?.onGuidedSetup(authResponse)
                            ?: openGuidedSetup(authResponse)
                    }
                    1 -> {
                        listener?.onScanConfig(authResponse)
                            ?: openScanConfig(authResponse)
                    }
                }
            }
            .setNegativeButton(R.string.dialog_cancel) { _, _ -> onCancelled() }
            .create()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        onCancelled()
    }

    private fun onCancelled() {
        // Only finish the host activity when launched from the system AccountManager.
        // When shown from within BrapiPreferencesFragment, just dismiss.
        val authResponse: AccountAuthenticatorResponse? = arguments?.getParcelable(ARG_AUTH_RESPONSE)
        if (authResponse != null) {
            activity?.setResult(android.app.Activity.RESULT_CANCELED)
            activity?.finish()
        }
        dismiss()
    }

    private fun openGuidedSetup(authResponse: AccountAuthenticatorResponse?) {
        val frag = BrapiStepperAccountDialogFragment.newInstance(authResponse = authResponse)
        frag.show(parentFragmentManager, BrapiStepperAccountDialogFragment.TAG)
        dismiss()
    }

    private fun openScanConfig(authResponse: AccountAuthenticatorResponse?) {
        val frag = BrapiManualAccountDialogFragment.newInstance(
            authResponse = authResponse,
            startScan = true
        )
        frag.show(parentFragmentManager, BrapiManualAccountDialogFragment.TAG)
        dismiss()
    }
}
