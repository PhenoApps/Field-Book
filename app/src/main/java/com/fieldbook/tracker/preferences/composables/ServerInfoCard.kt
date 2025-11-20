package com.fieldbook.tracker.preferences.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.components.CardView
import com.fieldbook.tracker.ui.theme.AppTheme

@Composable
fun ServerInfoCard(
    serverName: String,
    organizationName: String,
    serverDescription: String
) {
    CardView {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.brapi_server_name_label, serverName),
                style = AppTheme.typography.titleStyle,
            )

            if (organizationName.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.brapi_organization_name_label, organizationName),
                    style = AppTheme.typography.subheadingStyle,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (serverDescription.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.brapi_server_description_label, serverDescription),
                    style = AppTheme.typography.subheadingStyle,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ServerInfoCardPreview() {
    AppTheme {
        ServerInfoCard(
            serverName = "Test Server",
            organizationName = "Breeding Insight",
            serverDescription = "A test server"
        )
    }
}