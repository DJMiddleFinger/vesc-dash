package com.vescdash.vesc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProtocolTest {

    @Test
    fun crcMatchesXmodemCheckValue() {
        assertEquals(0x31C3, Crc16.compute("123456789".toByteArray()))
    }

    @Test
    fun encodeDecodeRoundTrip() {
        for (len in listOf(1, 10, 255, 256, 1000)) {
            val payload = ByteArray(len) { (it * 7).toByte() }
            val decoded = PacketDecoder().feed(VescPacket.encode(payload))
            assertEquals("len $len", 1, decoded.size)
            assertArrayEquals(payload, decoded[0])
        }
    }

    @Test
    fun decoderReassemblesSplitPacketsAndSkipsNoise() {
        val a = byteArrayOf(4, 1, 2)
        val b = byteArrayOf(0, 6, 5)
        val noise = byteArrayOf(0x55, 0x03, 0x00, 0x02) // 0x03 header with short length = invalid
        val stream = noise + VescPacket.encode(a) + byteArrayOf(0x7F) + VescPacket.encode(b)

        val decoder = PacketDecoder()
        val out = mutableListOf<ByteArray>()
        stream.toList().chunked(5).forEach { out += decoder.feed(it.toByteArray()) }

        assertEquals(2, out.size)
        assertArrayEquals(a, out[0])
        assertArrayEquals(b, out[1])
    }

    @Test
    fun float32AutoMatchesIeee() {
        assertArrayEquals(byteArrayOf(0x3F, 0x80.toByte(), 0, 0), PayloadWriter().f32Auto(1.0).build())
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), PayloadWriter().f32Auto(1e-40).build())
    }

    @Test
    fun setMcconfTempLayout() {
        val limits = TempLimits(0.5, 0.8, -40000.0, 40000.0, 0.005, 0.95, -1.5e6, 1.5e6, -10.0, 30.0)
        val p = VescProtocol.setMcconfTemp(limits, forwardCan = true)
        assertEquals(1 + 4 + 10 * 4, p.size)
        assertEquals(CommPacketId.SET_MCCONF_TEMP, p[0].toInt())
        val r = PayloadReader(p, 1)
        assertEquals(0, r.u8()) // store
        assertEquals(1, r.u8()) // forward can
        assertEquals(1, r.u8()) // ack
        assertEquals(1, r.u8()) // divide by controllers
        assertEquals(0.5f, r.f32Auto())
        assertEquals(0.8f, r.f32Auto())
        assertEquals(-40000f, r.f32Auto())
    }

    @Test
    fun parsesGetValues() {
        val p = PayloadWriter()
            .u8(CommPacketId.GET_VALUES)
            .i16(452) // fet 45.2 C
            .i16(381) // motor 38.1 C
            .i32(1234) // motor current 12.34 A
            .i32(-250) // input current -2.5 A
            .i32(0).i32(0) // id, iq
            .i16(500) // duty 0.5
            .i32(12000) // erpm
            .i16(504) // 50.4 V
            .i32(12345) // 1.2345 Ah
            .i32(0)
            .i32(250000) // 25 Wh
            .i32(0)
            .i32(100) // tacho
            .i32(5000) // tacho abs
            .u8(0) // fault
            .i32(0) // pid pos
            .u8(3) // controller id
            .build()

        val parsed = VescProtocol.parseValues(p)
        assertNotNull(parsed)
        val v = parsed!!
        assertEquals(45.2, v.tempFet, 1e-9)
        assertEquals(12.34, v.currentMotor, 1e-9)
        assertEquals(-2.5, v.currentIn, 1e-9)
        assertEquals(0.5, v.duty, 1e-9)
        assertEquals(12000.0, v.erpm, 1e-9)
        assertEquals(50.4, v.voltage, 1e-9)
        assertEquals(25.0, v.wattHours, 1e-9)
        assertEquals(5000, v.tachometerAbs)
        assertEquals(3, v.controllerId)
    }
}
