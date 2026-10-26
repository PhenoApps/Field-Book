package com.fieldbook.tracker.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.fieldbook.tracker.R
import com.fieldbook.tracker.interfaces.FieldSortController
import com.fieldbook.tracker.ui.dialogs.sort.FieldSortDialog
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.viewmodels.FieldSortViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Opens from the field detail "Sort" chip.
 * Edits the columns a field's entries are sorted by, and hands the result to the activity's FieldSortController on OK.
 */
@AndroidEntryPoint
class FieldSortDialogFragment : DialogFragment() {

    companion object {

        const val TAG = "FieldSortDialogFragment"

        private const val ARG_STUDY_ID = "study_id"

        fun newInstance(studyId: Int) = FieldSortDialogFragment().apply {
            arguments = bundleOf(ARG_STUDY_ID to studyId)
        }
    }

    private val viewModel: FieldSortViewModel by viewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val studyId = requireArguments().getInt(ARG_STUDY_ID)

        viewModel.load(studyId)

        val dialog = Dialog(requireContext(), R.style.AppAlertDialog)

        dialog.setContentView(ComposeView(requireContext()).apply {
            setContent {
                AppTheme {
                    val keys by viewModel.keys.collectAsState()
                    val state by viewModel.uiState.collectAsState()

                    FieldSortDialog(
                        keys = keys,
                        state = state,
                        onAddKey = viewModel::addKey,
                        onRemoveKey = viewModel::removeKey,
                        onMoveKey = viewModel::moveKey,
                        onToggleDirection = viewModel::toggleDirection,
                        onToggleSerpentine = viewModel::toggleSerpentine,
                        onClearKeys = viewModel::clearKeys,
                        onConfirm = {
                            (activity as? FieldSortController)?.submitSort(studyId, viewModel.sortSpec())
                            dismiss()
                        },
                        onCancel = { dismiss() },
                    )
                }
            }
        })

        return dialog
    }
}
