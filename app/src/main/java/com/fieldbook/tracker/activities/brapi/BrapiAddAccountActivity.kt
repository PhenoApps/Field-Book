package com.fieldbook.tracker.activities.brapi

import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.brapi.dialogs.BrapiStepperAccountDialogFragment
import com.fieldbook.tracker.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import org.phenoapps.brapi.BrapiAccountConstants

/**
 * Thin activity that hosts the BrAPI add-account dialog.
 * Launched by the system AccountManager (via BrapiAuthenticator.addAccount),
 * or by the BrAPI preferences screen when the user taps "Add Account".
 */
@AndroidEntryPoint
class BrapiAddAccountActivity : ThemedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.getBooleanExtra(BrapiAccountConstants.EXTRA_SHOW_IN_APP_ADDER_TOAST, false)) {
            val message = getString(org.phenoapps.brapi.R.string.pheno_brapi_add_account_in_app_only)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            intent.getParcelableExtra<AccountAuthenticatorResponse>(
                AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE,
            )?.onError(AccountManager.ERROR_CODE_CANCELED, message)
            finish()
            return
        }

        setContent { AppTheme { } }

        if (savedInstanceState == null) {
            val fragment = BrapiStepperAccountDialogFragment.newInstance(
                authResponse = intent.getParcelableExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE)
            )
            fragment.show(supportFragmentManager, BrapiStepperAccountDialogFragment.TAG)
        }
    }
}
