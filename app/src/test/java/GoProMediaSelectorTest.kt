import com.fieldbook.tracker.devices.camera.GoProApi.GoProImage
import com.fieldbook.tracker.devices.camera.GoProMediaSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers how the file a GoPro capture produced is picked out of the camera's media list, and the
 * name it is saved under. A wrong pick files the wrong name against a plot without any error, and
 * the edge cases (chaptered recordings, mixed cards, wrapped numbering) are hard to stage in the
 * field, so they are pinned down here.
 */
class GoProMediaSelectorTest {

    private fun file(
        name: String,
        mod: Long = 0L,
        dir: String = "100GOPRO",
        serial: String? = null
    ) = GoProImage(dir, name, mod, 0L, "http://10.5.5.9:8080/videos/DCIM/$dir/$name", serial)

    private fun GoProImage?.path() = this?.let { "${it.fileDir}/${it.fileName}" }

    /**
     * Recording region
     */
    @Test
    fun singleRecordingIsReturned() {

        val latest = GoProMediaSelector.latestRecording(listOf(file("GX010001.MP4", mod = 100)))

        assertEquals("100GOPRO/GX010001.MP4", latest.path())
    }

    @Test
    fun chapteredRecordingReturnsFirstChapter() {

        val media = listOf(
            file("GX010001.MP4", mod = 100),
            file("GX020001.MP4", mod = 200),
            file("GX030001.MP4", mod = 300)
        )

        assertEquals("100GOPRO/GX010001.MP4", GoProMediaSelector.latestRecording(media).path())
    }

    /**
     * The later chapters must point back at the recording already saved, otherwise the poller
     * would file a second observation for chapter two.
     */
    @Test
    fun laterChapterIsNotANewRecording() {

        val first = GoProMediaSelector.latestRecording(listOf(file("GX010001.MP4", mod = 100)))

        val afterSecondChapter = GoProMediaSelector.latestRecording(
            listOf(file("GX010001.MP4", mod = 100), file("GX020001.MP4", mod = 200))
        )

        assertEquals(first?.url, afterSecondChapter?.url)
    }

    @Test
    fun chapterOfNewerRecordingBeatsHigherChapterOfOlderOne() {

        val media = listOf(
            file("GX010001.MP4", mod = 100),
            file("GX020001.MP4", mod = 200),
            file("GX010002.MP4", mod = 300)
        )

        assertEquals("100GOPRO/GX010002.MP4", GoProMediaSelector.latestRecording(media).path())
    }

    /**
     * Numbering restarts in a new folder once it wraps, so the newest recording can carry the
     * lowest number on the card.
     */
    @Test
    fun newestByTimeWinsOverHighestNumber() {

        val media = listOf(
            file("GX019999.MP4", mod = 100, dir = "100GOPRO"),
            file("GX010001.MP4", mod = 200, dir = "101GOPRO")
        )

        assertEquals("101GOPRO/GX010001.MP4", GoProMediaSelector.latestRecording(media).path())
    }

    @Test
    fun chaptersAreGroupedWithinTheirFolder() {

        val media = listOf(
            file("GX010001.MP4", mod = 100, dir = "100GOPRO"),
            file("GX010001.MP4", mod = 200, dir = "101GOPRO"),
            file("GX020001.MP4", mod = 300, dir = "101GOPRO")
        )

        assertEquals("101GOPRO/GX010001.MP4", GoProMediaSelector.latestRecording(media).path())
    }

    @Test
    fun sameTimestampFallsBackToRecordingNumber() {

        val media = listOf(
            file("GX010002.MP4", mod = 100),
            file("GX010001.MP4", mod = 100)
        )

        assertEquals("100GOPRO/GX010002.MP4", GoProMediaSelector.latestRecording(media).path())
    }

    @Test
    fun avcAndLowerCaseNamesAreRecognised() {

        val latest = GoProMediaSelector.latestRecording(listOf(file("gh010003.mp4", mod = 100)))

        assertEquals("100GOPRO/gh010003.mp4", latest.path())
    }

    @Test
    fun photosAreIgnoredWhenRecording() {

        val media = listOf(
            file("GX010001.MP4", mod = 100),
            file("GOPR0005.JPG", mod = 999)
        )

        assertEquals("100GOPRO/GX010001.MP4", GoProMediaSelector.latestRecording(media).path())
    }

    @Test
    fun noRecordingReturnsNull() {

        assertNull(GoProMediaSelector.latestRecording(listOf(file("GOPR0001.JPG"))))

        assertNull(GoProMediaSelector.latestRecording(emptyList()))
    }

    /**
     * Photo region
     */
    @Test
    fun highestNumberedPhotoIsReturned() {

        val media = listOf(file("GOPR0001.JPG"), file("GOPR0003.JPG"), file("GOPR0002.JPG"))

        assertEquals("100GOPRO/GOPR0003.JPG", GoProMediaSelector.latestPhoto(media).path())
    }

    /**
     * A video name's chapter prefix outnumbers every photo name, which used to make any video on
     * the card the "new photo".
     */
    @Test
    fun videosAreIgnoredWhenTakingPhotos() {

        val media = listOf(
            file("GX010001.MP4"),
            file("GOPR0005.JPG"),
            file("GS010002.360")
        )

        assertEquals("100GOPRO/GOPR0005.JPG", GoProMediaSelector.latestPhoto(media).path())
    }

    @Test
    fun noPhotoReturnsNull() {

        assertNull(GoProMediaSelector.latestPhoto(listOf(file("GX010001.MP4"))))

        assertNull(GoProMediaSelector.latestPhoto(emptyList()))
    }

    @Test
    fun latestFollowsTheCaptureMode() {

        val media = listOf(file("GOPR0005.JPG", mod = 200), file("GX010001.MP4", mod = 100))

        assertEquals("100GOPRO/GX010001.MP4", GoProMediaSelector.latest(media, video = true).path())

        assertEquals("100GOPRO/GOPR0005.JPG", GoProMediaSelector.latest(media, video = false).path())
    }

    /**
     * Saved name region
     */
    @Test
    fun observationNameIsPrefixedWithSerial() {

        val image = file("GX010001.MP4", serial = "C3501324500711")

        assertEquals("C3501324500711/GX010001.MP4", image.observationName)
    }

    @Test
    fun observationNameFallsBackToFileNameWithoutSerial() {

        assertEquals("GOPR0001.JPG", file("GOPR0001.JPG").observationName)
    }
}
