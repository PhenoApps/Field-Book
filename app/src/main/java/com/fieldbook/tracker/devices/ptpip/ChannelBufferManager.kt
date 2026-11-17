package com.fieldbook.tracker.devices.ptpip

import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

class ChannelBufferManager(val channel: SocketChannel) {

    private val longBuffer = ByteBuffer.allocate(Long.SIZE_BYTES)

    private val intBuffer = ByteBuffer.allocate(Int.SIZE_BYTES)

    private val shortBuffer = ByteBuffer.allocate(Short.SIZE_BYTES)

    // helper that ensures the provided ByteBuffer is completely filled from the channel
    @Throws(IOException::class)
    private fun readFully(buffer: ByteBuffer) {
        buffer.clear()
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer)
            if (read == -1) throw EOFException("Unexpected EOF while reading from channel")
        }
        buffer.rewind()
    }

    @Throws(IOException::class)
    fun getInt(): Int {

        readFully(intBuffer)

        val value = intBuffer.toInt()

        intBuffer.clear()

        return value
    }

    @Throws(IOException::class)
    fun getShort(): Int {

        readFully(shortBuffer)

        val value = shortBuffer.toInt()

        shortBuffer.clear()

        return value
    }

    @Throws(IOException::class)
    fun getLong(): Long {

        readFully(longBuffer)

        val value = longBuffer.toLong()

        longBuffer.clear()

        return value
    }

    @Throws(IOException::class)
    fun getBytes(length: Int): ByteArray {

        if (length < 0) throw IOException("Negative length requested: $length")

        if (length == 0) return byteArrayOf()

        val data = ByteArray(length)
        val buf = ByteBuffer.wrap(data)

        while (buf.hasRemaining()) {
            val read = channel.read(buf)
            if (read == -1) throw EOFException("Unexpected EOF while reading $length bytes from channel")
        }

        return data
    }

}