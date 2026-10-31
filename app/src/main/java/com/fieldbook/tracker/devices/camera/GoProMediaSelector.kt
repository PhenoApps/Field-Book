package com.fieldbook.tracker.devices.camera

/**
 * Picks the file a capture produced out of the camera's media list. These rules decide which name
 * an observation is saved under, so they are kept free of Android and the session to be testable.
 *
 * Grouped media (bursts, time lapse photos) is expected to be filtered out before this is called.
 */
object GoProMediaSelector {

    //GXccnnnn.MP4: encoding, two digit chapter and four digit recording number. Every chapter
    //of a long recording shares the recording number.
    private val VIDEO_NAME = Regex("^G([A-Z])(\\d{2})(\\d{4})\\.MP4$", RegexOption.IGNORE_CASE)

    private val FILE_NUMBER = Regex("[0-9]+")

    fun latest(media: List<GoProApi.GoProImage>, video: Boolean): GoProApi.GoProImage? =
        if (video) latestRecording(media) else latestPhoto(media)

    /**
     * Returns the most recently created photo. Only JPGs count: videos and RAW (.GPR) files share
     * the card, and video names outrank every photo by number, so one used to be picked as the
     * "new photo". The camera clock only has second resolution, so photos taken within the same
     * second fall back to the directory and the file number.
     */
    fun latestPhoto(media: List<GoProApi.GoProImage>): GoProApi.GoProImage? =
        media
            .filter { it.fileName.endsWith(".jpg", ignoreCase = true) }
            .maxWithOrNull(
                compareBy<GoProApi.GoProImage>({ it.created }, { it.fileDir }, { fileNumber(it.fileName) })
            )

    /**
     * Returns the first chapter of the newest recording. The newest file found by time decides the
     * recording, and its first chapter names it: later chapters share its number, so they are
     * never mistaken for a new recording and the name points at the start of the footage.
     */
    fun latestRecording(media: List<GoProApi.GoProImage>): GoProApi.GoProImage? {

        data class Chapter(val file: GoProApi.GoProImage, val chapter: Int, val recording: Int)

        val chapters = media.mapNotNull { file ->
            VIDEO_NAME.matchEntire(file.fileName)?.destructured?.let { (_, chapter, recording) ->
                Chapter(file, chapter.toInt(), recording.toInt())
            }
        }

        val newest = chapters.maxWithOrNull(
            compareBy<Chapter>({ it.file.created }, { it.recording }, { it.chapter })
        ) ?: return null

        return chapters
            .filter { it.file.fileDir == newest.file.fileDir && it.recording == newest.recording }
            .minByOrNull { it.chapter }
            ?.file
    }

    private fun fileNumber(fileName: String): Int =
        FILE_NUMBER.find(fileName.substringBeforeLast('.'))?.value?.toIntOrNull() ?: -1
}
