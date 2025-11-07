package com.fieldbook.tracker.activities.brapi.io.sync

import com.fieldbook.tracker.brapi.model.Observation
import com.fieldbook.tracker.objects.FieldObject

sealed class DownloadProgressUpdate {
    data class InDownloadProgress(val pageCount: Int, val totalPages: Int) : DownloadProgressUpdate()
    data class Completed(val data: List<Observation>) : DownloadProgressUpdate()
}

sealed class UploadProgressUpdate {
    data class InUploadProgress(val items: Int, val totalItems: Int) : UploadProgressUpdate()
    data class InUploadFailedChunk(val errorCode: Int, val failedChunk: List<Observation>) : UploadProgressUpdate()
    data class Completed(val incompatible: Int, val new: Int, val edited: Int) : UploadProgressUpdate()
}

sealed class MergeStrategy {
    object Local : MergeStrategy()
    object Server : MergeStrategy()
    object MostRecent : MergeStrategy()
}

data class Progress(
    val message: String = "",
    val current: Int = 0,
    val total: Int = 1,
) {
    val primaryProgress: Float get() = if (total > 0) current.toFloat() / total.toFloat() else 0f
}

data class BrapiExportUiState(

    val isInitialized: Boolean = false,
    val study: FieldObject? = null,

    val viewMode: ViewMode = ViewMode.IDLE, // IDLE, EXPORTING, DOWNLOADING, SAVING

    val isUploadFinished: Boolean = false,
    val uploadError: String? = null,

    val uploadImages: Boolean = true, //toggle to upload images or not in the UI

    val isDownloadFinished: Boolean = false,
    val downloadError: String? = null,
    val downloadSuccessMessage: String? = null,
    val downloadMergeStrategy: MergeStrategy = MergeStrategy.Local,

    // Progress
    val progress: Progress = Progress(),

    // Data for UI cards
    val newObservationCount: Int = 0,
    val newImageCount: Int = 0,
    val editedObservationCount: Int = 0,
    val editedImageCount: Int = 0,
    val incompleteImageCount: Int = 0,
    val syncedObservationCount: Int = 0,
    val syncedImageCount: Int = 0,

    // Download results for user to confirm
    val downloadedInserts: Int = 0,
    val downloadedUpdates: Int = 0,
    val downloadFails: Int = 0,

    //Upload results for user to review
    val uploadInserts: Int = 0,
    val uploadEdits: Int = 0,
    val uploadFails: Int = 0,

    val uploadImageInserts: Int = 0,
    val uploadImageEdits: Int = 0,
    val uploadImageFails: Int = 0,
)

enum class ViewMode {
    IDLE,
    EXPORTING,
    DOWNLOADING,
    SAVING
}
