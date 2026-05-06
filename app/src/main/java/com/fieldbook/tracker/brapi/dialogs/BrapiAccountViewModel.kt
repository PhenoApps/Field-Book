package com.fieldbook.tracker.brapi.dialogs

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldbook.tracker.R
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
import org.phenoapps.brapi.ui.BrapiAccountConfig
import org.phenoapps.brapi.ui.BrapiAccountUiState
import org.phenoapps.brapi.ui.defaultBrapiAccountState
import org.phenoapps.brapi.ui.isValidBrapiUrl
import org.phenoapps.brapi.ui.withConfig
import org.phenoapps.brapi.ui.withUrlUpdate
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

    /**
     * The server URL the account was originally saved under, captured at the start of an edit
     * session. Passed to [BrapiAccountHelper.addAccountConfig] so the existing account is found
     * by its old URL even if the user changes the URL field during editing.
     */
    private var editOriginalUrl: String? = null

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
        _uiState.value = defaultBrapiAccountState(
            context,
            context.getString(R.string.brapi_oidc_clientid_default),
        )
        _urlForFetch.value = ""
        accountWasNew = false
        editOriginalUrl = null
    }

    /** Records the URL the account is currently stored under before the user edits it. */
    fun setEditOriginalUrl(url: String) {
        editOriginalUrl = url
    }

    private fun initDefaults() = reset()

    // ── Field updates ─────────────────────────────────────────────────────────

    fun updateUrl(url: String) {
        _uiState.value = _uiState.value.withUrlUpdate(url)
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

    fun applyConfig(config: BrapiAccountConfig) {
        _uiState.value = _uiState.value.withConfig(config)
    }

    // ── Stepper navigation ────────────────────────────────────────────────────

    fun onNext() {
        val state = _uiState.value
        when (state.currentStep) {
            0 -> _uiState.value = state.copy(currentStep = 1)
            1 -> {
                val normalized = try { accountHelper.normalizeUrl(state.url.trim()) } catch (_: Exception) { "" }
                if (normalized.isEmpty() || !isValidBrapiUrl(normalized)) {
                    showInvalidUrlError()
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
        if (url.isEmpty() || !isValidBrapiUrl(url)) {
            showInvalidUrlError()
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
        if (url.isEmpty() || !isValidBrapiUrl(url)) {
            showInvalidUrlError()
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
            originalServerUrl = editOriginalUrl,
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

    private fun showInvalidUrlError() {
        viewModelScope.launch {
            _events.emit(
                BrapiAccountEvent.ShowError(
                    org.phenoapps.brapi.R.string.pheno_brapi_invalid_url,
                ),
            )
        }
    }
}

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
