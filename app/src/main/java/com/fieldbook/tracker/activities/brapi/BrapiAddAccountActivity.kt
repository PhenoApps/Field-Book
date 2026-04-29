package com.fieldbook.tracker.activities.brapi

import android.accounts.AccountManager
import android.os.Bundle
import androidx.activity.compose.setContent
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.brapi.dialogs.BrapiStepperAccountDialogFragment
import com.fieldbook.tracker.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Thin activity that hosts the BrAPI add-account dialog.
 * Launched by the system AccountManager (via BrapiAuthenticator.addAccount),
 * or by the BrAPI preferences screen when the user taps "Add Account".
 */
@AndroidEntryPoint
class BrapiAddAccountActivity : ThemedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { } }

        if (savedInstanceState == null) {
            val fragment = BrapiStepperAccountDialogFragment.newInstance(
                authResponse = intent.getParcelableExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE)
            )
            fragment.show(supportFragmentManager, BrapiStepperAccountDialogFragment.TAG)
        }
    }
}
