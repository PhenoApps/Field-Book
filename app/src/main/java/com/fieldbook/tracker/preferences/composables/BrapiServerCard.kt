package com.fieldbook.tracker.preferences.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.components.widgets.Chip
import com.fieldbook.tracker.ui.theme.AppTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown

@Composable
fun BrapiServerCard(
    displayName: String,
    serverUrl: String,
    isActive: Boolean,
    hasToken: Boolean,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onEnable: () -> Unit,
    onAuthorize: () -> Unit,
    onLogOut: () -> Unit,
    onCheckCompatibility: () -> Unit,
    onShareSettings: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onRequestSwitchServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "chevron_rotation",
    )

    // Status icon resource and tint
    val statusIconRes: Int
    val statusIconTint: Color
    when {
        isActive && hasToken -> {
            statusIconRes = R.drawable.ic_tb_lock
            statusIconTint = Color(0xFF4CAF50)
        }
        isActive && !hasToken -> {
            statusIconRes = R.drawable.ic_tb_unlock
            statusIconTint = Color(0xFFF44336)
        }
        hasToken -> {
            statusIconRes = R.drawable.ic_tb_lock
            statusIconTint = Color(0xFF787878)
        }
        else -> {
            statusIconRes = R.drawable.ic_tb_unlock
            statusIconTint = Color(0xFF787878)
        }
    }

    val inactiveStroke = Color(0xFF787878)
    val chipStroke = if (isActive) AppTheme.colors.chip.selectableStroke else inactiveStroke

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.background),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // BrAPI logo
                Image(
                    painter = painterResource(R.drawable.brapi_logo),
                    contentDescription = null,
                    modifier = Modifier.height(36.dp),
                    contentScale = ContentScale.Fit,
                    colorFilter = if (isActive) null
                    else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Display name + URL
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = serverUrl,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Status icon
                Icon(
                    painter = painterResource(statusIconRes),
                    contentDescription = null,
                    tint = statusIconTint,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(24.dp),
                )

                // Chevron
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier
                        .size(30.dp)
                        .rotate(chevronRotation),
                )
            }

            // Action chips
            AnimatedVisibility(visible = isExpanded) {
                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (!isActive) {
                        Chip(
                            text = stringResource(R.string.brapi_switch_server_title),
                            icon = R.drawable.ic_pref_brapi_login,
                            strokeColor = chipStroke,
                            onClick = { if (hasToken) onEnable() else onRequestSwitchServer() },
                        )
                    }
                    Chip(
                        text = stringResource(R.string.brapi_chip_compatibility),
                        icon = R.drawable.ic_server_compare,
                        strokeColor = chipStroke,
                        onClick = onCheckCompatibility,
                    )
                    Chip(
                        text = stringResource(R.string.brapi_chip_share),
                        icon = R.drawable.ic_share,
                        strokeColor = chipStroke,
                        onClick = onShareSettings,
                    )
                    Chip(
                        text = stringResource(R.string.brapi_chip_edit),
                        icon = R.drawable.square_edit_outline,
                        strokeColor = chipStroke,
                        onClick = onEdit,
                    )
                    if (hasToken) {
                        Chip(
                            text = stringResource(R.string.brapi_chip_logout),
                            icon = R.drawable.ic_pref_brapi_logout,
                            strokeColor = chipStroke,
                            onClick = onLogOut,
                        )
                    } else {
                        Chip(
                            text = stringResource(R.string.brapi_chip_authorize),
                            icon = R.drawable.key,
                            strokeColor = chipStroke,
                            onClick = onAuthorize,
                        )
                        Chip(
                            text = stringResource(R.string.brapi_chip_remove),
                            icon = R.drawable.ic_pref_brapi_logout,
                            strokeColor = chipStroke,
                            onClick = onRemove,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BrapiServerCardPreview() {
    AppTheme {
        BrapiServerCard(
            displayName = "Test BrAPI Server",
            serverUrl = "https://test.brapi.org/brapi/v2",
            isActive = true,
            hasToken = true,
            isExpanded = true,
            onToggleExpanded = {},
            onEnable = {},
            onAuthorize = {},
            onLogOut = {},
            onCheckCompatibility = {},
            onShareSettings = {},
            onEdit = {},
            onRemove = {},
            onRequestSwitchServer = {},
        )
    }
}
