package com.vescdash.vesc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun fwVersionCarriesTheControllerUuid() {
        val uuid = ByteArray(12) { (0xA0 + it).toByte() }
        val p = PayloadWriter().u8(CommPacketId.FW_VERSION).u8(6).u8(5).bytes("HW\u0000".toByteArray()).bytes(uuid).u8(0).build()
        val fw = VescProtocol.parseFwVersion(p)!!
        assertEquals("HW", fw.hardware)
        assertEquals("A0A1A2A3A4A5A6A7A8A9AAAB", fw.uuid)
        assertNull(VescProtocol.parseFwVersion(byteArrayOf(0, 6, 5, 'H'.code.toByte(), 0))!!.uuid)
    }

    /** A COMM_GET_APPCONF reply: [id][signature][...], filled with a pattern so untouched bytes are noticed. */
    private fun appconfReply(signature: Long, app: Int) = ByteArray(150) { (it * 7).toByte() }.also { b ->
        b[0] = CommPacketId.GET_APPCONF.toByte()
        for (i in 0..3) b[1 + i] = (signature shr (24 - 8 * i)).toByte()
        b[1 + 33] = app.toByte()
    }

    @Test
    fun adcThrottleSetChangesOnlyTheThreeFields() {
        val reply = appconfReply(486554156L, app = 2)
        val out = VescProtocol.adcThrottleSet(reply, -0.5, 0.2, 0.1)!!
        assertEquals(CommPacketId.SET_APPCONF_NO_STORE, out[0].toInt() and 0xFF)
        assertEquals(-0.5f, PayloadReader(out, 1 + 114).f32Auto())
        assertEquals(0.2f, PayloadReader(out, 1 + 123).f32Auto())
        assertEquals(0.1f, PayloadReader(out, 1 + 127).f32Auto())
        val patched = (115 until 119) + (124 until 128) + (128 until 132)
        for (i in 1 until out.size) if (i !in patched) assertEquals("byte $i", reply[i], out[i])
    }

    @Test
    fun adcThrottleSetRefusesWhatItDoesNotKnow() {
        assertNull(VescProtocol.adcThrottleSet(appconfReply(1234L, app = 2), 0.0, 0.3, 0.1)) // unknown firmware layout
        assertNull(VescProtocol.adcThrottleSet(appconfReply(2099347128L, app = 1), 0.0, 0.3, 0.1)) // PPM, not ADC
        assertNull(VescProtocol.adcThrottleSet(ByteArray(10), 0.0, 0.3, 0.1)) // truncated reply
    }
}
