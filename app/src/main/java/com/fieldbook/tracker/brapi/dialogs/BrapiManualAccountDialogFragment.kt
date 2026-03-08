package com.fieldbook.tracker.brapi.dialogs

import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.brapi.BrapiAuthActivity
import com.fieldbook.tracker.brapi.BrapiAuthenticator
import com.fieldbook.tracker.objects.BrAPIConfig
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.utilities.BrapiAccountHelper
import com.fieldbook.tracker.utilities.JsonUtil
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import com.fieldbook.tracker.activities.CameraActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        const val REQUEST_KEY_EDIT_SAVED = "brapi_edit_account_saved"

        private const val ARG_AUTH_RESPONSE = "auth_response"
        private const val ARG_START_SCAN = "start_scan"
        private const val ARG_PREFILL_CONFIG = "prefill_config"
        private const val ARG_EDIT_MODE = "edit_mode"

        private const val REQUEST_SCAN_BASE_URL = 201
        private const val REQUEST_SCAN_OIDC_URL = 202
        private const val REQUEST_SCAN_CONFIG = 203
        private const val REQUEST_AUTH = 204

        fun newInstance(
            authResponse: AccountAuthenticatorResponse? = null,
            startScan: Boolean = false,
            prefillConfig: BrAPIConfig? = null,
            editMode: Boolean = false
        ): BrapiManualAccountDialogFragment {
            return BrapiManualAccountDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_AUTH_RESPONSE, authResponse)
                    putBoolean(ARG_START_SCAN, startScan)
                    putBoolean(ARG_EDIT_MODE, editMode)
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
    private lateinit var oidcFlowSpinner: AutoCompleteTextView
    private lateinit var versionSpinner: AutoCompleteTextView

    private var isEditMode = false

    /** True when the OIDC URL was set by the user (directly or via scan), suppressing auto-derive. */
    private var oidcUrlExplicitlySet = false

    /** Set to true while the code is programmatically updating oidcUrlEdit to avoid triggering the explicit flag. */
    private var suppressOidcWatcher = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        authResponse = arguments?.getParcelable(ARG_AUTH_RESPONSE)
        isEditMode = arguments?.getBoolean(ARG_EDIT_MODE, false) == true

        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_brapi_manual_account, null)

        urlEdit = view.findViewById(R.id.brapi_manual_url_edit)
        displayNameEdit = view.findViewById(R.id.brapi_manual_display_name_edit)
        oidcUrlEdit = view.findViewById(R.id.brapi_manual_oidc_url_edit)
        oidcClientIdEdit = view.findViewById(R.id.brapi_manual_oidc_client_id_edit)
        oidcScopeEdit = view.findViewById(R.id.brapi_manual_oidc_scope_edit)
        oidcFlowSpinner = view.findViewById(R.id.brapi_manual_oidc_flow_spinner)
        versionSpinner = view.findViewById(R.id.brapi_manual_version_spinner)

        // Apply gray hint color to all fields. We do this programmatically because
        // ?attr/fb_color_text_secondary can fail to resolve inside the dialog's
        // ContextThemeWrapper, causing Material to fall back to colorOnSurface (black).
        val grayHint = ContextCompat.getColor(requireContext(), R.color.main_color_text_secondary)
        val grayHintList = ColorStateList.valueOf(grayHint)
        listOf(
            view.findViewById<TextInputLayout>(R.id.brapi_manual_url_input_layout),
            view.findViewById(R.id.brapi_manual_display_name_layout),
            view.findViewById(R.id.brapi_manual_oidc_flow_layout),
            view.findViewById(R.id.brapi_manual_oidc_url_layout),
            view.findViewById(R.id.brapi_manual_oidc_client_id_layout),
            view.findViewById(R.id.brapi_manual_oidc_scope_layout),
            view.findViewById(R.id.brapi_manual_version_layout)
        ).forEach { layout ->
            layout?.defaultHintTextColor = grayHintList
            layout?.hintTextColor = grayHintList
        }
        listOf(urlEdit, displayNameEdit, oidcUrlEdit, oidcClientIdEdit, oidcScopeEdit).forEach {
            it.setHintTextColor(grayHint)
        }

        // Set up OIDC flow exposed dropdown
        val oidcFlowOptions = resources.getStringArray(R.array.pref_brapi_oidc_flow)
        val oidcFlowAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, oidcFlowOptions)
        oidcFlowSpinner.setAdapter(oidcFlowAdapter)
        val implicitIndex = oidcFlowOptions.indexOfFirst {
            it.equals(getString(R.string.preferences_brapi_oidc_flow_oauth_implicit), ignoreCase = true)
        }
        if (implicitIndex >= 0) oidcFlowSpinner.setText(oidcFlowOptions[implicitIndex], false)

        // Set up BrAPI version exposed dropdown
        val versionOptions = resources.getStringArray(R.array.pref_brapi_version)
        val versionAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, versionOptions)
        versionSpinner.setAdapter(versionAdapter)
        val v2Index = versionOptions.indexOfFirst { it.equals("V2", ignoreCase = true) }
        if (v2Index >= 0) versionSpinner.setText(versionOptions[v2Index], false)

        // Auto-derive OIDC discovery URL from base URL while typing
        urlEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val raw = s?.toString()?.trim() ?: ""
                val normalized = try { accountHelper.normalizeUrl(raw) } catch (e: Exception) { "" }

                // Auto-derive OIDC discovery URL unless user has explicitly set it
                if (!oidcUrlExplicitlySet && normalized.isNotEmpty() && normalized != "https://") {
                    val derived = normalized.trimEnd('/') + "/.well-known/openid-configuration"
                    suppressOidcWatcher = true
                    oidcUrlEdit.setText(derived)
                    suppressOidcWatcher = false
                }
            }
        })

        // Auto-populate display name via server fetch when focus leaves the URL field
        urlEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && displayNameEdit.text.isNullOrEmpty()) {
                val raw = urlEdit.text?.toString()?.trim() ?: ""
                val url = try { accountHelper.normalizeUrl(raw) } catch (e: Exception) { "" }
                if (url.isNotEmpty() && url != "https://") fetchDisplayName(url)
            }
        }

        // Also fetch display name from server when the display name field is focused and empty
        displayNameEdit.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && displayNameEdit.text.isNullOrEmpty()) {
                val raw = urlEdit.text?.toString()?.trim() ?: ""
                val url = try { accountHelper.normalizeUrl(raw) } catch (e: Exception) { "" }
                if (url.isNotEmpty() && url != "https://") fetchDisplayName(url)
            }
        }

        // Track explicit user edits to the OIDC URL field
        oidcUrlEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!suppressOidcWatcher) oidcUrlExplicitlySet = true
            }
        })

        // Barcode scan buttons
        view.findViewById<View>(R.id.brapi_manual_url_scan_btn).setOnClickListener {
            startScan(REQUEST_SCAN_BASE_URL)
        }
        view.findViewById<View>(R.id.brapi_manual_oidc_url_scan_btn).setOnClickListener {
            oidcUrlExplicitlySet = true
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
            .setTitle(if (isEditMode) R.string.brapi_edit_account_title else R.string.brapi_add_account_title)
            .setView(view)
            .create()

        // Wire embedded buttons
        val authorizeBtn = view.findViewById<android.widget.Button>(R.id.brapi_btn_authorize)
        if (isEditMode) {
            authorizeBtn.setText(R.string.dialog_save)
            authorizeBtn.setOnClickListener { onSaveEdit(dialog) }
        } else {
            authorizeBtn.setOnClickListener { onAuthorize(dialog) }
        }
        view.findViewById<View>(R.id.brapi_btn_cancel).setOnClickListener {
            onCancelled()
            dialog.dismiss()
        }

        dialog.setOnShowListener {
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
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
        val intent = Intent(requireContext(), CameraActivity::class.java)
        intent.putExtra(CameraActivity.EXTRA_MODE, CameraActivity.MODE_BARCODE)
        startActivityForResult(intent, requestCode)
    }

    /**
     * Edit-mode save: persists config changes to AccountManager without launching re-auth.
     * Notifies the parent fragment via FragmentResult so it can refresh the server cards.
     */
    private fun onSaveEdit(dialog: AlertDialog) {
        val url = accountHelper.normalizeUrl(urlEdit.text?.toString()?.trim() ?: "")
        if (url.isEmpty() || url == "https://") {
            Toast.makeText(requireContext(), R.string.brapi_base_url, Toast.LENGTH_SHORT).show()
            return
        }

        val displayName = displayNameEdit.text?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() } ?: url
        val oidcFlow = oidcFlowSpinner.text?.toString() ?: ""
        val oidcUrl = oidcUrlEdit.text?.toString()?.trim() ?: ""
        val oidcClientId = oidcClientIdEdit.text?.toString()?.trim() ?: ""
        val oidcScope = oidcScopeEdit.text?.toString()?.trim() ?: ""
        val brapiVersion = versionSpinner.text?.toString() ?: "V2"

        accountHelper.addAccountConfig(
            serverUrl = url,
            displayName = displayName,
            oidcUrl = oidcUrl,
            oidcFlow = oidcFlow,
            oidcClientId = oidcClientId,
            oidcScope = oidcScope,
            brapiVersion = brapiVersion
        )

        parentFragmentManager.setFragmentResult(REQUEST_KEY_EDIT_SAVED, Bundle.EMPTY)
        dialog.dismiss()
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

        val oidcFlow = oidcFlowSpinner.text?.toString() ?: ""
        val oidcUrl = oidcUrlEdit.text?.toString()?.trim() ?: ""
        val oidcClientId = oidcClientIdEdit.text?.toString()?.trim() ?: ""
        val oidcScope = oidcScopeEdit.text?.toString()?.trim() ?: ""
        val brapiVersion = versionSpinner.text?.toString() ?: "V2"

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
        return data?.getStringExtra(CameraActivity.EXTRA_BARCODE)
    }

    private fun applyConfig(config: BrAPIConfig) {
        // If config provides an explicit OIDC URL, lock it in before setting the base URL
        // (so the URL watcher's auto-derive doesn't overwrite it).
        if (!config.oidcUrl.isNullOrEmpty()) {
            oidcUrlExplicitlySet = true
        }
        config.url?.let { urlEdit.setText(it) }
        config.name?.let { displayNameEdit.setText(it) }
        if (!config.oidcUrl.isNullOrEmpty()) {
            suppressOidcWatcher = true
            oidcUrlEdit.setText(config.oidcUrl)
            suppressOidcWatcher = false
        }
        config.clientId?.let { oidcClientIdEdit.setText(it) }
        config.scope?.let { oidcScopeEdit.setText(it) }

        // Set OIDC flow dropdown
        val oidcFlowOptions = resources.getStringArray(R.array.pref_brapi_oidc_flow)
        val flowMatch = oidcFlowOptions.indexOfFirst {
            it.equals(config.authFlow, ignoreCase = true)
        }
        if (flowMatch >= 0) oidcFlowSpinner.setText(oidcFlowOptions[flowMatch], false)

        // Set version dropdown
        val versionOptions = resources.getStringArray(R.array.pref_brapi_version)
        val versionStr = when {
            "v1".equals(config.version, ignoreCase = true) -> "V1"
            else -> "V2"
        }
        val versionMatch = versionOptions.indexOfFirst { it.equals(versionStr, ignoreCase = true) }
        if (versionMatch >= 0) versionSpinner.setText(versionOptions[versionMatch], false)
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
     * Fetches the server's display name from the BrAPI v2 /serverinfo endpoint.
     * Falls back to the URL hostname if the request fails or returns no name.
     */
    private fun fetchDisplayName(baseUrl: String) {
        lifecycleScope.launch {
            val serverName = withContext(Dispatchers.IO) {
                try {
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
            }

            val displayText = serverName
                ?: try { java.net.URL(baseUrl).host } catch (e: Exception) { "" }

            if (isAdded && displayText.isNotEmpty()) {
                displayNameEdit.setText(displayText)
            }
        }
    }
}
