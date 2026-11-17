import com.fieldbook.tracker.utilities.JpegUtil
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Covers the JPEG repair that keeps a camera's EXIF block from overflowing the 65535 byte APP1
 * segment once Field Book's user comment is added to it.
 *
 * Fixtures are built here rather than checked in, so the offsets under test stay readable.
 */
class JpegUtilTest {

    companion object {

        private const val APP0 = 0xE0
        private const val APP1 = 0xE1
        private const val APP2 = 0xE2
        private const val APP5 = 0xE5
        private const val APP14 = 0xEE
        private const val APP15 = 0xEF
        private const val SOS = 0xDA

        /** IFD0 sits 8 bytes into the TIFF block, and its pointer to IFD1 follows one 12 byte entry. */
        private const val NEXT_IFD_POINTER_IN_TIFF = 8 + 2 + 12

        /** TIFF header (8), IFD0 (18), IFD1 (32) — everything ahead of the thumbnail itself. */
        private const val TIFF_SIZE_BEFORE_THUMBNAIL = 56

        private val SCAN_DATA = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0xFF.toByte(), 0xD9.toByte())
    }

    /** Where the TIFF block starts, found the same way a reader would: from the EXIF identifier. */
    private fun ByteArray.tiffOffset(): Int {

        val identifier = "Exif".toByteArray() + byteArrayOf(0, 0)

        val start = indices.first { i ->
            i + identifier.size <= size && identifier.indices.all { this[i + it] == identifier[it] }
        }

        return start + identifier.size
    }

    private fun ByteArray.nextIfdPointer() = tiffOffset() + NEXT_IFD_POINTER_IN_TIFF

    /**
     * Builds an EXIF APP1 payload holding an IFD0 with a single orientation entry, an IFD1
     * describing an embedded thumbnail, and a thumbnail padded well past its real length, the
     * way the cameras that trip this bug write it.
     */
    private fun exifPayload(paddedThumbnailLength: Int): ByteArray {

        val tiff = ByteArrayOutputStream()

        tiff.write("II".toByteArray())      // little endian
        tiff.writeShortLe(42)               // TIFF magic
        tiff.writeIntLe(8)                  // IFD0 sits right after the header

        val ifd1Offset = 8 + 2 + 12 + 4
        val thumbnailOffset = ifd1Offset + 2 + 24 + 4

        tiff.writeShortLe(1)                // IFD0, one entry
        tiff.writeShortLe(0x0112)           // Orientation
        tiff.writeShortLe(3)                // SHORT
        tiff.writeIntLe(1)
        tiff.writeIntLe(1)
        tiff.writeIntLe(ifd1Offset)         // the pointer prepareForExifWrite should zero

        tiff.writeShortLe(2)                // IFD1, two entries
        tiff.writeShortLe(0x0201)           // JPEGInterchangeFormat
        tiff.writeShortLe(4)                // LONG
        tiff.writeIntLe(1)
        tiff.writeIntLe(thumbnailOffset)
        tiff.writeShortLe(0x0202)           // JPEGInterchangeFormatLength
        tiff.writeShortLe(4)
        tiff.writeIntLe(1)
        tiff.writeIntLe(paddedThumbnailLength)
        tiff.writeIntLe(0)                  // no further directories

        // a token thumbnail followed by the padding that makes the block so expensive
        tiff.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))
        tiff.write(ByteArray(paddedThumbnailLength - 4))

        val payload = ByteArrayOutputStream()
        payload.write("Exif".toByteArray())
        payload.write(byteArrayOf(0, 0))
        tiff.writeTo(payload)

        return payload.toByteArray()
    }

    /**
     * @param app1LengthAdjustment bytes to subtract from the declared APP1 length, standing in for
     * the 16 bit truncation that corrupts the real files.
     */
    private fun jpeg(
        paddedThumbnailLength: Int = 200,
        app1LengthAdjustment: Int = 0,
        vendorMarkers: List<Int> = listOf(APP5)
    ): ByteArray {

        val out = ByteArrayOutputStream()

        out.writeMarker(0xD8)                                       // SOI
        out.writeSegment(APP0, "JFIF".toByteArray() + byteArrayOf(0))
        out.writeSegment(APP1, exifPayload(paddedThumbnailLength), app1LengthAdjustment)
        vendorMarkers.forEach { out.writeSegment(it, ByteArray(64) { i -> i.toByte() }) }
        out.writeSegment(APP2, "ICC_PROFILE".toByteArray())
        out.writeSegment(APP14, "Adobe".toByteArray())
        out.writeSegment(SOS, byteArrayOf(0x01, 0x00, 0x00))
        out.write(SCAN_DATA)

        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeMarker(marker: Int) {
        write(0xFF)
        write(marker)
    }

    private fun ByteArrayOutputStream.writeSegment(marker: Int, payload: ByteArray, lengthAdjustment: Int = 0) {
        writeMarker(marker)
        writeShortBe(payload.size + 2 - lengthAdjustment)
        write(payload)
    }

    private fun ByteArrayOutputStream.writeShortBe(value: Int) {
        write((value shr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun ByteArrayOutputStream.writeShortLe(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeIntLe(value: Int) {
        writeShortLe(value and 0xFFFF)
        writeShortLe((value shr 16) and 0xFFFF)
    }

    private fun ByteArray.markers(): List<Int> {
        val found = arrayListOf<Int>()
        var i = 2
        while (i + 3 < size) {
            if (this[i] != 0xFF.toByte()) break
            val marker = this[i + 1].toInt() and 0xFF
            found.add(marker)
            if (marker == SOS) break
            i += 2 + (((this[i + 2].toInt() and 0xFF) shl 8) or (this[i + 3].toInt() and 0xFF))
        }
        return found
    }

    @Test
    fun `only jpeg bytes are recognised as jpeg`() {

        // other formats reach the camera traits, and must not be judged by the jpeg rules
        assertTrue(JpegUtil.isJpeg(jpeg()))
        assertFalse(JpegUtil.isJpeg(ByteArray(0)))
        assertFalse(JpegUtil.isJpeg("ftypmp42".toByteArray()))
    }

    @Test
    fun `a well formed jpeg is structurally valid`() {

        assertTrue(JpegUtil.isStructurallyValid(jpeg()))
    }

    @Test
    fun `a short app1 length is not structurally valid`() {

        // the failure mode on the affected devices: the declared length drops the decoder into the
        // middle of the EXIF block rather than onto the next marker
        assertFalse(JpegUtil.isStructurallyValid(jpeg(app1LengthAdjustment = 40)))
    }

    @Test
    fun `truncated and non jpeg input is not structurally valid`() {

        assertFalse(JpegUtil.isStructurallyValid(ByteArray(0)))
        assertFalse(JpegUtil.isStructurallyValid("not a jpeg at all".toByteArray()))
        assertFalse(JpegUtil.isStructurallyValid(jpeg().copyOf(40)))
    }

    @Test
    fun `preparing zeroes the pointer to the thumbnail directory`() {

        val original = jpeg()
        val pointer = original.nextIfdPointer()

        assertTrue("the fixture should start with a thumbnail to drop", original[pointer].toInt() != 0)

        val prepared = JpegUtil.prepareForExifWrite(original)

        assertArrayEquals(ByteArray(4), prepared.copyOfRange(pointer, pointer + 4))
    }

    @Test
    fun `preparing leaves the rest of the exif block alone`() {

        val thumbnailLength = 200

        val original = jpeg(paddedThumbnailLength = thumbnailLength, vendorMarkers = emptyList())
        val prepared = JpegUtil.prepareForExifWrite(original)

        val pointer = original.nextIfdPointer()
        val app1End = original.tiffOffset() + TIFF_SIZE_BEFORE_THUMBNAIL + thumbnailLength

        assertArrayEquals(
            original.copyOfRange(0, pointer),
            prepared.copyOfRange(0, pointer)
        )
        assertArrayEquals(
            original.copyOfRange(pointer + 4, app1End),
            prepared.copyOfRange(pointer + 4, app1End)
        )
    }

    @Test
    fun `preparing drops vendor app segments and keeps the standard ones`() {

        val original = jpeg(vendorMarkers = listOf(APP5, APP15))
        val prepared = JpegUtil.prepareForExifWrite(original)

        assertEquals(listOf(APP0, APP1, APP5, APP15, APP2, APP14, SOS), original.markers())
        assertEquals(listOf(APP0, APP1, APP2, APP14, SOS), prepared.markers())

        // each vendor segment was a 64 byte payload plus its marker and length
        assertEquals(original.size - 2 * 68, prepared.size)
        assertTrue(JpegUtil.isStructurallyValid(prepared))
    }

    @Test
    fun `preparing leaves the scan data untouched`() {

        val prepared = JpegUtil.prepareForExifWrite(jpeg())

        assertArrayEquals(SCAN_DATA, prepared.copyOfRange(prepared.size - SCAN_DATA.size, prepared.size))
    }

    @Test
    fun `preparing returns unusable input unchanged`() {

        listOf(
            ByteArray(0),
            "not a jpeg at all".toByteArray(),
            jpeg().copyOf(40),                          // truncated mid segment
            jpeg(app1LengthAdjustment = 40),            // the already corrupt case
        ).forEach { assertSame(it, JpegUtil.prepareForExifWrite(it)) }
    }

    @Test
    fun `preparing a jpeg without exif only drops vendor segments`() {

        val out = ByteArrayOutputStream()
        out.writeMarker(0xD8)
        out.writeSegment(APP0, "JFIF".toByteArray() + byteArrayOf(0))
        out.writeSegment(APP5, ByteArray(64))
        out.writeSegment(SOS, byteArrayOf(0x01, 0x00, 0x00))
        out.write(SCAN_DATA)

        val prepared = JpegUtil.prepareForExifWrite(out.toByteArray())

        assertEquals(listOf(APP0, SOS), prepared.markers())
        assertTrue(JpegUtil.isStructurallyValid(prepared))
    }
}
