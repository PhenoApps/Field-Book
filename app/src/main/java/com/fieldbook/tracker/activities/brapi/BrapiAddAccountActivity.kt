package com.fieldbook.tracker.activities.brapi

import android.accounts.AccountManager
import android.os.Bundle
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.brapi.dialogs.BrapiAddAccountDialogFragment
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
        setContentView(R.layout.activity_brapi_add_account)

        if (savedInstanceState == null) {
            val fragment = BrapiAddAccountDialogFragment.newInstance(
                authenticatorResponse = intent.getParcelableExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE)
            )
            fragment.show(supportFragmentManager, BrapiAddAccountDialogFragment.TAG)
        }
    }
}
