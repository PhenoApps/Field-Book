package com.fieldbook.tracker.activities

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.database.viewmodels.TraitDetailViewModel
import com.fieldbook.tracker.dialogs.FileExploreDialogFragment
import com.fieldbook.tracker.dialogs.NewTraitDialog
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.ui.screens.traits.TraitEditorScreen
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.utilities.Utils
import com.fieldbook.tracker.database.viewmodels.TraitEditorViewModel
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.traits.formats.NumericFormat
import com.fieldbook.tracker.traits.formats.TraitFormat
import com.fieldbook.tracker.traits.formats.parameters.BaseFormatParameter
import com.fieldbook.tracker.traits.formats.parameters.DecimalPlacesParameter
import com.fieldbook.tracker.ui.navigation.controllers.TraitNavController
import com.fieldbook.tracker.ui.navigation.routes.TraitDetail
import com.fieldbook.tracker.ui.navigation.routes.TraitEditor
import com.fieldbook.tracker.ui.navigation.routes.TraitGraph
import com.fieldbook.tracker.ui.screens.traits.TraitDetailScreen
import com.fieldbook.tracker.utilities.SoundHelperImpl
import com.fieldbook.tracker.utilities.VibrateUtil
import dagger.hilt.android.AndroidEntryPoint
import org.phenoapps.utils.BaseDocumentTreeUtil
import javax.inject.Inject

@AndroidEntryPoint
class TraitActivity : ThemedActivity() {

    @Inject
    lateinit var soundHelperImpl: SoundHelperImpl

    @Inject
    lateinit var vibrator: VibrateUtil

    @Inject
    lateinit var database: DataHelper

    @Inject
    lateinit var traitRepo: TraitRepository

    companion object {
        private const val TAG = "TraitEditorActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupBackCallback()

        setContent {
            AppTheme {
                // actual controller of NavHost
                // use this for accessing backStackEntry, state handling, etc
                val navController = rememberNavController()

                // wrapper around navController
                // use this for all navigation
                val traitNav = remember { TraitNavController(navController) }

                NavHost(
                    navController = navController,
                    startDestination = TraitGraph
                ) {
                    navigation<TraitGraph>(startDestination = TraitEditor) {

                        composable<TraitEditor> { backStackEntry ->

                            val parentEntry = remember(backStackEntry) {
                                navController.getBackStackEntry(TraitGraph)
                            }

                            val editorViewModel: TraitEditorViewModel = hiltViewModel(parentEntry)

                            TraitEditorScreen(
                                viewModel = editorViewModel,
                                onNavigateBack = { finish() },
                                onTraitDetail = { traitId ->
                                    traitNav.navigateToTraitDetail(
                                        traitId = traitId,
                                        from = backStackEntry,
                                    )
                                },
                                onShowCreateNewTraitDialog = {
                                    showCreateNewTraitDialog(
                                        trait = null,
                                        onSaved = { editorViewModel.loadTraits() },
                                    )
                                },
                                onShowLocalFilePicker = {
                                    showLocalFilePickerDialog(
                                        onImported = {
                                            editorViewModel.importTraits(it)
                                        }
                                    )
                                }
                            )
                        }

                        composable<TraitDetail> { backStackEntry ->
                            val args: TraitDetail = backStackEntry.toRoute()


                            val parentEntry = remember(backStackEntry) {
                                navController.getBackStackEntry(TraitGraph)
                            }

                            val editorViewModel: TraitEditorViewModel = hiltViewModel(parentEntry)
                            val detailViewModel: TraitDetailViewModel = hiltViewModel(parentEntry)

                            TraitDetailScreen(
                                traitId = args.traitId,
                                detailViewModel = detailViewModel,
                                editorViewModel = editorViewModel,
                                onShowConfigureTraitDialog = { trait ->
                                    showCreateNewTraitDialog(
                                        trait = trait,
                                        onSaved = {
                                            trait?.let { detailViewModel.loadTraitDetails(it.id) }
                                            editorViewModel.loadTraits()
                                        },
                                    )
                                },
                                onBack = { traitNav.back() },
                                onResourceFilePickerDialog = { onFileSelected ->
                                    showResourceFilePickerDialog(onFileSelected)
                                },
                                onShowParameterEditDialog = { param, trait, onParamUpdate ->
                                    showParameterEditDialog(param, trait) { updatedTrait ->
                                        onParamUpdate(updatedTrait)
                                    }
                                },
                            )
                        }

                    }
                }
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

    private fun showCreateNewTraitDialog(trait: TraitObject?, onSaved: () -> Unit) {
        val dialog = NewTraitDialog(this)
        dialog.setTraitObject(trait)
        dialog.setOnTraitSavedListener { onSaved() }
        dialog.show(supportFragmentManager, "NewTraitDialog")
    }

    private fun showLocalFilePickerDialog(onImported: (Uri) -> Unit) {
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
                    this@TraitActivity.getString(R.string.traits_dialog_import)
                )
                putStringArray("include", arrayOf("trt"))
            }

            setOnFileSelectedListener { selectedUri ->
                onImported(selectedUri)
            }
        }

        dialog.show(supportFragmentManager, "FilePickerDialog")
    }

    private fun showResourceFilePickerDialog(onFileSelected: (String) -> Unit) {
        val resDir = BaseDocumentTreeUtil.getDirectory(this, R.string.dir_resources)
        if (resDir?.exists() != true) {
            Utils.makeToast(this, getString(R.string.error_storage_directory))
            return
        }

        val dialog = FileExploreDialogFragment().apply {
            arguments = Bundle().apply {
                putString("path", resDir.uri.toString())
                putString(
                    "dialogTitle",
                    this@TraitActivity.getString(R.string.main_toolbar_resources)
                )
                putStringArray("include", arrayOf("jpg", "jpeg", "png", "bmp"))
            }

            setOnFileSelectedListener { selectedUri ->
                onFileSelected(selectedUri.toString())
            }
        }

        dialog.show(supportFragmentManager, "ResourceFilePickerDialog")
    }

    /**
     * [onUpdated] calls detailViewModel.updateAttributes() and updates UI state in detail
     */
    private fun showParameterEditDialog(
        parameter: BaseFormatParameter, trait: TraitObject,
        onUpdated: (TraitObject) -> Unit,
    ) {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_trait_parameter_edit, null)

        val format = Formats.entries.find { it.getDatabaseName() == trait.format }
        val formatDefinition = format?.getTraitFormatDefinition()

        val parameterContainer = dialogView.findViewById<LinearLayout>(R.id.parameter_container)

        parameter.createViewHolder(parameterContainer)?.let { holder ->
            holder.bind(parameter, trait)
            parameterContainer.addView(holder.itemView)

            val dialog = AlertDialog.Builder(this, R.style.AppAlertDialog)
                .setView(dialogView)
                .setPositiveButton(R.string.dialog_save, null)
                .setNegativeButton(R.string.dialog_cancel, null)
                .create()

            dialog.setOnShowListener {
                val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                positiveButton.setOnClickListener {
                    holder.textInputLayout.error = null

                    // parameter specific validation
                    val paramValidationResult = holder.validate(traitRepo, trait)

                    if (paramValidationResult.result != true) {
                        val errorMessage = paramValidationResult.error
                            ?: getString(R.string.error_loading_trait_detail)
                        holder.textInputLayout.error = errorMessage
                        soundHelperImpl.playError()
                        vibrator.vibrate()
                        return@setOnClickListener
                    }

                    val updatedTrait = holder.merge(trait.clone())

                    // inter-parameter validation if needed
                    if (formatDefinition != null && paramHasValidationDependency(
                            formatDefinition,
                            parameter
                        )
                    ) {

                        val errorMessage =
                            validateInterParameterValidation(formatDefinition, updatedTrait)

                        if (errorMessage != null) {
                            holder.textInputLayout.error = errorMessage
                            soundHelperImpl.playError()
                            vibrator.vibrate()
                            return@setOnClickListener
                        }
                    }

                    onUpdated(updatedTrait)
                    CollectActivity.reloadData = true

                    dialog.dismiss()
                }
            }

            dialog.show()
        }
    }

    /**
     * Returns whether a specific parameter of a trait has validation dependency on another parameter
     * eg. for numeric trait, decimalPlaces restriction and mathSymbols cannot both be enabled
     * since mathSymbols is like a boolean switch, it will automatically reset decimalPlaces to default
     * but we need to handle decimalPlaces
     */
    private fun paramHasValidationDependency(
        formatDefinition: TraitFormat,
        parameter: BaseFormatParameter,
    ): Boolean {
        return when (formatDefinition) {
            is NumericFormat -> {
                parameter is DecimalPlacesParameter
            }

            else -> false
        }
    }

    /**
     * Validates inter-parameter dependencies and returns error message if validation fails
     */
    private fun validateInterParameterValidation(
        formatDefinition: TraitFormat,
        trait: TraitObject,
    ): String? {
        return when (formatDefinition) {
            is NumericFormat -> validateNumericInterParameters(trait)
            else -> null
        }
    }

    /**
     * Validates numeric trait inter-parameter dependencies (decimal places vs math symbols)
     */
    private fun validateNumericInterParameters(trait: TraitObject): String? {
        val mathSymbolsEnabled = trait.mathSymbolsEnabled
        val decimalPlaces = trait.maxDecimalPlaces.toIntOrNull() ?: -1

        if (mathSymbolsEnabled && decimalPlaces >= 0) {
            return getString(R.string.traits_create_warning_math_symbols_conflict)
        }

        return null
    }
}