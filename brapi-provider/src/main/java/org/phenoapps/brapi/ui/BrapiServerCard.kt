package org.phenoapps.brapi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import org.phenoapps.brapi.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrapiServerCard(
    displayName: String,
    serverUrl: String,
    isActive: Boolean,
    hasToken: Boolean,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onEnable: () -> Unit,
    onAuthorize: (() -> Unit)?,
    onLogOut: (() -> Unit)?,
    onCheckCompatibility: (() -> Unit)?,
    onShareSettings: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    onRequestSwitchServer: () -> Unit,
    modifier: Modifier = Modifier,
    ownerLabel: String? = null,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "brapi_chevron_rotation",
    )

    val statusIcon = if (hasToken) R.drawable.pheno_brapi_ic_lock else R.drawable.pheno_brapi_ic_unlock
    val statusTint = when {
        isActive && hasToken -> Color(0xFF4CAF50)
        isActive -> Color(0xFFF44336)
        else -> Color(0xFF787878)
    }
    val chipStroke = if (isActive) MaterialTheme.colorScheme.primary else Color(0xFF787878)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.pheno_brapi_logo),
                    contentDescription = null,
                    modifier = Modifier.height(36.dp),
                    contentScale = ContentScale.Fit,
                    colorFilter = if (isActive) null
                    else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                )

                Spacer(modifier = Modifier.width(8.dp))

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
                    if (ownerLabel != null) {
                        Text(
                            text = ownerLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Icon(
                    painter = painterResource(statusIcon),
                    contentDescription = null,
                    tint = statusTint,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(24.dp),
                )

                Icon(
                    painter = painterResource(R.drawable.pheno_brapi_ic_chevron_down),
                    contentDescription = null,
                    modifier = Modifier
                        .size(30.dp)
                        .rotate(chevronRotation),
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (!isActive) {
                        BrapiChip(
                            text = stringResource(R.string.pheno_brapi_switch_server_title),
                            icon = R.drawable.pheno_brapi_ic_login,
                            strokeColor = chipStroke,
                            onClick = { if (hasToken) onEnable() else onRequestSwitchServer() },
                        )
                    }
                    if (onCheckCompatibility != null) {
                        BrapiChip(
                            text = stringResource(R.string.pheno_brapi_chip_compatibility),
                            icon = R.drawable.pheno_brapi_ic_compare,
                            strokeColor = chipStroke,
                            onClick = onCheckCompatibility,
                        )
                    }
                    if (onShareSettings != null) {
                        BrapiChip(
                            text = stringResource(R.string.pheno_brapi_chip_share),
                            icon = R.drawable.pheno_brapi_ic_share,
                            strokeColor = chipStroke,
                            onClick = onShareSettings,
                        )
                    }
                    if (onEdit != null) {
                        BrapiChip(
                            text = stringResource(R.string.pheno_brapi_chip_edit),
                            icon = R.drawable.pheno_brapi_ic_edit,
                            strokeColor = chipStroke,
                            onClick = onEdit,
                        )
                    }
                    if (hasToken && onLogOut != null) {
                        BrapiChip(
                            text = stringResource(R.string.pheno_brapi_chip_logout),
                            icon = R.drawable.pheno_brapi_ic_logout,
                            strokeColor = chipStroke,
                            onClick = onLogOut,
                        )
                    } else {
                        if (onAuthorize != null) {
                            BrapiChip(
                                text = stringResource(R.string.pheno_brapi_chip_authorize),
                                icon = R.drawable.pheno_brapi_ic_key,
                                strokeColor = chipStroke,
                                onClick = onAuthorize,
                            )
                        }
                        if (onRemove != null) {
                            BrapiChip(
                                text = stringResource(R.string.pheno_brapi_chip_remove),
                                icon = R.drawable.pheno_brapi_ic_logout,
                                strokeColor = chipStroke,
                                onClick = onRemove,
                            )
                        }
                    }
                }
            }
        }
    }
}
