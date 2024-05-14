package com.fieldbook.tracker.devices.ptpip

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun Long.toByteArray() = ByteBuffer.allocate(Long.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putLong(this).array()
fun Int.toByteArray() = ByteBuffer.allocate(UInt.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(this).array() //.array().joinToString(",") {  "0x%02x".format(it) })
fun Short.toByteArray() = ByteBuffer.allocate(Short.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putShort(this).array()
fun ByteArray.toInt(): Int = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).getInt()
fun ByteArray.toShort(): Short = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).getShort()
fun ByteArray.toLong(): Long = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).getLong()
fun ByteBuffer.toInt() = array().toInt()
fun ByteBuffer.toShort() = array().toShort()
fun ByteBuffer.toLong() = array().toLong()