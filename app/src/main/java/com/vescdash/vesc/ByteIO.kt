package com.vescdash.vesc

import java.io.ByteArrayOutputStream
import kotlin.math.abs

/** Big-endian reader matching the firmware's buffer_get_* helpers. */
class PayloadReader(private val data: ByteArray, start: Int = 0) {
    var pos: Int = start
        private set

    val remaining: Int get() = data.size - pos

    fun u8(): Int = data[pos++].toInt() and 0xFF
    fun i16(): Int = ((u8() shl 8) or u8()).toShort().toInt()
    fun i32(): Int = (u8() shl 24) or (u8() shl 16) or (u8() shl 8) or u8()

    /** buffer_get_float16(scale) */
    fun f16(scale: Double): Double = i16() / scale

    /** buffer_get_float32(scale) */
    fun f32(scale: Double): Double = i32() / scale

    /** buffer_get_float32_auto — bit-compatible with IEEE-754 for normal numbers. */
    fun f32Auto(): Float = Float.fromBits(i32())

    fun cString(): String {
        val start = pos
        while (pos < data.size && data[pos] != 0.toByte()) pos++
        val s = String(data, start, pos - start, Charsets.UTF_8)
        if (pos < data.size) pos++ // skip terminator
        return s
    }
}

class PayloadWriter {
    private val out = ByteArrayOutputStream()

    fun u8(v: Int) = apply { out.write(v and 0xFF) }
    fun bool(v: Boolean) = u8(if (v) 1 else 0)
    fun i16(v: Int) = apply { u8(v ushr 8); u8(v) }
    fun i32(v: Int) = apply { u8(v ushr 24); u8(v ushr 16); u8(v ushr 8); u8(v) }

    /** buffer_append_float32_auto. The firmware flushes subnormals to zero, so do the same. */
    fun f32Auto(v: Double) = apply {
        val f = v.toFloat()
        i32(if (abs(f) < 1.5e-38f) 0 else f.toRawBits())
    }

    fun bytes(b: ByteArray) = apply { out.write(b, 0, b.size) }
    fun build(): ByteArray = out.toByteArray()
}
