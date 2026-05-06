package com.fieldbook.shared.brapi.model.v2.phenotyping

data class BrapiImageExport(
    val fieldBookDbId: String,
    val imageDbId: String? = null,
    val observationUnitDbId: String,
    val fileName: String,
    val imageName: String = fileName,
    val mimeType: String = DEFAULT_MIME_TYPE,
    val fileSize: Int? = null,
    val observationTimeStamp: String? = null,
    val lastSyncedTime: String? = null,
    val content: ByteArray,
) {
    enum class Status {
        NEW,
        SYNCED,
        EDITED,
        INCOMPLETE,
        INVALID
    }

    val status: Status
        get() = when {
            content.isEmpty() -> Status.INVALID
            imageDbId.isNullOrBlank() -> Status.NEW
            lastSyncedTime.isNullOrBlank() -> Status.INCOMPLETE
            observationTimeStamp.isNullOrBlank() || observationTimeStamp <= lastSyncedTime -> Status.SYNCED
            observationTimeStamp > lastSyncedTime -> Status.EDITED
            else -> Status.INVALID
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BrapiImageExport) return false

        return fieldBookDbId == other.fieldBookDbId &&
            imageDbId == other.imageDbId &&
            observationUnitDbId == other.observationUnitDbId &&
            fileName == other.fileName &&
            imageName == other.imageName &&
            mimeType == other.mimeType &&
            fileSize == other.fileSize &&
            observationTimeStamp == other.observationTimeStamp &&
            lastSyncedTime == other.lastSyncedTime &&
            content.contentEquals(other.content)
    }

    override fun hashCode(): Int {
        var result = fieldBookDbId.hashCode()
        result = 31 * result + (imageDbId?.hashCode() ?: 0)
        result = 31 * result + observationUnitDbId.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + imageName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + (fileSize ?: 0)
        result = 31 * result + (observationTimeStamp?.hashCode() ?: 0)
        result = 31 * result + (lastSyncedTime?.hashCode() ?: 0)
        result = 31 * result + content.contentHashCode()
        return result
    }

    companion object {
        const val DEFAULT_MIME_TYPE = "image/jpeg"
    }
}
