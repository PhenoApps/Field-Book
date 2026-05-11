package com.fieldbook.tracker.preferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.PreferencesActivity
import com.fieldbook.tracker.preferences.composables.ServerInfoScreen
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.utilities.Utils
import com.fieldbook.tracker.viewmodels.BrapiServerInfoViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BrapiServerInfoFragment : Fragment() {

    private val viewModel: BrapiServerInfoViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setupToolbar()

        return ComposeView(requireContext()).apply {
            setContent {
                AppTheme {
                    val uiState by viewModel.uiState.observeAsState(BrapiServerInfoViewModel.ServerInfoUiState())

                    ServerInfoScreen(uiState)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.uiState.observe(viewLifecycleOwner) { uiState ->
            if (uiState.isBrapiV1Incompatible) {
                showToast(getString(R.string.brapi_v1_is_not_compatible))
            }

            if (!uiState.errorMessage.isNullOrEmpty()) {
                showToast(uiState.errorMessage)
            }

            if (uiState.hasApiException) {
                showToast(getString(R.string.act_brapi_list_api_exception))
            }
        }

        viewModel.loadServerInfo()
    }

    private fun showToast(message: String) {
        viewModel.clearErrors()

        Utils.makeToast(requireContext(), message)
        parentFragmentManager.popBackStack()
    }

    private fun setupToolbar() {
        val activity = activity as? PreferencesActivity
        activity?.supportActionBar?.apply {
            title = getString(R.string.brapi_compatibility)
            setDisplayHomeAsUpEnabled(true)
        }
    }
}