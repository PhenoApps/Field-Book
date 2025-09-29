package com.fieldbook.shared

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.theme.MainTheme


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(onBack: (() -> Unit)? = null) {
    MainTheme {
        val configItems = listOf(
            "Fields",
            "Traits",
            "Collect",
            "Export",
            "Advanced",
            "Statistics",
            "About"
        )
        /*val configIcons = listOf(
            Res.drawable.ic_nav_drawer_fields,
            Res.drawable.ic_nav_drawer_traits,
            Res.drawable.ic_nav_drawer_collect_data,
            Res.drawable.trait_date_save,
            Res.drawable.ic_nav_drawer_settings,
            Res.drawable.ic_nav_drawer_statistics,
            Res.drawable.ic_tb_info
        )*/
        Surface(modifier = Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(text = "KMP Module") },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(configItems) { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            /*Icon(
                                painter = painterResource(configIcons[index]),
                                contentDescription = item,
                                modifier = Modifier.padding(end = 16.dp).size(24.dp)
                            )*/
                            Text(
                                text = item,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Divider()
                    }
                }
            }
        }
    }
}
