package com.fieldbook.shared.screens.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.database.utils.DATABASE_NAME
import com.fieldbook.shared.database.utils.importDatabaseFromBundled
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.app_intro_intro_summary_slide1
import com.fieldbook.shared.generated.resources.app_intro_intro_summary_slide2
import com.fieldbook.shared.generated.resources.app_intro_intro_title_slide1
import com.fieldbook.shared.generated.resources.app_intro_intro_title_slide2
import com.fieldbook.shared.generated.resources.app_intro_load_sample_data_summary
import com.fieldbook.shared.generated.resources.app_intro_load_sample_data_title
import com.fieldbook.shared.generated.resources.app_intro_permissions_title
import com.fieldbook.shared.generated.resources.app_intro_required_optional_summary
import com.fieldbook.shared.generated.resources.app_intro_required_optional_title
import com.fieldbook.shared.generated.resources.app_intro_required_setup_summary
import com.fieldbook.shared.generated.resources.app_intro_required_setup_title
import com.fieldbook.shared.generated.resources.app_intro_storage_title
import com.fieldbook.shared.generated.resources.field_book_intro_brapi
import com.fieldbook.shared.generated.resources.field_book_mini_percent
import com.fieldbook.shared.generated.resources.other_ic_field_book
import com.fieldbook.shared.preferences.GeneralKeys
import com.fieldbook.shared.utilities.configurePickedStorageDirectory
import com.fieldbook.shared.utilities.isStorageDirectoryConfigured
import com.fieldbook.shared.utilities.selectFirstField
import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.compose.rememberDirectoryPickerLauncher
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun OnboardingScreen(
    hasPermissions: Boolean,
    onRequestPermissions: suspend () -> Boolean,
    onComplete: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val settings = remember { Settings() }

    var permissionsGranted by remember { mutableStateOf(hasPermissions) }
    var storageConfigured by remember { mutableStateOf(isStorageDirectoryConfigured()) }
    var importInProgress by remember { mutableStateOf(false) }

    LaunchedEffect(hasPermissions) {
        permissionsGranted = hasPermissions
    }

    val directoryLauncher = rememberDirectoryPickerLauncher(
        title = stringResource(Res.string.app_intro_storage_title)
    ) { directory ->
        val configuredDirectory = directory?.let(::configurePickedStorageDirectory)
        if (configuredDirectory != null) {
            settings.putString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY.key, configuredDirectory)
            storageConfigured = true
        } else if (directory != null) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Failed to configure the selected folder.")
            }
        }
    }

    val hasRequiredSetup = permissionsGranted && storageConfigured

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = false
            ) { page ->
                when (page) {
                    0 -> IntroPage(
                        title = stringResource(Res.string.app_intro_intro_title_slide1),
                        summary = stringResource(Res.string.app_intro_intro_summary_slide1),
                        hero = Res.drawable.other_ic_field_book,
                    )

                    1 -> IntroPage(
                        title = stringResource(Res.string.app_intro_intro_title_slide2),
                        summary = stringResource(Res.string.app_intro_intro_summary_slide2),
                        hero = Res.drawable.field_book_intro_brapi,
                        secondaryHero = Res.drawable.field_book_mini_percent,
                    )

                    2 -> RequiredSetupPage(
                        permissionsGranted = permissionsGranted,
                        storageConfigured = storageConfigured,
                        onRequestPermissions = {
                            coroutineScope.launch {
                                permissionsGranted = onRequestPermissions()
                                if (!permissionsGranted) {
                                    snackbarHostState.showSnackbar("Please grant the required permissions.")
                                }
                            }
                        },
                        onChooseDirectory = { directoryLauncher.launch() }
                    )

                    else -> OptionalSetupPage(
                        importInProgress = importInProgress,
                        onImportSamples = {
                            coroutineScope.launch {
                                importInProgress = true
                                val result = runCatching {
                                    importDatabaseFromBundled(DATABASE_NAME)
                                    selectFirstField()
                                }
                                importInProgress = false

                                snackbarHostState.showSnackbar(
                                    result.fold(
                                        onSuccess = { "Sample database imported successfully." },
                                        onFailure = { "Failed to import sample database." }
                                    )
                                )
                            }
                        }
                    )
                }
            }

            PagerFooter(
                currentPage = pagerState.currentPage,
                canContinue = pagerState.currentPage != 2 || hasRequiredSetup,
                onBack = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                    }
                },
                onNext = {
                    coroutineScope.launch {
                        if (pagerState.currentPage == 3) {
                            settings.putBoolean(GeneralKeys.FIRST_RUN_KMP.key, false)
                            onComplete()
                        } else if (pagerState.currentPage != 2 || hasRequiredSetup) {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        } else {
                            snackbarHostState.showSnackbar("Complete permissions and storage setup first.")
                        }
                    }
                }
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun IntroPage(
    title: String,
    summary: String,
    hero: DrawableResource,
    secondaryHero: DrawableResource? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(24.dp)
        ) {
            Image(
                painter = painterResource(hero),
                contentDescription = null,
                modifier = Modifier.size(140.dp),
                contentScale = ContentScale.Fit
            )
        }

        secondaryHero?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Image(
                painter = painterResource(it),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                contentScale = ContentScale.Fit
            )
        }

        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RequiredSetupPage(
    permissionsGranted: Boolean,
    storageConfigured: Boolean,
    onRequestPermissions: () -> Unit,
    onChooseDirectory: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(Res.string.app_intro_required_setup_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.app_intro_required_setup_summary),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(28.dp))

        SetupActionCard(
            title = stringResource(Res.string.app_intro_permissions_title),
            summary = if (permissionsGranted) "Granted" else "Camera, audio, and location access",
            done = permissionsGranted,
            actionLabel = "Grant Permissions",
            onAction = onRequestPermissions,
            enabled = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        SetupActionCard(
            title = stringResource(Res.string.app_intro_storage_title),
            summary = if (storageConfigured) "Folder selected and initialized" else "Choose a folder for Field Book data",
            done = storageConfigured,
            actionLabel = "Choose Folder",
            onAction = onChooseDirectory,
            enabled = true
        )
    }
}

@Composable
private fun OptionalSetupPage(
    importInProgress: Boolean,
    onImportSamples: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(Res.string.app_intro_required_optional_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.app_intro_required_optional_summary),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(28.dp))

        SetupActionCard(
            title = stringResource(Res.string.app_intro_load_sample_data_title),
            summary = stringResource(Res.string.app_intro_load_sample_data_summary),
            done = false,
            actionLabel = if (importInProgress) "Importing..." else "Load Samples",
            onAction = onImportSamples,
            enabled = !importInProgress,
            trailing = {
                if (importInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
        )
    }
}

@Composable
private fun SetupActionCard(
    title: String,
    summary: String,
    done: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    enabled: Boolean,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (done) {
                Text(
                    text = "Ready",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onAction,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors()
            ) {
                Text(actionLabel)
            }
            trailing?.let {
                Spacer(modifier = Modifier.size(12.dp))
                it()
            }
        }
    }
}

@Composable
private fun PagerFooter(
    currentPage: Int,
    canContinue: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = if (index == currentPage) 24.dp else 10.dp, height = 10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (index == currentPage) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                onClick = onBack,
                enabled = currentPage > 0
            ) {
                Text("Back")
            }
            Button(
                onClick = onNext,
                enabled = canContinue || currentPage == 3
            ) {
                Text(if (currentPage == 3) "Done" else "Next")
            }
        }
    }
}
