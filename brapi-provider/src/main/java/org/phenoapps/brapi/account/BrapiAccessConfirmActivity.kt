package org.phenoapps.brapi.account

import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.phenoapps.brapi.BrapiAccountConstants
import org.phenoapps.brapi.R

class BrapiAccessConfirmActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT_NAME = "brapi_confirm_account_name"
        const val EXTRA_CALLING_PACKAGE = "brapi_confirm_calling_package"

        fun intent(
            context: Context,
            response: AccountAuthenticatorResponse,
            accountName: String,
            callingPackage: String,
        ): Intent =
            Intent(context, BrapiAccessConfirmActivity::class.java).apply {
                putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
                putExtra(EXTRA_ACCOUNT_NAME, accountName)
                putExtra(EXTRA_CALLING_PACKAGE, callingPackage)
            }
    }

    private var authenticatorResponse: AccountAuthenticatorResponse? = null
    private lateinit var accessPolicy: BrapiAccountAccessPolicy

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.pheno_brapi_activity_access_confirm)

        accessPolicy = BrapiAccountAccessPolicy(this)
        authenticatorResponse = intent.getParcelableExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE)
        authenticatorResponse?.onRequestContinued()

        val accountName = intent.getStringExtra(EXTRA_ACCOUNT_NAME) ?: ""
        val callingPackage = intent.getStringExtra(EXTRA_CALLING_PACKAGE) ?: ""
        val appLabel = appLabel(callingPackage)
        val appIcon = runCatching { packageManager.getApplicationIcon(callingPackage) }.getOrNull()

        findViewById<ImageView>(R.id.pheno_brapi_confirm_icon).apply {
            if (appIcon != null) setImageDrawable(appIcon)
        }
        findViewById<TextView>(R.id.pheno_brapi_confirm_app_name).text = appLabel
        findViewById<TextView>(R.id.pheno_brapi_confirm_message).text =
            getString(R.string.pheno_brapi_access_confirm_message, appLabel, accountName)

        findViewById<Button>(R.id.pheno_brapi_confirm_allow).setOnClickListener {
            grantAccess(accountName, callingPackage)
        }
        findViewById<Button>(R.id.pheno_brapi_confirm_deny).setOnClickListener {
            authenticatorResponse?.onError(AccountManager.ERROR_CODE_CANCELED, "User denied access")
            authenticatorResponse = null
            finish()
        }
    }

    private fun grantAccess(accountName: String, callingPackage: String) {
        val account = accessPolicy.findAccount(accountName)
            ?: return finishWithResult(accessPolicy.accountNotFoundBundle())

        accessPolicy.grantLegacyAccess(account, callingPackage)
        finishWithResult(
            accessPolicy.tokenResultBundle(account, BrapiAccountConstants.AUTH_TOKEN_TYPE),
        )
    }

    private fun finishWithResult(result: Bundle) {
        authenticatorResponse?.onResult(result)
        authenticatorResponse = null
        finish()
    }

    private fun appLabel(packageName: String): String =
        try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            BrapiAccountConstants.displayNameForPackage(packageName).ifEmpty { packageName }
        }

    override fun onDestroy() {
        authenticatorResponse?.onError(AccountManager.ERROR_CODE_CANCELED, "User dismissed")
        authenticatorResponse = null
        super.onDestroy()
    }
}
