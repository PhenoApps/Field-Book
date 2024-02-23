package com.fieldbook.tracker.devices.camera

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.devices.ptpip.ChannelBufferManager
import com.fieldbook.tracker.devices.ptpip.PtpOperations.Companion.writeOperation
import com.fieldbook.tracker.devices.ptpip.PtpSession
import com.fieldbook.tracker.devices.ptpip.PtpSession.Companion.DATA_FROM_CAMERA
import com.fieldbook.tracker.devices.ptpip.PtpSession.Companion.EVENT_RESPONSE_OK
import com.fieldbook.tracker.devices.ptpip.PtpSession.Companion.OP_CLOSE_SESSION
import com.fieldbook.tracker.devices.ptpip.PtpSession.Companion.OP_START_SESSION
import com.fieldbook.tracker.devices.ptpip.PtpSession.Companion.PACKET_TYPE_DATA
import com.fieldbook.tracker.devices.ptpip.PtpSession.Companion.PACKET_TYPE_END_DATA
import com.fieldbook.tracker.devices.ptpip.PtpSession.Companion.PACKET_TYPE_EVENT_REQUEST
import com.fieldbook.tracker.devices.ptpip.PtpSession.Companion.PACKET_TYPE_OPERATION_REQUEST
import com.fieldbook.tracker.devices.ptpip.PtpSession.Companion.PACKET_TYPE_RESPONSE
import com.fieldbook.tracker.devices.ptpip.PtpSession.Companion.PACKET_TYPE_START_DATA
import com.fieldbook.tracker.devices.ptpip.PtpSession.Companion.PACKET_TYPE_START_SESSION
import com.fieldbook.tracker.devices.ptpip.PtpSessionCallback
import com.fieldbook.tracker.devices.ptpip.toByteArray
import com.fieldbook.tracker.devices.ptpip.toInt
import com.fieldbook.tracker.objects.RangeObject
import com.fieldbook.tracker.preferences.GeneralKeys
import dagger.hilt.android.qualifiers.ActivityContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import javax.inject.Inject


class CanonApi @Inject constructor(@ActivityContext private val context: Context) {

    companion object {
        const val TAG = "CanonAPI"
        const val HANDLE_UPDATE_FRAME_COUNT = 60
        const val IMAGE_SIZE = 50000
        const val PHONE_NAME = "Field Book"
    }

    var isConnected = false

    private var obsUnit: RangeObject? = null
    private var session: PtpSession? = null
    private var handles = hashMapOf<Int, ByteArray>()
    private var capturing = false

    private val prefs by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    private val canonIp by lazy {
        prefs.getString(GeneralKeys.CANON_IP, "192.168.1.2")
    }

    private val canonPort by lazy {
        prefs.getString(GeneralKeys.CANON_PORT, "15740")?.toInt() ?: 15740
    }

    private val isDebug by lazy {
        prefs.getBoolean(GeneralKeys.CANON_DEBUG, false)
    }

    private fun log(message: String) {
        if (isDebug) {
            Log.d(TAG, message)
        }
    }

    fun stopSession() {

        isConnected = false

        session?.let { session ->

            requestStopSession(session)

        }
    }

    fun initiateSession(sessionCallback: PtpSessionCallback) {

        if (isConnected) {
            resumeSession(sessionCallback)
        } else {
            isConnected = true
            startNewSession(sessionCallback)
        }
    }

    private fun startSession(
        chanMan: ChannelBufferManager,
        eventChanMan: ChannelBufferManager,
        sessionCallback: PtpSessionCallback
    ) {

        val session = PtpSession(chanMan, eventChanMan, sessionCallback)

        this.session = session

        requestStartSession(session)
    }

    private fun requestStartSession(session: PtpSession) {

        session.callbacks?.onSessionStart()

        log("Starting session")

        session.transaction({ tid ->

            writeOperation(
                session.comMan.channel,
                PACKET_TYPE_OPERATION_REQUEST,
                DATA_FROM_CAMERA,
                OP_START_SESSION,
                tid,
                session.sessionId
            )

        }) { response ->

            log("Starting session responded $response")

            if (response) {

                requestRemoteMode(session)
            }
        }
    }

    private fun requestStopSession(session: PtpSession) {

        log("Stopping session")

        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {

            try {

                session.transaction({ tid ->

                    writeOperation(
                        session.comMan.channel,
                        PACKET_TYPE_OPERATION_REQUEST,
                        DATA_FROM_CAMERA,
                        OP_CLOSE_SESSION,
                        tid,
                        session.sessionId
                    )

                }) { response ->

                    log("Stopping session responded $response")

                    session.callbacks?.onSessionStop()

                    session.disconnect()

                }

            } catch (_: Exception) {
            }
        }
    }

    private fun verifyInitCommandAckPacket(chanMan: ChannelBufferManager): ByteArray {

        val length = chanMan.getInt()

        val data = chanMan.getBytes(length - Int.SIZE_BYTES)

        return data.slice(4..7).toByteArray()
    }

    private fun readEventType(session: PtpSession, eventBytes: Int, eventType: Int) {

        val propCode = session.comMan.getInt()

        session.comMan.getBytes(eventBytes - 12)

    }

    private fun readEventData(session: PtpSession) {

        var total = 0

        do {

            val eventBytes = session.comMan.getInt()

            total += eventBytes

            val eventType = session.comMan.getInt()

            if (eventBytes == 8 && eventType == 0) {

                break

            }

            readEventType(session, eventBytes, eventType)

            if (eventType == PACKET_TYPE_RESPONSE) break

        } while (true)
    }

    private fun writeParameter1(session: PtpSession, storageId: ByteArray) {

        log("Writing parameter d136, required for live view")

        session.transaction({ tid ->

            val cameraModeDataLength = Int.SIZE_BYTES
            val cameraModeData = byteArrayOf(0x36, 0xd1.toByte(), 0x0, 0x0, 0x0, 0x0, 0x0, 0x0)

            session.writePropertyValue(tid)

            session.writeStartDataPacket(cameraModeDataLength + cameraModeData.size, tid)

            session.writeEndDataPacket(cameraModeData, tid)

        }) {

            log("Writing parameter d136 response: $it")

            writeParameter2(session, storageId)
        }
    }

    private fun writeParameter2(session: PtpSession, storageId: ByteArray) {

        log("Writing parameter d1b0 required for live view")

        session.transaction({ tid ->

            val cameraMode2Length = 4
            val cameraMode2Data =
                byteArrayOf(0xb0.toByte(), 0xd1.toByte(), 0x0, 0x0, 0x09, 0x0, 0x0, 0x0)

            session.writePropertyValue(tid)

            session.writeStartDataPacket(cameraMode2Length + cameraMode2Data.size, tid)

            session.writeEndDataPacket(cameraMode2Data, tid)

        }) { response ->

            log("d1b0 response $response")

            if (response) {

                startMainLoop(session, storageId)
            }
        }
    }

    private fun writeRequest(channel: SocketChannel, packetType: Int, params: ByteArray) {

        val packet = packetType.toByteArray()

        val length = (Int.SIZE_BYTES + packet.size + params.size).toByteArray()

        val payload = ByteBuffer.wrap(
            length
                    + packet
                    + params
        )

        channel.write(payload)

        channel.socket().getOutputStream().flush()
    }

    private fun writeLiveViewParameters(session: PtpSession, storageId: ByteArray) {

        writeParameter1(session, storageId)

    }

    private fun startNewSession(sessionCallback: PtpSessionCallback) {

        //TODO potentially add these timeouts to preferences
        val command: SocketChannel = SocketChannel.open(InetSocketAddress(canonIp, canonPort))
        command.socket().soTimeout = 10000
        command.socket().keepAlive = true
        command.socket().tcpNoDelay = true
        command.socket().reuseAddress = true

        val chanMan = ChannelBufferManager(command)

        val connectionNumber = requestConnection(chanMan)

        val events = SocketChannel.open(InetSocketAddress(canonIp, canonPort))
        events.socket().soTimeout = 120000
        events.socket().keepAlive = true
        events.socket().tcpNoDelay = true

        val eventChanMan = ChannelBufferManager(events)

        writeRequest(events, PACKET_TYPE_EVENT_REQUEST, connectionNumber)

        if (verifyEventResponse(eventChanMan)) {

            startSession(chanMan, eventChanMan, sessionCallback)

        }
    }

    private fun resumeSession(sessionCallbacks: PtpSessionCallback) {

        this.session?.callbacks = sessionCallbacks

        this.session?.callbacks?.onSessionStart()

    }

    private fun requestConnection(chanMan: ChannelBufferManager): ByteArray {

        val uuid = ByteArray(16) { 0x0 }

        val nameBytes = PHONE_NAME.toByteArray(Charsets.US_ASCII)
        val data = nameBytes
            .zip(ByteArray(nameBytes.size) { 0x00 })
            .flatMap { (x, y) -> listOf(x, y) }
            .toByteArray() + byteArrayOf(0x00, 0x00)

        val version = byteArrayOf(0x0, 0x0, 0x01, 0x0)

        writeRequest(chanMan.channel, PACKET_TYPE_START_SESSION, uuid + data + version)

        return verifyInitCommandAckPacket(chanMan)
    }

    private fun requestGetImage(session: PtpSession, handle: ByteArray, unit: RangeObject) {

        log("Requesting get image")

        session.transaction({ tid ->

            session.writeGetImage(handle, tid)

            val payloadSize = awaitStartPacket(session)

            while (true) {

                val (packetType, data) = awaitImageEndPacket(session)

                if (payloadSize >= IMAGE_SIZE) {

                    log("Found image")

                    try {

                        val bmp = BitmapFactory.decodeStream(ByteArrayInputStream(data))

                        session.callbacks?.onBitmapCaptured(bmp, unit)

                    } catch (ignore: Exception) {
                    }
                }

                if (packetType == 12) break
            }

        }) { response ->

            log("Response get image $response")

        }
    }

    private fun startMainLoop(
        session: PtpSession,
        storageId: ByteArray
    ) {

        log("Starting main loop")

        var frame = HANDLE_UPDATE_FRAME_COUNT / 2

        while (isConnected) {

            if (capturing) {

                capturing = false

                startCapture(session)

                requestUpdateHandles(session, storageId)

                continue

            } else {

                requestLiveViewImage(session)

                if (frame == 0) {

                    requestUpdateHandles(session, storageId)

                }

                frame = (frame + 1) % HANDLE_UPDATE_FRAME_COUNT

                //Thread.sleep(1000/24)
                Thread.sleep(1000 / 60)

            }
        }
    }

    private fun requestUpdateHandles(session: PtpSession, storageId: ByteArray) {

        log("Requesting update image handles")

        var newHandles: Array<ByteArray>? = null

        session.transaction({ tid ->

            session.writeGetObjectHandles(storageId, tid)

            val size = awaitStartPacket(session)

            val handles = awaitEndPacketHandles(session)

            newHandles = handles.toTypedArray()

        }) { response ->

            log("Response update image handles $response")

            if (response) {

                var newImage = handles.isNotEmpty()

                newHandles?.forEach { handle ->

                    val handleInteger = handle.toInt()

                    if (newImage) {

                        if (handleInteger !in handles) {

                            obsUnit?.let { unit ->

                                log("Found new image $handleInteger for ${unit.plot_id}")

                                requestGetImage(session, handle, unit)

                            }
                        }
                    }

                    handles[handleInteger] = handle
                }
            }
        }
    }

    private fun requestLiveViewImage(session: PtpSession) {

        log("Requesting live view image")

        session.transaction({ tid ->

            session.writeGetLiveView(tid)

            var payloadSize = awaitStartPacket(session)

            var total = 0

            while (true) {

                val (size, type) = awaitDataPacket(session)

                if (type == 12) break

                total += size

            }

        }) { response ->

            log("Response live view image $response")
        }
    }

    private fun requestStartImageRelease(session: PtpSession) {

        log("Requesting start image release")

        session.transaction({ tid ->

            session.writeStartImageRelease(tid)

        }) { response ->

            log("Start image release response $response")

            requestStopImageRelease(session)

        }
    }

    private fun requestStopAutoFocus(session: PtpSession) {

        log("Request stop auto focus")

        session.transaction({ tid ->

            session.writeStopAutoFocus(tid)

        }) { response ->

            log("Response stop auto focus $response")
        }
    }

    private fun requestStopImageRelease(session: PtpSession) {

        log("Requesting stop image release")

        session.transaction({ tid ->

            session.writeStopImageRelease(tid)

        }) { response ->

            log("Stop image release response: $response")

            requestStopAutoFocus(session)

        }
    }

    private fun requestRemoteReleaseOff(session: PtpSession) {

        log("Requesting remote release off")

        session.transaction({ tid ->

            session.writeRemoteReleaseOff(tid)

        }) { response ->

            log("Remote release response $response")

            requestStartImageRelease(session)

        }
    }

    private fun requestRemoteReleaseOn(session: PtpSession) {

        log("Requesting remote release on")

        session.transaction({ tid ->

            session.writeRemoteReleaseOn(tid)

        }) { response ->

            log("Remote release response $response")

            requestRemoteReleaseOff(session)

        }
    }

    private fun startCapture(session: PtpSession) {

        log("Starting capture")

        session.transaction({ tid ->

            session.writeStartShootingMode(tid)

        }) { response ->

            log("Capture response $response")

            requestRemoteReleaseOn(session)

        }
    }

    private fun verifyEventResponse(chanMan: ChannelBufferManager): Boolean {

        chanMan.channel.socket().getOutputStream().flush()

        val length = chanMan.getInt()

        val response = chanMan.getBytes(length - Int.SIZE_BYTES)

        val data = response.toInt()

        return data == EVENT_RESPONSE_OK

    }

    fun startSingleShotCapture(unit: RangeObject) {

        capturing = true

        obsUnit = unit

    }

    private fun initializeHandleCache(session: PtpSession, storageId: ByteArray) {

        log("Initializing image handles")

        var newHandles: Array<ByteArray>? = null

        session.transaction({ tid ->

            session.writeGetObjectHandles(storageId, tid)

            val size = awaitStartPacket(session)

            val handles = awaitEndPacketHandles(session)

            newHandles = handles.toTypedArray()

        }) { response ->

            if (response) {

                log("Found handles: ${newHandles?.toList()}")

                newHandles?.forEach { handle ->

                    val handleInteger = handle.toInt()

                    handles[handleInteger] = handle
                }

                writeLiveViewParameters(session, storageId)
            }
        }
    }

    private fun requestRemoteMode(session: PtpSession) {

        log("Requesting remote mode")

        session.transaction({ tid ->

            session.writeRemoteMode(tid)

        }) { response ->

            if (response) {

                log("Remote mode responded with OK")

                requestEventMode(session)
            }
        }
    }

    private fun requestStorageIds(session: PtpSession) {

        log("Requesting storage IDs")

        var storageId: ByteArray? = null

        session.transaction({ tid ->

            session.writeGetStorageIds(tid)

            var size = awaitStartPacket(session)

            val storageIds = awaitEndDataHandlesPacket(session)

            storageId = storageIds.first()

        }) { response ->

            if (response && storageId != null) {

                log("Found storage id: ${storageId?.toList()}")

                initializeHandleCache(session, storageId!!)

            }
        }
    }

    private fun requestEventMode(session: PtpSession) {

        log("Requesting event mode")

        session.transaction({ tid ->

            session.writeEventMode(tid)

        }) { response ->

            if (response) {

                log("Event mode responded with OK")

                requestStorageIds(session)
            }
        }
    }

    private fun awaitStartPacket(session: PtpSession): Int {

        var (length, packetType) = readDataPackets(session)

        while (packetType == PACKET_TYPE_RESPONSE) {
            val (l, p) = readDataPackets(session)
            length = l
            packetType = p
        }

        return length
    }

    private fun awaitEndDataHandlesPacket(session: PtpSession): List<ByteArray> {

        val handles = arrayListOf<ByteArray>()

        val endDataPacketSize = session.comMan.getInt()

        val packetType = session.comMan.getInt()

        val tid = session.comMan.getInt()

        val numStorageIds = session.comMan.getInt()

        for (i in 0..<numStorageIds) {

            handles.add(session.comMan.getInt().toByteArray())

        }

        return handles
    }

    private fun awaitEndPacketHandles(session: PtpSession): List<ByteArray> {

        val handles = arrayListOf<ByteArray>()

        val endDataPacketSize = session.comMan.getInt()

        val packetType = session.comMan.getInt()

        val tid = session.comMan.getInt()

        val numStorageIds = session.comMan.getInt()

        for (i in 0..<numStorageIds) {

            handles.add(session.comMan.getInt().toByteArray())

        }

        return handles
    }


    private fun readDataPackets(session: PtpSession): Pair<Int, Int> {

        val length = session.comMan.getInt()

        val packetType = session.comMan.getInt()

        if (packetType == PACKET_TYPE_RESPONSE) {

            val op = session.comMan.getShort()

            val tid = session.comMan.getInt()

        } else if (packetType == PACKET_TYPE_START_DATA) {

            val tid = session.comMan.getInt()

            val totalDataLength = session.comMan.getLong().toInt()

            return totalDataLength to packetType

        } else if (packetType == PACKET_TYPE_DATA) {

            val tid = session.comMan.getInt()

            val totalBytesToRead = length - 12 //+ final - 12 + 10

            val reader = session.comMan.channel.socket().getInputStream()

            val data = ByteArray(totalBytesToRead)
            var index = 0
            while (index < totalBytesToRead) {

                val bytes = reader.read(data, index, totalBytesToRead - index)

                index += bytes
            }

            if (totalBytesToRead >= IMAGE_SIZE) {

                val bmp = try {
                    BitmapFactory.decodeStream(ByteArrayInputStream(data))
                } catch (e: Exception) {
                    null
                }

                if (bmp != null) session.callbacks?.onPreview(bmp)
            }

        } else if (packetType == PACKET_TYPE_END_DATA) {

            readEventData(session)

        }

        return length to packetType
    }

    private fun awaitDataPacket(session: PtpSession): Pair<Int, Int> {

        val endDataPacketSize = session.comMan.getInt()

        val packetTypeInt = session.comMan.getInt()

        val tid = session.comMan.getInt()

        if (packetTypeInt == PACKET_TYPE_DATA) {

            val totalBytesToRead = endDataPacketSize - 12
            val reader = session.comMan.channel.socket().getInputStream()

            val data = ByteArray(totalBytesToRead)
            var index = 0
            while (index < totalBytesToRead) {

                val bytes = reader.read(data, index, totalBytesToRead - index)

                index += bytes
            }

            if (totalBytesToRead >= IMAGE_SIZE) {

                val bmp = try {
                    BitmapFactory.decodeStream(ByteArrayInputStream(data))
                } catch (e: Exception) {
                    null
                }

                if (bmp != null) session.callbacks?.onPreview(bmp)

            }

        } else if (packetTypeInt == 12) {

            session.comMan.getInt()

            session.comMan.getInt()

        } else {

            log("Something went wrong...debugging to log file")

            if (isDebug) {

                session.debugBuffer(context)

            }

            throw Exception()
        }

        return Pair(endDataPacketSize, packetTypeInt)
    }

    private fun awaitImageEndPacket(session: PtpSession): Pair<Int, ByteArray> {

        var data = byteArrayOf()

        val endDataPacketSize = session.comMan.getInt()

        val packetTypeInt = session.comMan.getInt()

        val tid = session.comMan.getInt()

        val totalBytesToRead = endDataPacketSize - 12

        if (packetTypeInt == PACKET_TYPE_END_DATA) {

            data = session.comMan.getBitmap(totalBytesToRead)

        } else {

            log("Something went wrong... starting debug log")

            if (isDebug) {

                session.debugBuffer(context)

            }

            throw Exception()
        }

        return packetTypeInt to data
    }
}