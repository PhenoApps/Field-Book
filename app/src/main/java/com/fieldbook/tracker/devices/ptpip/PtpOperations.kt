package com.fieldbook.tracker.devices.ptpip

import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

class PtpOperations {

    companion object {

        fun writeOperation(command: SocketChannel, packetType: Int, dataPhase: Int, opCode: Short, tid: Int, params: ByteArray) {

            val packet = packetType.toByteArray()
            val data = dataPhase.toByteArray()
            val op = opCode.toByteArray()
            val id = tid.toByteArray()

            val length = (Int.SIZE_BYTES + packet.size + data.size + op.size + id.size + params.size).toByteArray()

            val payload = ByteBuffer.wrap(
                length
                        + packet
                        + data
                        + op
                        + id
                        + params
            )

            command.write(payload)

            command.socket().getOutputStream().flush()
        }
    }
}