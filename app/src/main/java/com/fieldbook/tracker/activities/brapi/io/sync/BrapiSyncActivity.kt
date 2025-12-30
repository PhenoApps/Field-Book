package com.fieldbook.tracker.activities.brapi.io.sync

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.media3.common.util.Log
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.activities.brapi.BrapiAuthActivity
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.utilities.Utils
import dagger.hilt.android.AndroidEntryPoint

/**
 * An [android.app.Activity] that manages the user interface for synchronizing study data to a BrAPI (Breeding API) server.
 *
 * This activity is responsible for:
 * - Displaying the import/export options and progress to the user using a Jetpack Compose UI.
 * - Coordinating with the [BrapiSyncViewModel] to handle the logic of downloading data from
 *   the BrAPI server, merging it with local data, and uploading the result.
 * - Validating prerequisites such as network connectivity and BrAPI server configuration.
 * - Handling user interactions like starting, canceling, and configuring the export process.
 * - Launching the [BrapiAuthActivity] if re-authentication is required.
 *
 * This activity is launched with an intent that must contain a list of field IDs to be processed.
 * The list of IDs is passed via an [ArrayList] of [Int] with the key [FIELD_IDS].
 *
 * @see BrapiSyncViewModel
 * @see BrapiSyncScreen
 */
@AndroidEntryPoint
class BrapiSyncActivity : ThemedActivity() {

    companion object {
        private val TAG: String = BrapiSyncActivity::class.java.name
        const val FIELD_IDS: String = "FIELD_ID"
    }

    private val viewModel: BrapiSyncViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val fieldIds = intent.getIntegerArrayListExtra(FIELD_IDS)
        if (!validatePrerequisites(fieldIds)) {
            finish()
            return
        }

        //initialize the ViewModel with the first field ID
        fieldIds?.firstOrNull()?.let { id ->
            viewModel.initialize(id)
        }

        val brapiDisplayName = prefs.getString(PreferenceKeys.BRAPI_DISPLAY_NAME, "BrAPI") ?: "BrAPI"

        viewModel.setServerDisplayName(brapiDisplayName)

        setContent {

            AppTheme {
                val uiState by viewModel.uiState.collectAsState()

                BrapiSyncScreen(
                    uiState = uiState,
                    onDownloadClick = {
                        viewModel.startDownload()
                    },
                    onCancelDownloadClick = {
                        viewModel.cancelActiveJob()
                        fieldIds?.firstOrNull()?.let { id ->
                            viewModel.initialize(id)
                        }
                    },
                    onExportClick = {
                        viewModel.startUpload()
                    },
                    onCancelExportClick = {
                        viewModel.cancelActiveJob()
                        fieldIds?.firstOrNull()?.let { id ->
                            viewModel.initialize(id)
                        }
                    },
                    onNavigateUp = { finish() },
                    onImageUploadToggle = {
                        viewModel.toggleImageUpload()
                    },
                    onAuthenticate = {
                        viewModel.cancelActiveJob()
                        startActivity(Intent(this, BrapiAuthActivity::class.java))
                    },
                    onMergeStrategyChange = {
                        // Record the chosen strategy for UI state and immediately apply resolution to pending conflicts
                        viewModel.setMergeStrategy(it)
                        viewModel.applyConflictResolution(it)
                    },
                    // persist last-checked timestamps via ViewModel helper methods
                    onPersistLastCheckedDownload = { text -> viewModel.persistLastCheckedDownload(text) },
                    onApplyManualChoices = { choices ->
                        viewModel.setMergeStrategy(MergeStrategy.Manual)
                        viewModel.applyConflictResolution(MergeStrategy.Manual, choices)
                    }
                )
            }
        }

        onBackPressedDispatcher.addCallback(this, standardBackCallback())

    }

    override fun onResume() {
        super.onResume()
        // When returning from the authentication flow, refresh the BrAPI client inside the ViewModel
        // so any newly obtained credentials are used immediately.
        viewModel.resetClient()
    }

    private fun validatePrerequisites(fieldIds: ArrayList<Int>?): Boolean {
        if (!Utils.isConnected(this)) {
            Log.w(TAG, "Device is offline")
            Toast.makeText(applicationContext, R.string.device_offline_warning, Toast.LENGTH_SHORT)
                .show()
            return false
        }

        if (!BrAPIService.hasValidBaseUrl(this)) {
            Log.w(TAG, "BrAPI base URL is not configured")
            Toast.makeText(
                applicationContext,
                R.string.brapi_must_configure_url,
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        if (fieldIds.isNullOrEmpty()) {
            Log.w(TAG, "Intent missing or has empty FIELD_IDS extra")
            Toast.makeText(this, getString(R.string.no_fields_to_process), Toast.LENGTH_SHORT)
                .show()
            return false
        }

        return true
    }
}
