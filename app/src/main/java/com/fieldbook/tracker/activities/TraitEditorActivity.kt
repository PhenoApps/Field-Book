package com.fieldbook.tracker.activities

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.fieldbook.tracker.R
import com.fieldbook.tracker.dialogs.FileExploreDialogFragment
import com.fieldbook.tracker.dialogs.NewTraitDialog
import com.fieldbook.tracker.fragments.TraitDetailFragment
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.ui.screens.TraitEditorScreen
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.utilities.Utils
import com.fieldbook.tracker.viewmodels.TraitEditorViewModel
import dagger.hilt.android.AndroidEntryPoint
import org.phenoapps.utils.BaseDocumentTreeUtil

@AndroidEntryPoint
class TraitEditorActivity : ThemedActivity() {

    companion object {
        private const val TAG = "TraitEditorActivity"
    }

    private val viewModel: TraitEditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupBackCallback()

        setContent {
            AppTheme {
                TraitEditorScreen(
                    viewModel = viewModel,
                    onNavigateBack = { finish() },
                    onTraitSelected = { traitId -> showTraitDetailFragment(traitId) },
                    onShowCreateNewTraitDialog = { showCreateNewTraitDialog(null) },
                    onShowLocalFilePicker = { showLocalFilePickerDialog() }
                )
            }
        }
    }

    private fun setupBackCallback() {
        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    CollectActivity.reloadData = true
                    finish()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
    }

    private fun showTraitDetailFragment(traitId: String) {
        val fragment = TraitDetailFragment().apply {
            arguments = Bundle().apply {
                putString("traitId", traitId)
            }
        }

        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment, "TraitDetailFragmentTag")
            .addToBackStack(null)
            .commit()
    }

    private fun showCreateNewTraitDialog(trait: TraitObject?) {
        val dialog = NewTraitDialog(this)
        dialog.setTraitObject(trait)
        dialog.setOnTraitSavedListener { viewModel.loadTraits() }
        dialog.show(supportFragmentManager, "NewTraitDialog")
    }

    private fun showLocalFilePickerDialog() {
        val traitDir = BaseDocumentTreeUtil.getDirectory(this, R.string.dir_trait)
        if (traitDir?.exists() != true) {
            Utils.makeToast(this, getString(R.string.error_storage_directory))
            return
        }

        val dialog = FileExploreDialogFragment().apply {
            arguments = Bundle().apply {
                putString("path", traitDir.uri.toString())
                putString(
                    "dialogTitle",
                    this@TraitEditorActivity.getString(R.string.traits_dialog_import)
                )
                putStringArray("include", arrayOf("trt"))
            }

            setOnFileSelectedListener { selectedUri ->
                viewModel.importTraits(selectedUri)
            }
        }

        dialog.show(supportFragmentManager, "FilePickerDialog")
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadTraits()
    }
}