package com.fieldbook.tracker.devices.camera

/**
 * Picks the file a capture produced out of the camera's media list. These rules decide which name
 * an observation is saved under, so they are kept free of Android and the session to be testable.
 */
object GoProMediaSelector {

    //GXccnnnn.MP4: encoding, two digit chapter and four digit recording number. Every chapter
    //of a long recording shares the recording number.
    private val VIDEO_NAME = Regex("^G([A-Z])(\\d{2})(\\d{4})\\.MP4$", RegexOption.IGNORE_CASE)

    private val VIDEO_EXTENSIONS = listOf(".mp4", ".360")

    private val PHOTO_NAME = Regex("^([a-zA-Z]*)([0-9]*).([a-zA-Z]*)$")

    fun latest(media: List<GoProApi.GoProImage>, video: Boolean): GoProApi.GoProImage? =
        if (video) latestRecording(media) else latestPhoto(media)

    /**
     * Returns the most recently numbered photo. Videos are left out: their names carry a chapter
     * prefix that outnumbers every photo, so one on the card was picked as the "new photo".
     */
    fun latestPhoto(media: List<GoProApi.GoProImage>): GoProApi.GoProImage? =
        media
            .filterNot { file -> VIDEO_EXTENSIONS.any { file.fileName.endsWith(it, ignoreCase = true) } }
            .maxByOrNull {
                PHOTO_NAME.matchEntire(it.fileName)?.destructured?.let { (_, number, _) ->
                    number.toIntOrNull() ?: -1
                } ?: -1
            }

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
            compareBy<Chapter>({ it.file.mod }, { it.recording }, { it.chapter })
        ) ?: return null

        return chapters
            .filter { it.file.fileDir == newest.file.fileDir && it.recording == newest.recording }
            .minByOrNull { it.chapter }
            ?.file
    }
}
