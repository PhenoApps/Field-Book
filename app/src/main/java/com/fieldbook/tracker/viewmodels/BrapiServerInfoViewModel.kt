package com.fieldbook.tracker.viewmodels

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.fieldbook.tracker.R
import com.fieldbook.tracker.brapi.fieldBookImplementedCalls
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory
import com.fieldbook.tracker.brapi.service.BrAPIServiceV1
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2
import com.fieldbook.tracker.brapi.service.core.ServerInfoService
import com.fieldbook.tracker.utilities.BrapiImplementationHelper
import com.fieldbook.tracker.utilities.BrapiModuleCalls
import com.fieldbook.tracker.utilities.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.brapi.client.v2.BrAPIClient
import org.brapi.client.v2.modules.core.ServerInfoApi
import org.brapi.v2.model.core.response.BrAPIServerInfoResponse
import javax.inject.Inject

@HiltViewModel
class BrapiServerInfoViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    companion object {
        /** Bundle/SavedStateHandle key for a specific server URL to check (bypasses active prefs). */
        const val ARG_SERVER_URL = "server_url"
    }

    private val brapiImplementationHelper by lazy {
        BrapiImplementationHelper(fieldBookImplementedCalls, context.getString(R.string.field_book))
    }

    data class ServerInfoUiState(
        val isLoading: Boolean = false,
        val serverName: String = "",
        val organizationName: String = "",
        val serverDescription: String = "",
        val modulesMap: Map<String, BrapiModuleCalls> = emptyMap(),
        val appCompatibility: BrapiModuleCalls? = null,
        val errorMessage: String? = null,
        val isBrapiV1Incompatible: Boolean = false,
        val hasApiException: Boolean = false,
        val apiExceptionCode: Int = 0,
    )

    private val _uiState = MutableLiveData(ServerInfoUiState())
    val uiState: LiveData<ServerInfoUiState> = _uiState

    private val brapiService: BrAPIService by lazy {
        BrAPIServiceFactory.getBrAPIService(context)
    }

    /**
     * Returns a [ServerInfoService] for the given server URL, constructing a dedicated
     * [BrAPIClient] directly rather than reading the active server from SharedPreferences.
     * This avoids mutating global state when checking a non-active server's compatibility.
     */
    private fun buildServerInfoServiceForUrl(serverUrl: String): ServerInfoService {
        val brapiUrl = serverUrl.trimEnd('/') + Constants.BRAPI_PATH_V2
        val timeoutMs = BrAPIService.getTimeoutValue(context) * 1000
        val apiClient = BrAPIClient(brapiUrl, timeoutMs)
        return ServerInfoService.Default(ServerInfoApi(apiClient))
    }

    fun loadServerInfo() {
        val customUrl = savedStateHandle.get<String>(ARG_SERVER_URL)

        if (customUrl != null) {
            // Targeted check for a specific server — never touches the active-server preference.
            clearErrors()
            _uiState.postValue(_uiState.value?.copy(isLoading = true))
            buildServerInfoServiceForUrl(customUrl).fetchServerInfo(
                onSuccess = { response ->
                    handleServerInfoSuccess(response)
                    null
                },
                onFail = { errorCode ->
                    handleServerInfoError(errorCode)
                    null
                }
            )
            return
        }

        if (brapiService is BrAPIServiceV1) {
            _uiState.postValue(_uiState.value?.copy(isBrapiV1Incompatible = true))
            return
        }

        clearErrors()
        _uiState.postValue(_uiState.value?.copy(isLoading = true))

        (brapiService as BrAPIServiceV2).serverInfoService.fetchServerInfo(
            onSuccess = { response ->
                handleServerInfoSuccess(response)
                null
            },
            onFail = { errorCode ->
                handleServerInfoError(errorCode)
                null
            }
        )
    }

    private fun handleServerInfoSuccess(response: BrAPIServerInfoResponse?) {
        response?.result?.let { serverInfo ->
            val calls = serverInfo.calls ?: emptyList()
            val comparisonMap = brapiImplementationHelper.compareImplementation(calls)
            val appCompatibility = brapiImplementationHelper.getAppCompatibility(calls, context)

            _uiState.postValue(
                _uiState.value?.copy(
                    isLoading = false,
                    serverName = serverInfo.serverName,
                    organizationName = serverInfo.organizationName,
                    serverDescription = serverInfo.serverDescription,
                    modulesMap = comparisonMap,
                    appCompatibility = appCompatibility
                )
            )
        } ?: handleEmptyResponse()
    }

    private fun handleServerInfoError(errorCode: Int = 0) {
        _uiState.postValue(
            _uiState.value?.copy(
                isLoading = false,
                hasApiException = true,
                apiExceptionCode = errorCode
            )
        )
    }

    private fun handleEmptyResponse() {
        _uiState.postValue(
            _uiState.value?.copy(
                isLoading = false,
                errorMessage = context.getString(R.string.brapi_server_returned_empty_response)
            )
        )
    }

    fun clearErrors() {
        _uiState.postValue(
            _uiState.value?.copy(
                errorMessage = null,
                hasApiException = false,
                apiExceptionCode = 0,
                isBrapiV1Incompatible = false,
            )
        )
    }
}