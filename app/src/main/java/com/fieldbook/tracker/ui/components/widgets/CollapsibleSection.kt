package com.fieldbook.tracker.ui.components.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.components.graphs.PieChart
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.ui.utils.noRippleClickable

/**
 * Header consists of a Row where
 * [leadingIcon], [title] and [headerContent], and [trailingContent]
 * are placed.
 */
@Composable
fun CollapsibleSection(
    modifier: Modifier = Modifier,
    leadingIcon: Any? = null,
    title: String? = null,
    headerContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }

    CardView(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .noRippleClickable { isExpanded = !isExpanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                leadingIcon?.let {
                    AppIcon(
                        icon = it,
                        modifier = Modifier
                            .size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    title?.let {
                        Text(
                            text = title,
                            style = AppTheme.typography.titleStyle,
                        )
                    }

                    headerContent?.invoke()
                }

                trailingContent?.invoke()

                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription =
                        if (isExpanded) stringResource(R.string.menu_collapse_all)
                        else stringResource(R.string.menu_expand_all),
                    modifier = Modifier.rotate(if (isExpanded) 180f else 0f)
                )
            }

            // expanded section
            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .padding(
                            start = 16.dp, end = 16.dp, bottom = 16.dp
                        )
                ) {
                    content()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CollapsibleSectionPreview() {
    AppTheme {
        CollapsibleSection(
            leadingIcon = R.drawable.ic_configure,
            title = "Section Title",
            initiallyExpanded = true,
            modifier = Modifier,
            headerContent = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Chip(text = "2", icon = R.drawable.ic_land_fields)
                    Chip(text = "10", icon = R.drawable.ic_eye)
                }
            },
            trailingContent = {
                PieChart(completeness = 0.75f)
            },
            content = {
                Text("Section Content")
            },
        )
    }
}