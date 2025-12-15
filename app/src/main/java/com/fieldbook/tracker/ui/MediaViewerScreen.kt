package com.fieldbook.tracker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MediaViewerActivity : ComponentActivity() {

    private val viewModel: MediaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val studyId = intent.getStringExtra(EXTRA_STUDY_ID) ?: "0"
        val obsUnit = intent.getStringExtra(EXTRA_OBS_UNIT) ?: ""
        val traitDbId = intent.getStringExtra(EXTRA_TRAIT_DB_ID) ?: ""

        viewModel.loadMediaFor(studyId, obsUnit, traitDbId)

        setContent {
            MaterialTheme {
                MediaViewerScreen(
                    viewModel = viewModel,
                    studyId = studyId,
                    obsUnit = obsUnit,
                    traitDbId = traitDbId
                )
            }
        }
    }

    companion object {
        const val EXTRA_STUDY_ID = "extra_study_id"
        const val EXTRA_OBS_UNIT = "extra_observation_unit"
        const val EXTRA_TRAIT_DB_ID = "extra_trait_db_id"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    viewModel: MediaViewModel,
    studyId: String,
    obsUnit: String,
    traitDbId: String
) {
    val audio by viewModel.audio.collectAsState()
    val video by viewModel.video.collectAsState()
    val photo by viewModel.photo.collectAsState()

    var deleteTarget by remember { mutableStateOf<MediaEntry?>(null) }
    val showDelete = deleteTarget != null

    val selectedTab = remember { mutableIntStateOf(0) }

    Scaffold(topBar = {
        TopAppBar(title = { Text(text = stringResource(R.string.media_viewer)) })
    }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(8.dp)
        ) {

            TabRow(selectedTabIndex = selectedTab.value) {
                Tab(
                    selected = selectedTab.intValue == 0,
                    onClick = { selectedTab.intValue = 0 },
                    text = {
                        Text(
                            stringResource(R.string.audio)
                        )
                    })
                Tab(
                    selected = selectedTab.intValue == 1,
                    onClick = { selectedTab.intValue = 1 },
                    text = {
                        Text(
                            stringResource(R.string.video)
                        )
                    })
                Tab(
                    selected = selectedTab.intValue == 2,
                    onClick = { selectedTab.intValue = 2 },
                    text = {
                        Text(
                            stringResource(R.string.photos)
                        )
                    })
            }

            when (selectedTab.intValue) {
                0 -> MediaEntryList(
                    items = audio,
                    type = "audio",
                    onRequestDelete = { deleteTarget = it })

                1 -> MediaEntryList(
                    items = video,
                    type = "video",
                    onRequestDelete = { deleteTarget = it })

                2 -> MediaEntryList(
                    items = photo,
                    type = "photo",
                    onRequestDelete = { deleteTarget = it })
            }
        }
    }

    if (showDelete && deleteTarget != null) {
        ConfirmDeleteDialog(show = true, onConfirm = {
            if (deleteTarget != null) {
                // request deletion and reload after the async job completes
                val job = viewModel.deleteMedia(deleteTarget!!)
                job.invokeOnCompletion {
                    viewModel.loadMediaFor(studyId, obsUnit, traitDbId)
                }
                deleteTarget = null
            }
        }, onDismiss = {
            deleteTarget = null
        })
    }
}

@Composable
fun MediaEntryList(items: List<MediaEntry>, type: String, onRequestDelete: (MediaEntry) -> Unit) {
    if (items.isEmpty()) {
        Text(text = stringResource(R.string.no_media_found, type))
        return
    }

    LazyColumn {
        items(items) { entry ->
            Card(
                modifier = Modifier
                    .padding(4.dp)
                    .fillMaxWidth(), shape = RoundedCornerShape(8.dp)
            ) {
                when (type) {
                    "photo" -> PhotoItem(uri = entry.uri, onDelete = { onRequestDelete(entry) })
                    "video" -> VideoItem(uri = entry.uri, onDelete = { onRequestDelete(entry) })
                    else -> AudioItem(uri = entry.uri, onDelete = { onRequestDelete(entry) })
                }
            }
        }
    }
}