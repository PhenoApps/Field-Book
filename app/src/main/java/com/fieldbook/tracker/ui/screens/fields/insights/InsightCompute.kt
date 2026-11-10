package com.fieldbook.tracker.ui.screens.fields.insights

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs [compute] on a background thread, returning null until it finishes. Summaries that walk
 * every observation in a large field take long enough to stall the screen if done while it
 * composes.
 *
 * The result is only returned for the [keys] it was computed from, so while a recompute runs
 * nothing new is drawn against the previous result.
 */
@Composable
fun <T> rememberOffMain(vararg keys: Any?, compute: () -> T): T? {
    val keyList = keys.toList()
    val currentCompute by rememberUpdatedState(compute)

    val result by produceState<Pair<List<Any?>, T>?>(null, *keys) {
        value = keyList to withContext(Dispatchers.Default) { currentCompute() }
    }

    return result?.takeIf { it.first == keyList }?.second
}

/** Shown in place of a section while [rememberOffMain] is still working. */
@Composable
fun ComputingIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = AppTheme.colors.interactive.accent)
    }
}
