package com.fieldbook.tracker.devices.ptpip

import android.content.Context
import androidx.core.net.toUri
import java.io.File
import java.io.OutputStreamWriter
import java.nio.ByteBuffer

class PtpSession(val comMan: ChannelBufferManager,
                 val eventMan: ChannelBufferManager,
                 var callbacks: PtpSessionCallback?) {

    private var tid = 0

    //PowerShot v10 only goes into remote mode if session id starts here
    val sessionId = byteArrayOf(0x41, 0x0, 0x0, 0x0)

    companion object {

        const val PACKET_TYPE_OPERATION_REQUEST = 0x06
        const val PACKET_TYPE_START_DATA = 0x09
        const val PACKET_TYPE_END_DATA = 0x0c
        const val PACKET_TYPE_DATA = 0x0a
        const val PACKET_TYPE_EVENT_REQUEST = 0x03
        const val PACKET_TYPE_START_SESSION = 0x01
        const val PACKET_TYPE_RESPONSE = 0x07

        const val EVENT_RESPONSE_OK = 0x04
        const val COMMAND_RESPONSE_OK = 0x07

        const val DATA_FROM_CAMERA = 0x01
        const val DATA_TO_CAMERA = 0x02

        const val OP_START_SESSION: Short = 0x1002
        const val OP_CLOSE_SESSION: Short = 0x1003
        const val OP_9087: Short = 0x9087.toShort()
        const val OP_GET_LIVE_VIEW: Short = 0x9153.toShort()
        const val OP_REMOTE_MODE: Short = 0x9114.toShort()
        const val OP_EVENT_MODE: Short = 0x9115.toShort()
        const val OP_SET_PROP_VALUE: Short = 0x9110.toShort()
        const val OP_902f: Short = 0x902f.toShort()
        const val OP_UI_UNLOCK: Short = 0x911c.toShort()
        const val OP_UI_LOCK: Short = 0x911b.toShort()
        const val OP_GET_OBJECT_HANDLES: Short = 0x1007.toShort()
        const val OP_GET_STORAGE_IDS: Short = 0x9101.toShort()
        const val OP_GET_IMAGE: Short = 0x101b.toShort()
    }

    private fun nextId() = try {
        ++tid
    } catch (e: Exception) {
        tid = 0
        ++tid
    }

    fun request(operations: (tid: Int) -> Unit) {

        operations.invoke(tid)

    }

    fun transaction(operation: (tid: Int) -> Unit, onComplete: (Boolean) -> Unit) {

        val tid = nextId()

        operation.invoke(tid)

        onComplete(verifyResponse(comMan))
    }

    fun disconnect() {

        comMan.channel.close()

        eventMan.channel.close()

        callbacks = null

    }

    fun verifyResponse(chanMan: ChannelBufferManager): Boolean {

        chanMan.channel.socket().getOutputStream().flush()

        val length = chanMan.getInt()

        return if (length > 0) {

            val response = chanMan.getBytes(length - Int.SIZE_BYTES)
            val data = response.slice(4..5).toByteArray().toShort()
            data == 0x2001.toShort()

        } else true

    }

    fun writeGetImage1(handle: ByteArray, tid: Int, offset: ByteArray, length: ByteArray) {

        PtpOperations.writeOperation(
            comMan.channel,
            PACKET_TYPE_OPERATION_REQUEST,
            DATA_FROM_CAMERA,
            OP_GET_IMAGE,
            tid,
            handle + offset + length
        )
    }

    fun writeUiLock(tid: Int, lock: Boolean) {

        PtpOperations.writeOperation(
            comMan.channel,
            PACKET_TYPE_OPERATION_REQUEST,
            DATA_TO_CAMERA,
            if (lock) OP_UI_LOCK else OP_UI_UNLOCK,
            tid,
            byteArrayOf()
        )
    }

    fun writeGetObjectHandles(storageId: ByteArray, tid: Int) {

        PtpOperations.writeOperation(
            comMan.channel,
            PACKET_TYPE_OPERATION_REQUEST,
            DATA_FROM_CAMERA,
            OP_GET_OBJECT_HANDLES,
            tid,
            storageId
        )
    }

    fun writeGetStorageIds(tid: Int) {

        PtpOperations.writeOperation(
            comMan.channel,
            PACKET_TYPE_OPERATION_REQUEST,
            DATA_FROM_CAMERA,
            OP_GET_STORAGE_IDS,
            tid,
            byteArrayOf()
        )
    }

    fun writeRemoteMode(tid: Int) {

        PtpOperations.writeOperation(
            comMan.channel,
            PACKET_TYPE_OPERATION_REQUEST,
            DATA_FROM_CAMERA,
            OP_REMOTE_MODE,
            tid,
            byteArrayOf(0x15, 0x0, 0x0, 0x0)
        )
    }

    fun writeEventMode(tid: Int) {

        PtpOperations.writeOperation(
            comMan.channel,
            PACKET_TYPE_OPERATION_REQUEST,
            DATA_FROM_CAMERA,
            OP_EVENT_MODE,
            tid,
            byteArrayOf(0x02, 0x0, 0x0, 0x0)
        )
    }

    fun writeRemoteReleaseOn(tid: Int) {

        comMan.channel.write(
            ByteBuffer.wrap(
                byteArrayOf(
                    0x1a, 0x00, 0x0, 0x0,
                    0x06, 0x00, 0x00, 0x0, //packet type: operation request
                    0x01, 0x00, 0x00, 0x0, //data phase: no data
                    0x28, 0x91.toByte()
                )
                        + tid.toByteArray()
                        + byteArrayOf(
                    0x03, 0x00, 0x0, 0x0,
                    0x0, 0x0, 0x0, 0x0,
                )
            )
        )
    }

    fun writeRemoteReleaseOff(tid: Int) {

        comMan.channel.write(
            ByteBuffer.wrap(
                byteArrayOf(
                    0x16, 0x00, 0x0, 0x0,
                    0x06, 0x00, 0x00, 0x0, //packet type: operation request
                    0x01, 0x00, 0x00, 0x0, //data phase: no data
                    0x29, 0x91.toByte()
                )
                        + tid.toByteArray()
                        + byteArrayOf(
                    0x03, 0x00, 0x0, 0x0,
                )
            )
        )
    }

    fun writeStartShootingMode(tid: Int) {

        comMan.channel.write(
            ByteBuffer.wrap(
                byteArrayOf(
                    0x1a, 0x00, 0x0, 0x0,
                    0x06, 0x00, 0x00, 0x0, //packet type: operation request
                    0x01, 0x00, 0x00, 0x0, //data phase: no data
                    0x08, 0x90.toByte()
                )
                        + tid.toByteArray()
                        + byteArrayOf(
                    0x01, 0x00, 0x0, 0x0, // capture phase focus 0x1
                    0x0, 0x0, 0x0, 0x0, // unknown
                )
            )
        )
    }

    fun writeStartImageRelease(tid: Int) {

        comMan.channel.write(
            ByteBuffer.wrap(
                byteArrayOf(
                    0x1a, 0x00, 0x0, 0x0,
                    0x06, 0x00, 0x00, 0x0, //packet type: operation request
                    0x01, 0x00, 0x00, 0x0, //data phase: no data
                    0x28, 0x91.toByte()
                )
                        + tid.toByteArray()
                        + byteArrayOf(// open session (0x1002)
                    0x02, 0x00, 0x0, 0x0, // capture phase release 0x02
                    0x0, 0x0, 0x0, 0x0 //unknown property
                )
            )
        )
    }

    fun writeStopImageRelease(tid: Int) {

        comMan.channel.write(
            ByteBuffer.wrap(
                byteArrayOf(
                    0x16, 0x00, 0x0, 0x0,
                    0x06, 0x00, 0x00, 0x0, //packet type: operation request
                    0x01, 0x00, 0x00, 0x0, //data phase: no data
                    0x29, 0x91.toByte()
                )
                        + tid.toByteArray()
                        + byteArrayOf(// open session (0x1002)
                    0x02, 0x00, 0x0, 0x0 // capture phase release
                )
            )
        )
    }

    fun writeStopAutoFocus(tid: Int) {

        comMan.channel.write(
            ByteBuffer.wrap(
                byteArrayOf(
                    0x16, 0x00, 0x0, 0x0,
                    0x06, 0x00, 0x00, 0x0, //packet type: operation request
                    0x01, 0x00, 0x00, 0x0, //data phase: no data
                    0x29, 0x91.toByte()
                )
                        + tid.toByteArray()
                        + byteArrayOf(// open session (0x1002)
                    0x02, 0x00, 0x0, 0x0 // capture phase focus
                )
            )
        )
    }

    fun writeGetLiveView(tid: Int) {

        PtpOperations.writeOperation(
            comMan.channel,
            PACKET_TYPE_OPERATION_REQUEST,
            DATA_FROM_CAMERA,
            OP_GET_LIVE_VIEW,
            tid,
            byteArrayOf(
                0x0, 0x0, 0x20, 0x00, //param
                0x1, 0x0, 0x0, 0x0, //param
                0x0, 0x0, 0x0, 0x0
            )
        )
    }

    fun write902f(tid: Int) {

        PtpOperations.writeOperation(
            comMan.channel,
            PACKET_TYPE_OPERATION_REQUEST,
            DATA_TO_CAMERA,
            OP_902f,
            tid,
            byteArrayOf()
        )
    }

    fun write9087(tid: Int) {

        PtpOperations.writeOperation(
            comMan.channel,
            PACKET_TYPE_OPERATION_REQUEST,
            DATA_TO_CAMERA,
            OP_9087,
            tid,
            byteArrayOf()
        )
    }

    fun writePropertyValue(tid: Int) {

        PtpOperations.writeOperation(
            comMan.channel,
            PACKET_TYPE_OPERATION_REQUEST,
            DATA_TO_CAMERA,
            OP_SET_PROP_VALUE,
            tid,
            byteArrayOf()
        )
    }

    fun writeStartDataPacket(totalDataLength: Int, tid: Int) {

        val packet = PACKET_TYPE_START_DATA.toByteArray()
        val id = tid.toByteArray()
        val totalData = totalDataLength.toLong().toByteArray()

        val length = (4 + packet.size + id.size + totalData.size).toByteArray()

        val payload = ByteBuffer.wrap(
            length
                    + packet
                    + id
                    + totalData
        )

        comMan.channel.write(payload)

    }

    fun writeEndDataPacket(data: ByteArray, tid: Int) {

        val packet = PACKET_TYPE_END_DATA.toByteArray()
        val id = tid.toByteArray()

        val length = (Int.SIZE_BYTES + Int.SIZE_BYTES + packet.size + id.size + data.size).toByteArray()

        val eventLength = (Int.SIZE_BYTES + data.size).toByteArray()

        val payload = ByteBuffer.wrap(
            length
                    + packet
                    + id
                    + eventLength
                    + data
        )

        comMan.channel.write(payload)

    }

    fun debugBuffer(context: Context) {

        try {

            val output = File(context.externalCacheDir, "//Log.txt")

            context.contentResolver.openOutputStream(output.toUri(), "wa").use { stream ->

                val writer = OutputStreamWriter(stream)

                val intBuffer = ByteBuffer.allocate(16)

                try {

                    var i = 1

                    while (true) {

                        comMan.channel.read(intBuffer)

                        writer.write(intBuffer.array().joinToString(", ") { "0x%02x".format(it) }
                            .split(", ").chunked(4).joinToString("\n") { "$it" })

                        writer.write("\n")
                        writer.flush()

                        intBuffer.clear()

                        i++
                    }

                } catch (_: Exception) {}

                writer.flush()

                writer.close()

                stream?.close()
            }

        } catch (_: Exception) {}
    }
}