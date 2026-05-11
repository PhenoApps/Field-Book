package com.fieldbook.tracker.ui.screens.traits.toolbars

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.ui.components.appBar.ActionDisplayMode
import com.fieldbook.tracker.ui.components.appBar.AppBar
import com.fieldbook.tracker.ui.components.appBar.TopAppBarAction
import com.fieldbook.tracker.ui.theme.AppTheme

@Composable
fun TraitDetailToolbar(
    trait: TraitObject?,
    onBack: () -> Unit,
    onCopyTrait: () -> Unit,
    onConfigureTrait: () -> Unit,
    onDeleteTrait: () -> Unit
) {
    val appBarActions = buildList {

        if (trait != null) {
            // copy trait
            add(
                TopAppBarAction(
                    title = stringResource(R.string.traits_options_copy_title),
                    contentDescription = stringResource(R.string.traits_options_copy_title),
                    icon = Icons.Default.ContentCopy,
                    displayMode = ActionDisplayMode.ALWAYS,
                    onClick = onCopyTrait
                )
            )

            // configure trait
            add(
                TopAppBarAction(
                    title = stringResource(R.string.trait_detail_title),
                    contentDescription = stringResource(R.string.trait_detail_title),
                    icon = R.drawable.ic_configure,
                    displayMode = ActionDisplayMode.ALWAYS,
                    onClick = onConfigureTrait
                )
            )

            // delete
            add(
                TopAppBarAction(
                    title = stringResource(R.string.traits_options_delete_title),
                    contentDescription = stringResource(R.string.traits_options_delete_title),
                    icon = Icons.Default.Delete,
                    displayMode = ActionDisplayMode.ALWAYS,
                    onClick = onDeleteTrait
                )
            )
        }
    }


    AppBar(
        title = stringResource(R.string.trait_detail_title),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.arrow_left),
                    contentDescription = stringResource(R.string.appbar_back)
                )
            }
        },
        actions = appBarActions
    )
}

@Preview
@Composable
private fun TraitDetailToolbarPreview() {
    AppTheme {
        TraitDetailToolbar(
            trait = TraitObject(),
            onBack = { },
            onCopyTrait = { },
            onConfigureTrait = { },
            onDeleteTrait = { },
        )
    }
}