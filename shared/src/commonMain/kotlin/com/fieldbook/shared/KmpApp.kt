package com.fieldbook.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fieldbook.shared.screens.AboutScreen
import com.fieldbook.shared.screens.ConfigScreen
import com.fieldbook.shared.screens.ScannerScreen
import com.fieldbook.shared.screens.brapi.BrapiStudyScreen
import com.fieldbook.shared.screens.brapi.BrapiTraitFilterScreen
import com.fieldbook.shared.screens.collect.CollectScreen
import com.fieldbook.shared.screens.export.ExportScreen
import com.fieldbook.shared.screens.fields.FieldEditorScreen
import com.fieldbook.shared.screens.preferences.AppearancePreferencesScreen
import com.fieldbook.shared.screens.preferences.BrapiPreferencesScreen
import com.fieldbook.shared.screens.preferences.FeaturePreferenceScreen
import com.fieldbook.shared.screens.preferences.LanguageScreen
import com.fieldbook.shared.screens.preferences.PreferencesScreen
import com.fieldbook.shared.screens.preferences.StorageDefinerScreen
import com.fieldbook.shared.screens.preferences.StoragePreferencesScreen
import com.fieldbook.shared.screens.trait.TraitBrapiScreen
import com.fieldbook.shared.screens.trait.TraitEditorScreen
import com.fieldbook.shared.screens.trait.TraitEditorScreenViewModel
import com.fieldbook.shared.screens.trait.traitEditorScreenViewModelFactory

@Composable
fun KmpApp(
    startScreen: KmpHostScreenType = KmpHostScreenType.CONFIG,
    onExit: () -> Unit = {},
    onStorageResetExit: () -> Unit = onExit,
    onScannerResult: (String) -> Unit = {},
) {
    val navController = rememberNavController()
    val graphStartScreen = if (startScreen == KmpHostScreenType.SCANNER) {
        KmpHostScreenType.SCANNER
    } else {
        KmpHostScreenType.CONFIG
    }
    val traitEditorViewModel: TraitEditorScreenViewModel = viewModel(
        factory = traitEditorScreenViewModelFactory()
    )

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
            )
        }

        composable(KmpHostScreenType.TRAIT_EDITOR.route) {
            TraitEditorScreen(
                onBack = { navController.navigateBackOrExit(onExit) },
                onNavigateToBrapi = { navController.navigateTo(KmpHostScreenType.TRAIT_BRAPI) },
                viewModel = traitEditorViewModel,
            )
        }

        composable(KmpHostScreenType.TRAIT_BRAPI.route) {
            TraitBrapiScreen(
                onBack = { navController.navigateBackOrExit(onExit) },
                onNavigateToFilter = { navController.navigateTo(KmpHostScreenType.BRAPI_FILTER) },
                viewModel = traitEditorViewModel,
            )
        }

        composable(KmpHostScreenType.BRAPI_FILTER.route) {
            BrapiTraitFilterScreen(
                onBack = { navController.navigateBackOrExit(onExit) },
                viewModel = traitEditorViewModel,
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

        composable(KmpHostScreenType.BRAPI_STUDIES.route) {
            BrapiStudyScreen(
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

    LaunchedEffect(startScreen) {
        if (startScreen != KmpHostScreenType.CONFIG && startScreen != graphStartScreen) {
            navController.navigateTo(startScreen)
        }
    }
}

private val KmpHostScreenType.route: String
    get() = value

private fun NavHostController.navigateTo(screen: KmpHostScreenType) {
    navigate(screen.route) {
        launchSingleTop = true
    }
}

private fun NavHostController.navigateBackOrExit(onExit: () -> Unit) {
    if (!popBackStack()) {
        onExit()
    }
}
