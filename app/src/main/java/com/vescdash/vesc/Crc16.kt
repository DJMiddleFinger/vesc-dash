package com.vescdash.vesc

/** CRC-16/XMODEM (poly 0x1021, init 0) — the checksum VESC appends to every packet. */
object Crc16 {
    private val table = IntArray(256) { i ->
        var crc = i shl 8
        repeat(8) { crc = if ((crc and 0x8000) != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
        crc and 0xFFFF
    }

    fun compute(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        var crc = 0
        for (i in offset until offset + length) {
            val idx = ((crc ushr 8) xor (data[i].toInt() and 0xFF)) and 0xFF
            crc = ((crc shl 8) xor table[idx]) and 0xFFFF
        }
        return crc
    }
}
