package com.vescdash.vesc

import java.io.ByteArrayOutputStream

/**
 * VESC framing:
 *   short:  [0x02][len:1]        [payload][crc:2][0x03]   (len <= 255)
 *   long:   [0x03][len:2]        [payload][crc:2][0x03]   (len <= 65535)
 */
object VescPacket {
    fun encode(payload: ByteArray): ByteArray {
        val len = payload.size
        val out = ByteArrayOutputStream(len + 8)
        require(len <= 0xFFFF) { "VESC payloads are at most 64 KB" }
        if (len <= 0xFF) {
            out.write(2)
            out.write(len)
        } else {
            out.write(3)
            out.write(len ushr 8)
            out.write(len)
        }
        out.write(payload, 0, len)
        val crc = Crc16.compute(payload)
        out.write(crc ushr 8)
        out.write(crc)
        out.write(3)
        return out.toByteArray()
    }
}

/**
 * Reassembles packets from a byte stream that may arrive split across many BLE
 * notifications (or with junk in between). Feed it raw bytes, get whole payloads back.
 */
class PacketDecoder(private val maxPayload: Int = 4096) {
    private var buf = ByteArray(2048)
    private var size = 0

    @Synchronized
    fun reset() {
        size = 0
    }

    @Synchronized
    fun feed(data: ByteArray): List<ByteArray> {
        if (size + data.size > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + data.size))
        System.arraycopy(data, 0, buf, size, data.size)
        size += data.size

        val packets = ArrayList<ByteArray>(1)
        var pos = 0
        while (pos < size) {
            val lenBytes = when (buf[pos].toInt()) {
                2 -> 1
                3 -> 2
                else -> { pos++; continue }
            }
            val header = 1 + lenBytes
            if (size - pos < header) break

            var len = 0
            for (i in 1..lenBytes) len = (len shl 8) or (buf[pos + i].toInt() and 0xFF)
            // A long header carrying a short length is never produced by VESC -> treat as noise.
            val plausible = if (lenBytes == 1) len > 0 else len in 256..maxPayload
            if (!plausible) { pos++; continue }

            val total = header + len + 3
            if (size - pos < total) break

            val start = pos + header
            val crc = ((buf[start + len].toInt() and 0xFF) shl 8) or (buf[start + len + 1].toInt() and 0xFF)
            if (buf[start + len + 2].toInt() == 3 && crc == Crc16.compute(buf, start, len)) {
                packets += buf.copyOfRange(start, start + len)
                pos += total
            } else {
                pos++
            }
        }

        if (pos > 0) {
            System.arraycopy(buf, pos, buf, 0, size - pos)
            size -= pos
        }
        if (size > maxPayload * 2) size = 0 // hopelessly out of sync, start fresh
        return packets
    }
}
