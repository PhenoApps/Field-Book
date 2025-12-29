package com.fieldbook.tracker.ui

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.components.appBar.AppBar
import com.fieldbook.tracker.ui.theme.AppTheme
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
            AppTheme {
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

    val context = LocalContext.current
    val activity = remember { context as? Activity }

    val combined = remember(audio, video, photo) {
        buildList {
            audio.forEach { add("audio" to it) }
            video.forEach { add("video" to it) }
            photo.forEach { add("photo" to it) }
        }
    }

    Scaffold(topBar = {
        AppBar(
            title = stringResource(R.string.media_viewer),
            navigationIcon = {
                IconButton(onClick = { activity?.finish() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.appbar_back))
                }
            }
        )
    }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(8.dp)
        ) {
            if (combined.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(R.string.no_media_found, ""))
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(combined) { (type, entry) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {

                                when (type) {
                                    "photo" -> PhotoItem(uri = entry.uri)
                                    "video" -> VideoItem(uri = entry.uri)
                                    else -> AudioItem(uri = entry.uri)
                                }

                                Spacer(modifier = Modifier.size(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    IconButton(onClick = { deleteTarget = entry }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.delete),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDelete && deleteTarget != null) {
        ConfirmDeleteDialog(show = true, onConfirm = {
            val target = deleteTarget
            if (target != null) {
                val job = viewModel.deleteMedia(target)
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