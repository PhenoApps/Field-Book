package com.fieldbook.tracker.ui.screens.traits.toolbars

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.viewmodels.DialogTriggerSource
import com.fieldbook.tracker.database.viewmodels.TraitActivityDialog
import com.fieldbook.tracker.ui.components.appBar.ActionDisplayMode
import com.fieldbook.tracker.ui.components.appBar.AppBar
import com.fieldbook.tracker.ui.components.appBar.TopAppBarAction
import com.fieldbook.tracker.ui.theme.AppTheme

@Composable
fun TraitEditorToolbar(
    hasTraits: Boolean,
    isTutorialEnabled: Boolean,
    onBack: () -> Unit,
    onToggleAllTraits: () -> Unit,
    onShowDialog: (TraitActivityDialog) -> Unit,
    onRequestExportPermission: () -> Unit
) {


    val appBarActions = buildList {
        // if (isTutorialEnabled) {
        //     add(
        //         TopAppBarAction(
        //             title = stringResource(R.string.tutorial_dialog_title),
        //             contentDescription = "Help",
        //             icon = Icons.AutoMirrored.Filled.Help,
        //             displayMode = ActionDisplayMode.ALWAYS,
        //             onClick = {
        //                 // TODO add tutorial
        //             }
        //         )
        //     )
        // }

        if (hasTraits) {
            addAll(
                listOf(
                    TopAppBarAction(
                        title = stringResource(R.string.traits_sort_visibility),
                        contentDescription = stringResource(R.string.traits_sort_visibility),
                        icon = R.drawable.ic_tb_toggle_all,
                        displayMode = ActionDisplayMode.ALWAYS,
                        onClick = onToggleAllTraits
                    ),
                    TopAppBarAction(
                        title = stringResource(R.string.traits_toolbar_sort),
                        contentDescription = stringResource(R.string.traits_toolbar_sort),
                        icon = R.drawable.ic_sort,
                        displayMode = ActionDisplayMode.ALWAYS,
                        onClick = {
                            onShowDialog(TraitActivityDialog.SortTraits)
                        }
                    ),
                    TopAppBarAction(
                        title = stringResource(R.string.traits_toolbar_delete_all),
                        contentDescription = stringResource(R.string.traits_toolbar_delete_all),
                        icon = Icons.Filled.Delete,
                        displayMode = ActionDisplayMode.IF_ROOM,
                        onClick = {
                            onShowDialog(TraitActivityDialog.DeleteAll(DialogTriggerSource.TOOLBAR))
                        }
                    ),
                    TopAppBarAction(
                        title = stringResource(R.string.traits_dialog_export),
                        contentDescription = stringResource(R.string.traits_dialog_export),
                        icon = Icons.Filled.FileDownload,
                        displayMode = ActionDisplayMode.IF_ROOM,
                        onClick = onRequestExportPermission
                    ),
                )
            )
        }
    }


    AppBar(
        title = stringResource(R.string.settings_traits),
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
private fun TraitEditorToolbarPreview() {
    AppTheme {
        TraitEditorToolbar(
            hasTraits = true,
            isTutorialEnabled = true,
            onBack = { },
            onToggleAllTraits = { },
            onShowDialog = { },
            onRequestExportPermission = { },
        )
    }
}