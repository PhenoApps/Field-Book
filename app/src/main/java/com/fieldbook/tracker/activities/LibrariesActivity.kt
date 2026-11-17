package com.fieldbook.tracker.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.components.appBar.AppBar
import com.fieldbook.tracker.ui.theme.AppTheme
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

/**
 * Displays the open source libraries used by the app.
 *
 * The library list is generated at build time by the aboutlibraries gradle plugin into
 * res/raw/aboutlibraries.json, it is not detected at runtime.
 */
class LibrariesActivity : ThemedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                LibrariesScreen(onBack = { finish() })
            }
        }
    }

    @Composable
    private fun LibrariesScreen(onBack: () -> Unit) {

        val libraries by produceLibraries(R.raw.aboutlibraries)

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(R.string.about_libraries_title),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_left),
                                contentDescription = stringResource(R.string.appbar_back)
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LibrariesContainer(
                    libraries = libraries,
                    modifier = Modifier.fillMaxSize(),
                    licenseDialogConfirmText = stringResource(R.string.dialog_ok)
                )
            }
        }
    }
}
