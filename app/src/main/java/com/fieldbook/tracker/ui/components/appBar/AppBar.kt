package com.fieldbook.tracker.ui.components.appBar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.components.widgets.AppIcon
import com.fieldbook.tracker.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBar(
    title: String,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: List<TopAppBarAction> = emptyList(),
    maxVisibleActions: Int = 3, // includes overflow as an action. applies to actions that are set to IF_ROOM display mode
) {
    var showOverflowMenu by remember { mutableStateOf(false) }

    // separate actions based on display mode
    val alwaysVisible = actions.filter { it.displayMode == ActionDisplayMode.ALWAYS }
    val ifRoomActions = actions.filter { it.displayMode == ActionDisplayMode.IF_ROOM }
    val neverVisible = actions.filter { it.displayMode == ActionDisplayMode.NEVER }

    // calculate which actions to show in toolbar vs overflow
    val availableSpots = maxVisibleActions - alwaysVisible.size
    val visibleIfRoomActions =
        ifRoomActions.take(maxOf(0, availableSpots - 1)) // -1 for overflow button
    val overflowActions = ifRoomActions.drop(visibleIfRoomActions.size) + neverVisible

    TopAppBar(
        title = {
            Text(
                text = title,
                color = AppTheme.colors.text.tertiary,
                fontWeight = FontWeight.Medium
            )
        },
        colors = AppTheme.colors.topAppBarColors,
        navigationIcon = navigationIcon ?: { },
        actions = {
            alwaysVisible.forEach { action ->
                ActionIconButton(action = action)
            }

            // IF_ROOM actions that fit
            visibleIfRoomActions.forEach { action ->
                ActionIconButton(action = action)
            }

            if (overflowActions.isNotEmpty()) {
                IconButton(onClick = { showOverflowMenu = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.appbar_more_options),
                    )
                }

                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false },
                    containerColor = AppTheme.colors.background
                ) {
                    overflowActions.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.title) },
                            onClick = {
                                action.onClick()
                                showOverflowMenu = false
                            },
                            leadingIcon = if (action.icon != null) {
                                {
                                    AppIcon(
                                        icon = action.icon,
                                        contentDescription = action.contentDescription
                                    )
                                }
                            } else null
                        )
                    }
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun AppBarPreview() {
    AppTheme {
        AppBar(
            title = "Title",
            navigationIcon = {
                IconButton(onClick = { }) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_left),
                        contentDescription = "Navigate back"
                    )
                }
            },
            actions = listOf(
                TopAppBarAction(
                    title = "Search",
                    contentDescription = "Search",
                    icon = Icons.Filled.Search,
                    displayMode = ActionDisplayMode.ALWAYS,
                    onClick = { }
                ),
                TopAppBarAction(
                    title = "Favorite",
                    contentDescription = "Add to favorites",
                    icon = Icons.Filled.Favorite,
                    displayMode = ActionDisplayMode.IF_ROOM,
                    onClick = { }
                ),
                TopAppBarAction(
                    title = "Share",
                    contentDescription = "Share",
                    icon = Icons.Filled.Share,
                    displayMode = ActionDisplayMode.IF_ROOM,
                    onClick = { }
                ),
                TopAppBarAction(
                    title = "Settings",
                    contentDescription = "Settings",
                    icon = Icons.Filled.Settings,
                    displayMode = ActionDisplayMode.NEVER,
                    onClick = { }
                ),
                TopAppBarAction(
                    title = "Help",
                    contentDescription = "Help",
                    icon = Icons.AutoMirrored.Filled.Help,
                    displayMode = ActionDisplayMode.NEVER,
                    onClick = { }
                )
            )
        )
    }
}