package com.fieldbook.tracker.devices.ptpip

import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

class ChannelBufferManager(val channel: SocketChannel) {

    private val longBuffer = ByteBuffer.allocate(Long.SIZE_BYTES)

    private val intBuffer = ByteBuffer.allocate(Int.SIZE_BYTES)

    private val shortBuffer = ByteBuffer.allocate(Short.SIZE_BYTES)

    fun getInt(): Int {

        channel.read(intBuffer)

        val value = intBuffer.toInt()

        intBuffer.clear()

        return value
    }

    fun getShort(): Int {

        channel.read(shortBuffer)

        val value = shortBuffer.toInt()

        shortBuffer.clear()

        return value
    }

    fun getLong(): Long {

        channel.read(longBuffer)

        val value = longBuffer.toLong()

        longBuffer.clear()

        return value
    }

    fun getBytes(length: Int): ByteArray {

        val data = ByteBuffer.allocate(length)

        channel.read(data)

        val bytes = data.array().copyOf()

        data.clear()

        return bytes
    }

    fun getBitmap(length: Int): ByteArray {

        val reader = channel.socket().getInputStream()

        val data = ByteArray(length)
        var index = 0
        while (index < length) {

            val bytes = reader.read(data, index, length - index)

            index += bytes
        }

        return data
    }

}