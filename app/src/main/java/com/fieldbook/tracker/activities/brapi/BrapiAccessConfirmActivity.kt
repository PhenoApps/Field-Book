package com.fieldbook.tracker.activities.brapi

import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fieldbook.tracker.R
import com.fieldbook.tracker.brapi.BrapiAuthenticator
import org.phenoapps.brapi.BrapiAccountConstants

/**
 * Shown when a PhenoApps app other than the account owner calls getAuthToken().
 * Presents a one-time grant dialog; the decision is persisted as AccountManager user data
 * so subsequent calls from the same package go straight through without prompting again.
 */
class BrapiAccessConfirmActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT_NAME = "brapi_confirm_account_name"
        const val EXTRA_CALLING_PACKAGE = "brapi_confirm_calling_package"
    }

    private var authenticatorResponse: AccountAuthenticatorResponse? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_brapi_access_confirm)

        authenticatorResponse = intent.getParcelableExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE)
        authenticatorResponse?.onRequestContinued()

        val accountName = intent.getStringExtra(EXTRA_ACCOUNT_NAME) ?: ""
        val callingPkg = intent.getStringExtra(EXTRA_CALLING_PACKAGE) ?: ""

        val appLabel = try {
            val info = packageManager.getApplicationInfo(callingPkg, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            callingPkg
        }

        val appIcon = try {
            packageManager.getApplicationIcon(callingPkg)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

        findViewById<ImageView>(R.id.brapi_confirm_icon).apply {
            if (appIcon != null) setImageDrawable(appIcon)
        }

        findViewById<TextView>(R.id.brapi_confirm_app_name).text = appLabel

        findViewById<TextView>(R.id.brapi_confirm_message).text =
            getString(R.string.brapi_access_confirm_message, appLabel, accountName)

        findViewById<Button>(R.id.brapi_confirm_allow).setOnClickListener {
            grantAccess(accountName, callingPkg)
        }

        findViewById<Button>(R.id.brapi_confirm_deny).setOnClickListener {
            authenticatorResponse?.onError(AccountManager.ERROR_CODE_CANCELED, "User denied access")
            authenticatorResponse = null
            finish()
        }
    }

    private fun grantAccess(accountName: String, callingPkg: String) {
        val am = AccountManager.get(this)
        val account = am.getAccountsByType(BrapiAccountConstants.ACCOUNT_TYPE)
            .firstOrNull { it.name == accountName } ?: run {
            authenticatorResponse?.onError(AccountManager.ERROR_CODE_BAD_ARGUMENTS, "Account not found")
            authenticatorResponse = null
            finish()
            return
        }

        val grantKey = BrapiAccountConstants.grantedPackageKey(callingPkg)
        am.setUserData(account, grantKey, "true")

        val token = am.peekAuthToken(account, BrapiAccountConstants.AUTH_TOKEN_TYPE)
        val result = Bundle().apply {
            putString(AccountManager.KEY_ACCOUNT_NAME, account.name)
            putString(AccountManager.KEY_ACCOUNT_TYPE, account.type)
            putString(AccountManager.KEY_AUTHTOKEN, token)
        }
        authenticatorResponse?.onResult(result)
        authenticatorResponse = null
        finish()
    }

    override fun onDestroy() {
        // If the activity is destroyed without an explicit response (e.g. back press), deny.
        authenticatorResponse?.onError(AccountManager.ERROR_CODE_CANCELED, "User dismissed")
        authenticatorResponse = null
        super.onDestroy()
    }
}
