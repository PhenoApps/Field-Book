package com.fieldbook.shared

import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fieldbook.shared.screens.AboutScreen
import com.fieldbook.shared.screens.ConfigScreen
import com.fieldbook.shared.screens.ScannerScreen
import com.fieldbook.shared.screens.brapi.BrapiFilterScreen
import com.fieldbook.shared.screens.brapi.BrapiImportSharedViewModel
import com.fieldbook.shared.screens.brapi.brapiImportSharedViewModelFactory
import com.fieldbook.shared.screens.brapi.field.BrapiFieldImportScreen
import com.fieldbook.shared.screens.brapi.field.BrapiFieldImportViewModel
import com.fieldbook.shared.screens.brapi.field.BrapiStudyPreviewScreen
import com.fieldbook.shared.screens.brapi.field.brapiFieldImportViewModelFactory
import com.fieldbook.shared.screens.brapi.trait.BrapiTraitImportScreen
import com.fieldbook.shared.screens.brapi.trait.BrapiTraitImportViewModel
import com.fieldbook.shared.screens.brapi.trait.brapiTraitImportViewModelFactory
import com.fieldbook.shared.screens.collect.CollectScreen
import com.fieldbook.shared.screens.export.ExportScreen
import com.fieldbook.shared.screens.fields.FieldEditorScreen
import com.fieldbook.shared.screens.fields.FieldEditorScreenViewModel
import com.fieldbook.shared.screens.fields.fieldEditorViewModelFactory
import com.fieldbook.shared.screens.preferences.AppearancePreferencesScreen
import com.fieldbook.shared.screens.preferences.BrapiPreferencesScreen
import com.fieldbook.shared.screens.preferences.FeaturePreferenceScreen
import com.fieldbook.shared.screens.preferences.LanguageScreen
import com.fieldbook.shared.screens.preferences.PreferencesScreen
import com.fieldbook.shared.screens.preferences.StorageDefinerScreen
import com.fieldbook.shared.screens.preferences.StoragePreferencesScreen
import com.fieldbook.shared.screens.trait.TraitEditorScreen
import com.fieldbook.shared.screens.trait.TraitEditorScreenViewModel
import com.fieldbook.shared.screens.trait.traitEditorScreenViewModelFactory
import kotlinx.coroutines.launch

@Composable
fun KmpApp(
    startScreen: KmpHostScreenType = KmpHostScreenType.CONFIG,
    onExit: () -> Unit = {},
    onStorageResetExit: () -> Unit = onExit,
    onScannerResult: (String) -> Unit = {},
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val onSnackbarMessage: (String) -> Unit = remember(snackbarHostState, snackbarScope) {
        { message ->
            snackbarScope.launch {
                snackbarHostState.showSnackbar(message)
            }
            Unit
        }
    }
    val graphStartScreen = if (startScreen == KmpHostScreenType.SCANNER) {
        KmpHostScreenType.SCANNER
    } else {
        KmpHostScreenType.CONFIG
    }
    val traitEditorViewModel: TraitEditorScreenViewModel = viewModel(
        factory = traitEditorScreenViewModelFactory()
    )
    val fieldEditorViewModel: FieldEditorScreenViewModel = viewModel(
        factory = fieldEditorViewModelFactory()
    )
    val brapiImportSharedViewModel: BrapiImportSharedViewModel = viewModel(
        factory = brapiImportSharedViewModelFactory()
    )
    val brapiTraitImportViewModel: BrapiTraitImportViewModel = viewModel(
        factory = brapiTraitImportViewModelFactory()
    )
    val brapiFieldImportViewModel: BrapiFieldImportViewModel = viewModel(
        factory = brapiFieldImportViewModelFactory()
    )

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { _ ->
        NavHost(
            navController = navController,
            startDestination = graphStartScreen.route,
        ) {
            composable(KmpHostScreenType.CONFIG.route) {
                ConfigScreen(
                    onBack = onExit,
                    onNavigate = navController::navigateTo,
                )
            }

            composable(KmpHostScreenType.SCANNER.route) {
                ScannerScreen(
                    onBack = { navController.navigateBackOrExit(onExit) },
                    onResult = onScannerResult,
                )
            }

            composable(KmpHostScreenType.FIELD_EDITOR.route) {
                FieldEditorScreen(
                    onBack = { navController.navigateBackOrExit(onExit) },
                    onNavigateToBrapi = { navController.navigateTo(KmpHostScreenType.FIELD_BRAPI) },
                    viewModel = fieldEditorViewModel,
                    onSnackbarMessage = onSnackbarMessage,
                )
            }

            composable(KmpHostScreenType.FIELD_BRAPI.route) {
                BrapiFieldImportScreen(
                    onBack = { navController.navigateBackOrExit(onExit) },
                    onNavigateToFilter = { navController.navigateTo(KmpHostScreenType.BRAPI_FILTER) },
                    onStudySelected = { navController.navigateTo(KmpHostScreenType.FIELD_BRAPI_PREVIEW) },
                    sharedViewModel = brapiImportSharedViewModel,
                    viewModel = brapiFieldImportViewModel,
                    onSnackbarMessage = onSnackbarMessage,
                )
            }

            composable(KmpHostScreenType.FIELD_BRAPI_PREVIEW.route) {
                BrapiStudyPreviewScreen(
                    importViewModel = brapiFieldImportViewModel,
                    onBack = { navController.navigateBackOrExit(onExit) },
                    onMissingStudy = { navController.navigateBackOrExit(onExit) },
                    onImportComplete = {
                        fieldEditorViewModel.loadFields()
                        navController.navigateTo(
                            screen = KmpHostScreenType.FIELD_EDITOR,
                            popUpToScreen = KmpHostScreenType.FIELD_EDITOR,
                        )
                    },
                    onSnackbarMessage = onSnackbarMessage,
                )
            }

            composable(KmpHostScreenType.TRAIT_EDITOR.route) {
                TraitEditorScreen(
                    onBack = { navController.navigateBackOrExit(onExit) },
                    onNavigateToBrapi = { navController.navigateTo(KmpHostScreenType.TRAIT_BRAPI) },
                    viewModel = traitEditorViewModel,
                    onSnackbarMessage = onSnackbarMessage,
                )
            }

            composable(KmpHostScreenType.TRAIT_BRAPI.route) {
                BrapiTraitImportScreen(
                    onBack = { navController.navigateBackOrExit(onExit) },
                    onNavigateToFilter = { navController.navigateTo(KmpHostScreenType.BRAPI_FILTER) },
                    onImportComplete = {
                        traitEditorViewModel.loadTraits()
                        navController.navigateBackOrExit(onExit)
                    },
                    sharedViewModel = brapiImportSharedViewModel,
                    viewModel = brapiTraitImportViewModel,
                    onSnackbarMessage = onSnackbarMessage,
                )
            }

            composable(KmpHostScreenType.BRAPI_FILTER.route) {
                val filterState by brapiImportSharedViewModel.filterState.collectAsState()

                BrapiFilterScreen(
                    state = filterState,
                    onBack = { navController.navigateBackOrExit(onExit) },
                    onApply = { selectedIds ->
                        brapiImportSharedViewModel.applyActiveFilterSelection(selectedIds)
                        navController.navigateBackOrExit(onExit)
                    },
                )
            }

            composable(KmpHostScreenType.COLLECT.route) {
                CollectScreen(
                    onBack = { navController.navigateBackOrExit(onExit) },
                )
            }

            composable(KmpHostScreenType.PREFERENCES.route) {
                PreferencesScreen(
                    onBack = { navController.navigateBackOrExit(onExit) },
                    onNavigate = navController::navigateTo,
                )
            }

            composable(KmpHostScreenType.BRAPI_PREFERENCES.route) {
                BrapiPreferencesScreen(
                    onBack = { navController.navigateBackOrExit(onExit) },
                )
            }

            composable(KmpHostScreenType.FEATURE_PREFERENCES.route) {
                FeaturePreferenceScreen(
                    onBack = { navController.navigateBackOrExit(onExit) },
                )
            }

            composable(KmpHostScreenType.APPEARANCE_PREFERENCES.route) {
                AppearancePreferencesScreen(
                    onBack = { navController.navigateBackOrExit(onExit) },
                    onNavigate = navController::navigateTo,
                )
            }

            composable(KmpHostScreenType.LANGUAGE_PREFERENCES.route) {
                LanguageScreen(
                    onBack = { navController.navigateBackOrExit(onExit) },
                )
            }

            composable(KmpHostScreenType.STORAGE_PREFERENCES.route) {
                StoragePreferencesScreen(
                    onBack = { navController.navigateBackOrExit(onExit) },
                    onNavigate = navController::navigateTo,
                    onExit = onStorageResetExit,
                    onSnackbarMessage = onSnackbarMessage,
                )
            }

            composable(KmpHostScreenType.STORAGE_DEFINER.route) {
                StorageDefinerScreen(
                    onBack = { navController.navigateBackOrExit(onExit) },
                )
            }

            composable(KmpHostScreenType.EXPORT.route) {
                ExportScreen(
                    fieldIds = emptyList(),
                    onBack = { navController.navigateBackOrExit(onExit) },
                )
            }

            composable(KmpHostScreenType.ABOUT.route) {
                AboutScreen(
                    onBack = { navController.navigateBackOrExit(onExit) },
                )
            }
        }
    }

    LaunchedEffect(startScreen) {
        if (startScreen != KmpHostScreenType.CONFIG && startScreen != graphStartScreen) {
            navController.navigateTo(startScreen)
        }
    }
}

private val KmpHostScreenType.route: String
    get() = value

private fun NavHostController.navigateTo(
    screen: KmpHostScreenType,
    popUpToScreen: KmpHostScreenType? = null,
) {
    navigate(screen.route) {
        popUpToScreen?.let { popUpTo(it.route) }
        launchSingleTop = true
    }
}

private fun NavHostController.navigateBackOrExit(onExit: () -> Unit) {
    if (!popBackStack()) {
        onExit()
    }
}
