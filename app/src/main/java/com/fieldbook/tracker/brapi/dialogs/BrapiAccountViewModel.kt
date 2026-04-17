package com.fieldbook.tracker.brapi.dialogs

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.BrAPIConfig
import com.fieldbook.tracker.utilities.BrapiAccountHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Holds all form state and business logic for the BrAPI add/edit account dialogs.
 *
 * Scoped to the host activity via [activityViewModels], so state survives configuration
 * changes (rotation) automatically. Both [BrapiManualAccountDialogFragment] and
 * [BrapiStepperAccountDialogFragment] observe the same instance within their respective
 * host activities — they are always in separate activities so there is no shared-state conflict.
 *
 * One-shot navigation/error signals are emitted as [BrapiAccountEvent] via a [SharedFlow]
 * so fragments can handle them (launch auth activity, show toast, dismiss) without the
 * ViewModel holding Android framework references.
 */
@HiltViewModel
class BrapiAccountViewModel @Inject constructor(
    private val accountHelper: BrapiAccountHelper,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrapiAccountUiState())
    val uiState: StateFlow<BrapiAccountUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BrapiAccountEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<BrapiAccountEvent> = _events.asSharedFlow()

    // Receives normalized URLs; debounced collector triggers the serverinfo fetch.
    // This gives the manual form per-keystroke URL updates without firing a network
    // request on every character. The stepper calls fetchDisplayName() directly on Next.
    private val _urlForFetch = MutableStateFlow("")

    /**
     * Set to true in [onAuthorize] when the account did not exist before being added.
     * Used by the fragment to decide whether to offer removal on auth failure.
     */
    var accountWasNew: Boolean = false
        private set

    init {
        initDefaults()
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _urlForFetch
                .debounce(600L)
                .collect { url -> if (url.isNotEmpty()) fetchDisplayName(url) }
        }
    }

    // ── Initialization ────────────────────────────────────────────────────────

    /**
     * Resets all form state to a clean baseline with default dropdown values.
     * Call from a fragment's [onCreate] when [savedInstanceState] is null — i.e. on first
     * creation, but not on rotation (where savedInstanceState is non-null and the existing
     * ViewModel state should be preserved).
     */
    fun reset() {
        val oidcFlowOptions = context.resources.getStringArray(R.array.pref_brapi_oidc_flow)
        val implicitOption = context.getString(R.string.preferences_brapi_oidc_flow_oauth_implicit)
        val implicitIndex = oidcFlowOptions.indexOfFirst { it.equals(implicitOption, ignoreCase = true) }
        val defaultFlow = if (implicitIndex >= 0) oidcFlowOptions[implicitIndex]
                          else oidcFlowOptions.firstOrNull() ?: ""

        val versionOptions = context.resources.getStringArray(R.array.pref_brapi_version)
        val v2Index = versionOptions.indexOfFirst { it.equals("V2", ignoreCase = true) }
        val defaultVersion = if (v2Index >= 0) versionOptions[v2Index] else "V2"

        _uiState.value = BrapiAccountUiState(oidcFlow = defaultFlow, brapiVersion = defaultVersion)
        _urlForFetch.value = ""
        accountWasNew = false
    }

    private fun initDefaults() = reset()

    // ── Field updates ─────────────────────────────────────────────────────────

    fun updateUrl(url: String) {
        _uiState.value = _uiState.value.let { state ->
            val derivedOidcUrl = if (!state.oidcUrlExplicitlySet && url.isNotEmpty() && url != "https://") {
                url.trimEnd('/') + "/.well-known/openid-configuration"
            } else {
                state.oidcUrl
            }
            state.copy(url = url, oidcUrl = derivedOidcUrl)
        }
        val normalized = try { accountHelper.normalizeUrl(url) } catch (_: Exception) { "" }
        if (normalized.isNotEmpty() && normalized != "https://") {
            _urlForFetch.value = normalized
        }
    }

    fun updateDisplayName(name: String) {
        _uiState.value = _uiState.value.copy(displayName = name)
    }

    /**
     * Updates the OIDC URL.
     * [isUserEdit] = true locks in the value so future [updateUrl] calls do not overwrite it
     * with an auto-derived URL. Pass false for programmatic updates that should not suppress
     * auto-derivation (e.g. when the composable reflects a state-driven change back).
     */
    fun updateOidcUrl(url: String, isUserEdit: Boolean) {
        _uiState.value = _uiState.value.let { state ->
            state.copy(
                oidcUrl = url,
                oidcUrlExplicitlySet = if (isUserEdit) true else state.oidcUrlExplicitlySet,
            )
        }
    }

    fun updateOidcFlow(flow: String) { _uiState.value = _uiState.value.copy(oidcFlow = flow) }
    fun updateBrapiVersion(version: String) { _uiState.value = _uiState.value.copy(brapiVersion = version) }
    fun updateOidcClientId(id: String) { _uiState.value = _uiState.value.copy(oidcClientId = id) }
    fun updateOidcScope(scope: String) { _uiState.value = _uiState.value.copy(oidcScope = scope) }

    // ── Config from QR scan ───────────────────────────────────────────────────

    fun applyConfig(config: BrAPIConfig) {
        val oidcFlowOptions = context.resources.getStringArray(R.array.pref_brapi_oidc_flow)
        val flowMatch = oidcFlowOptions.indexOfFirst { it.equals(config.authFlow, ignoreCase = true) }
        val newFlow = if (flowMatch >= 0) oidcFlowOptions[flowMatch] else _uiState.value.oidcFlow

        val versionOptions = context.resources.getStringArray(R.array.pref_brapi_version)
        val versionStr = if ("v1".equals(config.version, ignoreCase = true)) "V1" else "V2"
        val versionMatch = versionOptions.indexOfFirst { it.equals(versionStr, ignoreCase = true) }
        val newVersion = if (versionMatch >= 0) versionOptions[versionMatch] else _uiState.value.brapiVersion

        _uiState.value = _uiState.value.let { state ->
            state.copy(
                url = config.url ?: state.url,
                displayName = config.name ?: state.displayName,
                oidcUrl = config.oidcUrl ?: state.oidcUrl,
                oidcClientId = config.clientId ?: state.oidcClientId,
                oidcScope = config.scope ?: state.oidcScope,
                oidcFlow = newFlow,
                brapiVersion = newVersion,
                oidcUrlExplicitlySet = !config.oidcUrl.isNullOrEmpty() || state.oidcUrlExplicitlySet,
            )
        }
    }

    // ── Stepper navigation ────────────────────────────────────────────────────

    fun onNext() {
        val state = _uiState.value
        when (state.currentStep) {
            0 -> _uiState.value = state.copy(currentStep = 1)
            1 -> {
                val normalized = try { accountHelper.normalizeUrl(state.url.trim()) } catch (_: Exception) { "" }
                if (normalized.isEmpty() || !isValidUrl(normalized)) {
                    viewModelScope.launch { _events.emit(BrapiAccountEvent.ShowError(R.string.brapi_invalid_url)) }
                    return
                }
                _uiState.value = state.copy(url = normalized, currentStep = 2)
                fetchDisplayName(normalized)
            }
        }
    }

    fun onBack() {
        _uiState.value = _uiState.value.let { it.copy(currentStep = (it.currentStep - 1).coerceAtLeast(0)) }
    }

    fun setStep(step: Int) {
        _uiState.value = _uiState.value.copy(currentStep = step)
    }

    // ── Account actions ───────────────────────────────────────────────────────

    fun onAuthorize() {
        val state = _uiState.value
        val url = try { accountHelper.normalizeUrl(state.url.trim()) } catch (_: Exception) { "" }
        if (url.isEmpty() || !isValidUrl(url)) {
            viewModelScope.launch { _events.emit(BrapiAccountEvent.ShowError(R.string.brapi_invalid_url)) }
            return
        }
        accountWasNew = accountHelper.getAccountByUrl(url) == null
        val displayName = state.displayName.trim().takeIf { it.isNotEmpty() } ?: url
        accountHelper.addAccountConfig(
            serverUrl = url,
            displayName = displayName,
            oidcUrl = state.oidcUrl.trim(),
            oidcFlow = state.oidcFlow,
            oidcClientId = state.oidcClientId.trim(),
            oidcScope = state.oidcScope.trim(),
            brapiVersion = state.brapiVersion,
        )
        accountHelper.setActiveAccount(url)
        viewModelScope.launch {
            _events.emit(
                BrapiAccountEvent.LaunchAuth(
                    url = url,
                    oidcUrl = state.oidcUrl.trim(),
                    oidcFlow = state.oidcFlow,
                    oidcClientId = state.oidcClientId.trim(),
                    oidcScope = state.oidcScope.trim(),
                    brapiVersion = state.brapiVersion,
                )
            )
        }
    }

    /**
     * Edit-mode save: persists the updated config without re-launching auth.
     * Emits [BrapiAccountEvent.EditSaved] so the fragment can send the fragment result
     * and dismiss.
     */
    fun onSaveEdit() {
        val state = _uiState.value
        val url = try { accountHelper.normalizeUrl(state.url.trim()) } catch (_: Exception) { "" }
        if (url.isEmpty() || !isValidUrl(url)) {
            viewModelScope.launch { _events.emit(BrapiAccountEvent.ShowError(R.string.brapi_invalid_url)) }
            return
        }
        val displayName = state.displayName.trim().takeIf { it.isNotEmpty() } ?: url
        accountHelper.addAccountConfig(
            serverUrl = url,
            displayName = displayName,
            oidcUrl = state.oidcUrl.trim(),
            oidcFlow = state.oidcFlow,
            oidcClientId = state.oidcClientId.trim(),
            oidcScope = state.oidcScope.trim(),
            brapiVersion = state.brapiVersion,
        )
        viewModelScope.launch { _events.emit(BrapiAccountEvent.EditSaved) }
    }

    fun onCancelled() {
        viewModelScope.launch { _events.emit(BrapiAccountEvent.Dismissed) }
    }

    /**
     * Removes the account that was just added in [onAuthorize] (if it was new).
     * Call this when the user declines to keep an unauthorized server.
     */
    fun removeNewAccount() {
        val url = try { accountHelper.normalizeUrl(_uiState.value.url.trim()) } catch (_: Exception) { "" }
        if (url.isNotEmpty()) accountHelper.removeAccount(url)
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private fun isValidUrl(url: String): Boolean {
        if (url.contains(' ')) return false
        return try {
            val parsed = java.net.URL(url)
            val scheme = parsed.protocol
            if (scheme != "http" && scheme != "https") return false
            val host = parsed.host ?: return false
            if (host.isEmpty()) return false
            // Accept dotted hostnames or bracketed IPv6 ([::1]). Reject bare single-word hosts.
            host.contains('.') || host.startsWith('[')
        } catch (_: Exception) {
            false
        }
    }

    private fun fetchDisplayName(baseUrl: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isFetchingDisplayName = true)
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
                } catch (_: Exception) { null }
            }
            val displayText = serverName ?: try { java.net.URL(baseUrl).host } catch (_: Exception) { "" }
            _uiState.value = _uiState.value.let { state ->
                state.copy(
                    displayName = if (displayText.isNotEmpty()) displayText else state.displayName,
                    isFetchingDisplayName = false,
                )
            }
        }
    }
}

// ── UI state ──────────────────────────────────────────────────────────────────

data class BrapiAccountUiState(
    val url: String = "",
    val displayName: String = "",
    val oidcUrl: String = "",
    val oidcClientId: String = "",
    val oidcScope: String = "",
    val oidcFlow: String = "",
    val brapiVersion: String = "",
    /** True once the user has manually edited the OIDC URL field or a scanned config provided one.
     *  Prevents [updateUrl] from auto-deriving and overwriting the user's explicit value. */
    val oidcUrlExplicitlySet: Boolean = false,
    /** Active step for the stepper dialog (0–2). Unused by the manual dialog. */
    val currentStep: Int = 0,
    /** True while a serverinfo network request is in flight. */
    val isFetchingDisplayName: Boolean = false,
)

// ── Events ────────────────────────────────────────────────────────────────────

sealed class BrapiAccountEvent {
    /** Fragment should build the BrapiAuthActivity intent and launch it. */
    data class LaunchAuth(
        val url: String,
        val oidcUrl: String,
        val oidcFlow: String,
        val oidcClientId: String,
        val oidcScope: String,
        val brapiVersion: String,
    ) : BrapiAccountEvent()

    /** Fragment should show a Toast with this string resource. */
    data class ShowError(@StringRes val messageRes: Int) : BrapiAccountEvent()

    /** Fragment should dismiss (and finish the host activity if launched from AccountManager). */
    object Dismissed : BrapiAccountEvent()

    /** Manual edit saved — fragment should send fragment result and dismiss. */
    object EditSaved : BrapiAccountEvent()
}
