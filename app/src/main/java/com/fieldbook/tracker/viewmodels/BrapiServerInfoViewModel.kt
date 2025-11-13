package com.fieldbook.tracker.viewmodels

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.brapi.io.fieldBookImplementedCalls
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory
import com.fieldbook.tracker.brapi.service.BrAPIServiceV1
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2
import com.fieldbook.tracker.utilities.BrapiImplementationHelper
import com.fieldbook.tracker.utilities.BrapiModuleCalls
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.brapi.v2.model.core.response.BrAPIServerInfoResponse
import javax.inject.Inject

@HiltViewModel
class BrapiServerInfoViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

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
    )

    private val _uiState = MutableLiveData(ServerInfoUiState())
    val uiState: LiveData<ServerInfoUiState> = _uiState

    private val brapiService: BrAPIService by lazy {
        BrAPIServiceFactory.getBrAPIService(context)
    }

    fun loadServerInfo() {
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
                handleServerInfoError()
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

    private fun handleServerInfoError() {
        _uiState.postValue(
            _uiState.value?.copy(
                isLoading = false,
                hasApiException = true
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
                isBrapiV1Incompatible = false,
            )
        )
    }
}