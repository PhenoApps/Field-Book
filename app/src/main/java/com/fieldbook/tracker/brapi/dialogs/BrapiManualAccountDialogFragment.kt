package com.fieldbook.tracker.brapi.dialogs

import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ScannerActivity
import com.fieldbook.tracker.activities.brapi.BrapiAuthActivity
import com.fieldbook.tracker.brapi.BrapiAuthenticator
import com.fieldbook.tracker.objects.BrAPIConfig
import com.fieldbook.tracker.utilities.BrapiAccountHelper
import com.fieldbook.tracker.utilities.JsonUtil
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Dialog for manually entering a new BrAPI server configuration.
 * Can be pre-populated from a scanned BrAPIConfig QR code.
 *
 * On "Authorize", stores the account config in AccountManager and launches BrapiAuthActivity.
 */
@AndroidEntryPoint
class BrapiManualAccountDialogFragment : DialogFragment() {

    companion object {
        const val TAG = "BrapiManualAccountDialog"

        private const val ARG_AUTH_RESPONSE = "auth_response"
        private const val ARG_START_SCAN = "start_scan"
        private const val ARG_PREFILL_CONFIG = "prefill_config"

        private const val REQUEST_SCAN_BASE_URL = 201
        private const val REQUEST_SCAN_OIDC_URL = 202
        private const val REQUEST_SCAN_CONFIG = 203
        private const val REQUEST_AUTH = 204

        fun newInstance(
            authResponse: AccountAuthenticatorResponse? = null,
            startScan: Boolean = false,
            prefillConfig: BrAPIConfig? = null
        ): BrapiManualAccountDialogFragment {
            return BrapiManualAccountDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_AUTH_RESPONSE, authResponse)
                    putBoolean(ARG_START_SCAN, startScan)
                    if (prefillConfig != null) {
                        putString(ARG_PREFILL_CONFIG, Gson().toJson(prefillConfig))
                    }
                }
            }
        }
    }

    @Inject
    lateinit var accountHelper: BrapiAccountHelper

    private var authResponse: AccountAuthenticatorResponse? = null

    private lateinit var urlEdit: TextInputEditText
    private lateinit var displayNameEdit: TextInputEditText
    private lateinit var oidcUrlEdit: TextInputEditText
    private lateinit var oidcClientIdEdit: TextInputEditText
    private lateinit var oidcScopeEdit: TextInputEditText
    private lateinit var oidcFlowSpinner: Spinner
    private lateinit var versionSpinner: Spinner

    private var mlkitEnabled = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        authResponse = arguments?.getParcelable(ARG_AUTH_RESPONSE)

        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_brapi_manual_account, null)

        urlEdit = view.findViewById(R.id.brapi_manual_url_edit)
        displayNameEdit = view.findViewById(R.id.brapi_manual_display_name_edit)
        oidcUrlEdit = view.findViewById(R.id.brapi_manual_oidc_url_edit)
        oidcClientIdEdit = view.findViewById(R.id.brapi_manual_oidc_client_id_edit)
        oidcScopeEdit = view.findViewById(R.id.brapi_manual_oidc_scope_edit)
        oidcFlowSpinner = view.findViewById(R.id.brapi_manual_oidc_flow_spinner)
        versionSpinner = view.findViewById(R.id.brapi_manual_version_spinner)

        // Set up OIDC flow spinner
        val oidcFlowOptions = resources.getStringArray(R.array.pref_brapi_oidc_flow)
        oidcFlowSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            oidcFlowOptions
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        // Default to Implicit Grant
        val implicitIndex = oidcFlowOptions.indexOfFirst {
            it.equals(getString(R.string.preferences_brapi_oidc_flow_oauth_implicit), ignoreCase = true)
        }
        if (implicitIndex >= 0) oidcFlowSpinner.setSelection(implicitIndex)

        // Set up BrAPI version spinner
        val versionOptions = resources.getStringArray(R.array.pref_brapi_version)
        versionSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            versionOptions
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        // Default to V2
        val v2Index = versionOptions.indexOfFirst { it.equals("V2", ignoreCase = true) }
        if (v2Index >= 0) versionSpinner.setSelection(v2Index)

        // Auto-populate display name from URL
        urlEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (displayNameEdit.text.isNullOrEmpty()) {
                    val url = s?.toString() ?: ""
                    val hostname = try {
                        java.net.URL(accountHelper.normalizeUrl(url)).host
                    } catch (e: Exception) { "" }
                    if (hostname.isNotEmpty()) displayNameEdit.setText(hostname)
                }
            }
        })

        // Barcode scan buttons
        view.findViewById<View>(R.id.brapi_manual_url_scan_btn).setOnClickListener {
            startScan(REQUEST_SCAN_BASE_URL)
        }
        view.findViewById<View>(R.id.brapi_manual_oidc_url_scan_btn).setOnClickListener {
            startScan(REQUEST_SCAN_OIDC_URL)
        }
        view.findViewById<View>(R.id.brapi_manual_display_name_auto_btn).setOnClickListener {
            val url = accountHelper.normalizeUrl(urlEdit.text?.toString()?.trim() ?: "")
            if (url.isNotEmpty() && url != "https://") fetchDisplayName(url)
        }

        // Pre-fill if a BrAPIConfig was passed
        val prefillJson = arguments?.getString(ARG_PREFILL_CONFIG)
        if (prefillJson != null) {
            try {
                val config = Gson().fromJson(prefillJson, BrAPIConfig::class.java)
                applyConfig(config)
            } catch (e: Exception) { /* ignore */ }
        }

        val dialog = AlertDialog.Builder(requireContext(), R.style.AppAlertDialog)
            .setTitle(R.string.brapi_add_account_title)
            .setView(view)
            .setPositiveButton(R.string.brapi_save_authorize, null) // set below to avoid auto-dismiss
            .setNegativeButton(R.string.dialog_cancel) { _, _ -> onCancelled() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                onAuthorize(dialog)
            }
        }

        return dialog
    }

    override fun onResume() {
        super.onResume()
        // Start barcode scan immediately if requested (Scan Configuration Code flow)
        if (arguments?.getBoolean(ARG_START_SCAN, false) == true) {
            arguments?.putBoolean(ARG_START_SCAN, false)
            startScan(REQUEST_SCAN_CONFIG)
        }
    }

    private fun startScan(requestCode: Int) {
        if (mlkitEnabled) {
            with(ScannerActivity) { requireActivity().requestCameraAndStartScanner(requestCode, null, null, null) }
        } else {
            IntentIntegrator(requireActivity())
                .setPrompt(getString(R.string.barcode_scanner_text))
                .setBeepEnabled(true)
                .setRequestCode(requestCode)
                .initiateScan()
        }
    }

    private fun onAuthorize(dialog: AlertDialog) {
        val url = accountHelper.normalizeUrl(urlEdit.text?.toString()?.trim() ?: "")
        if (url.isEmpty() || url == "https://") {
            Toast.makeText(requireContext(), R.string.brapi_base_url, Toast.LENGTH_SHORT).show()
            return
        }

        val displayName = displayNameEdit.text?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: url

        val oidcFlow = oidcFlowSpinner.selectedItem?.toString() ?: ""
        val oidcUrl = oidcUrlEdit.text?.toString()?.trim() ?: ""
        val oidcClientId = oidcClientIdEdit.text?.toString()?.trim() ?: ""
        val oidcScope = oidcScopeEdit.text?.toString()?.trim() ?: ""
        val brapiVersion = versionSpinner.selectedItem?.toString() ?: "V2"

        // Save config to AccountManager (no token yet — that comes after OAuth)
        accountHelper.addAccountConfig(
            serverUrl = url,
            displayName = displayName,
            oidcUrl = oidcUrl,
            oidcFlow = oidcFlow,
            oidcClientId = oidcClientId,
            oidcScope = oidcScope,
            brapiVersion = brapiVersion
        )

        // Set as active account so BrapiAuthActivity reads from it
        accountHelper.setActiveAccount(url)

        // Launch auth flow
        val intent = Intent(requireContext(), BrapiAuthActivity::class.java).apply {
            putExtra(BrapiAuthActivity.EXTRA_SERVER_URL, url)
            putExtra(BrapiAuthActivity.EXTRA_OIDC_URL, oidcUrl)
            putExtra(BrapiAuthActivity.EXTRA_OIDC_FLOW, oidcFlow)
            putExtra(BrapiAuthActivity.EXTRA_OIDC_CLIENT_ID, oidcClientId)
            putExtra(BrapiAuthActivity.EXTRA_OIDC_SCOPE, oidcScope)
            putExtra(BrapiAuthActivity.EXTRA_BRAPI_VERSION, brapiVersion)
            if (authResponse != null) {
                putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, authResponse)
            }
        }
        startActivityForResult(intent, REQUEST_AUTH)
        dialog.dismiss()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) {
            if (requestCode == REQUEST_AUTH && authResponse != null) {
                // Auth cancelled — finish the host activity only when started by AccountManager
                activity?.finish()
            }
            return
        }

        when (requestCode) {
            REQUEST_SCAN_BASE_URL -> {
                val scanned = extractScanned(data) ?: return
                urlEdit.setText(scanned)
            }
            REQUEST_SCAN_OIDC_URL -> {
                val scanned = extractScanned(data) ?: return
                oidcUrlEdit.setText(scanned)
            }
            REQUEST_SCAN_CONFIG -> {
                val scanned = extractScanned(data) ?: return
                if (JsonUtil.isJsonValid(scanned)) {
                    try {
                        val config = Gson().fromJson(scanned, BrAPIConfig::class.java)
                        applyConfig(config)
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), R.string.preferences_brapi_server_scan_error, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    urlEdit.setText(scanned)
                }
            }
            REQUEST_AUTH -> {
                // Auth succeeded — close add account activity
                authResponse?.onResult(Bundle().apply {
                    putString(AccountManager.KEY_ACCOUNT_NAME, urlEdit.text?.toString() ?: "")
                    putString(AccountManager.KEY_ACCOUNT_TYPE, BrapiAuthenticator.ACCOUNT_TYPE)
                })
                activity?.setResult(Activity.RESULT_OK)
                activity?.finish()
                dismiss()
            }
        }
    }

    private fun extractScanned(data: Intent?): String? {
        return if (mlkitEnabled) {
            data?.getStringExtra("barcode")
        } else {
            val result: IntentResult? = IntentIntegrator.parseActivityResult(Activity.RESULT_OK, data)
            result?.contents
        }
    }

    private fun applyConfig(config: BrAPIConfig) {
        config.url?.let { urlEdit.setText(it) }
        config.name?.let { displayNameEdit.setText(it) }
        config.oidcUrl?.let { oidcUrlEdit.setText(it) }
        config.clientId?.let { oidcClientIdEdit.setText(it) }
        config.scope?.let { oidcScopeEdit.setText(it) }

        // Set OIDC flow spinner
        val oidcFlowOptions = resources.getStringArray(R.array.pref_brapi_oidc_flow)
        val flowMatch = oidcFlowOptions.indexOfFirst {
            it.equals(config.authFlow, ignoreCase = true)
        }
        if (flowMatch >= 0) oidcFlowSpinner.setSelection(flowMatch)

        // Set version spinner
        val versionOptions = resources.getStringArray(R.array.pref_brapi_version)
        val versionStr = when {
            "v1".equals(config.version, ignoreCase = true) -> "V1"
            else -> "V2"
        }
        val versionMatch = versionOptions.indexOfFirst { it.equals(versionStr, ignoreCase = true) }
        if (versionMatch >= 0) versionSpinner.setSelection(versionMatch)
    }

    private fun onCancelled() {
        // Only finish the host activity when launched from the system AccountManager.
        // When shown from within BrapiPreferencesFragment, just dismiss.
        if (authResponse != null) {
            activity?.setResult(Activity.RESULT_CANCELED)
            activity?.finish()
        }
        dismiss()
    }

    /**
     * Fetches the server's display name from the BrAPI v2 /serverinfo endpoint on a background
     * thread. Falls back to the URL hostname if the request fails or returns no name.
     */
    private fun fetchDisplayName(baseUrl: String) {
        Thread {
            val serverName = try {
                val serverinfoUrl = baseUrl.trimEnd('/') + "/brapi/v2/serverinfo"
                val conn = java.net.URL(serverinfoUrl).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    org.json.JSONObject(body)
                        .optJSONObject("result")
                        ?.optString("serverName")
                        ?.takeIf { it.isNotEmpty() }
                } else null
            } catch (e: Exception) { null }

            val displayText = serverName
                ?: try { java.net.URL(baseUrl).host } catch (e: Exception) { "" }

            activity?.runOnUiThread {
                if (isAdded && displayText.isNotEmpty()) {
                    displayNameEdit.setText(displayText)
                }
            }
        }.start()
    }
}
