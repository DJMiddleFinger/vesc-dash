package com.vescdash.vesc

/** Subset of COMM_PACKET_ID from the VESC firmware (datatypes.h). */
object CommPacketId {
    const val FW_VERSION = 0
    const val GET_VALUES = 4
    const val GET_APPCONF = 17
    const val FORWARD_CAN = 34
    const val SET_MCCONF_TEMP = 48
    const val SET_APPCONF_NO_STORE = 149
}

/** [uuid] is the controller's 12-byte chip id as hex, when the firmware reports it. */
data class FwVersion(val major: Int, val minor: Int, val hardware: String, val uuid: String? = null) {
    override fun toString() = "$major.${minor.toString().padStart(2, '0')}" + if (hardware.isNotBlank()) " · $hardware" else ""
}

/** One COMM_GET_VALUES reply. */
data class VescValues(
    val tempFet: Double,
    val tempMotor: Double,
    val currentMotor: Double,
    val currentIn: Double,
    val duty: Double,
    val erpm: Double,
    val voltage: Double,
    val ampHours: Double,
    val ampHoursCharged: Double,
    val wattHours: Double,
    val wattHoursCharged: Double,
    val tachometerAbs: Int,
    val fault: Int,
    val controllerId: Int,
)

/**
 * Limits sent with COMM_SET_MCCONF_TEMP. These are applied live and — with store=false —
 * are NOT written to flash, so a power cycle restores the VESC Tool configuration.
 */
data class TempLimits(
    val currentMinScale: Double,
    val currentMaxScale: Double,
    val erpmMin: Double,
    val erpmMax: Double,
    val dutyMin: Double,
    val dutyMax: Double,
    val wattMin: Double,
    val wattMax: Double,
    val batteryCurrentMin: Double,
    val batteryCurrentMax: Double,
)

object VescProtocol {
    fun fwVersion(): ByteArray = byteArrayOf(CommPacketId.FW_VERSION.toByte())
    fun getValues(): ByteArray = byteArrayOf(CommPacketId.GET_VALUES.toByte())

    /** Wraps a command so the connected VESC relays it over CAN to controller [canId]. */
    fun forwardCan(canId: Int, inner: ByteArray): ByteArray =
        PayloadWriter().u8(CommPacketId.FORWARD_CAN).u8(canId).bytes(inner).build()

    fun setMcconfTemp(
        l: TempLimits,
        forwardCan: Boolean,
        store: Boolean = false,
        ack: Boolean = true,
        divideByControllers: Boolean = true,
    ): ByteArray = PayloadWriter()
        .u8(CommPacketId.SET_MCCONF_TEMP)
        .bool(store)
        .bool(forwardCan)
        .bool(ack)
        .bool(divideByControllers)
        .f32Auto(l.currentMinScale)
        .f32Auto(l.currentMaxScale)
        .f32Auto(l.erpmMin)
        .f32Auto(l.erpmMax)
        .f32Auto(l.dutyMin)
        .f32Auto(l.dutyMax)
        .f32Auto(l.wattMin)
        .f32Auto(l.wattMax)
        .f32Auto(l.batteryCurrentMin)
        .f32Auto(l.batteryCurrentMax)
        .build()

    fun getAppconf(): ByteArray = byteArrayOf(CommPacketId.GET_APPCONF.toByte())

    // App-config layout (offsets into the serialized blob) shared by firmware 6.00, 6.02, 6.05 and master.
    // ponytail: ADC app only, and only these firmware signatures; add a signature when its layout is checked.
    private val APPCONF_SIGNATURES = setOf(486554156L, 2099347128L, 296593100L)
    private val ADC_APPS = setOf(2, 5) // app_to_use: ADC, ADC + UART
    private const val APP_TO_USE = 33
    private const val ADC_THROTTLE_EXP = 114
    private const val ADC_RAMP_UP = 123
    private const val ADC_RAMP_DOWN = 127

    /**
     * Turns a COMM_GET_APPCONF [reply] into a COMM_SET_APPCONF_NO_STORE (RAM only, like the mode
     * limits) with the ADC throttle curve exponent and ramp times replaced. Null when the firmware
     * layout isn't recognised or the ADC app isn't in use, so nothing unknown is ever written.
     */
    fun adcThrottleSet(reply: ByteArray, exp: Double, rampUpS: Double, rampDownS: Double): ByteArray? {
        if (reply.size < 1 + ADC_RAMP_DOWN + 4) return null
        val signature = PayloadReader(reply, 1).i32().toLong() and 0xFFFFFFFFL
        if (signature !in APPCONF_SIGNATURES || (reply[1 + APP_TO_USE].toInt() and 0xFF) !in ADC_APPS) return null
        val out = reply.copyOf()
        out[0] = CommPacketId.SET_APPCONF_NO_STORE.toByte()
        fun put(at: Int, v: Double) = PayloadWriter().f32Auto(v).build().copyInto(out, 1 + at)
        put(ADC_THROTTLE_EXP, exp)
        put(ADC_RAMP_UP, rampUpS)
        put(ADC_RAMP_DOWN, rampDownS)
        return out
    }

    fun parseFwVersion(p: ByteArray): FwVersion? = try {
        val r = PayloadReader(p, 1)
        val major = r.u8()
        val minor = r.u8()
        val hw = if (r.remaining > 0) r.cString() else ""
        val uuid = if (r.remaining >= 12) ByteArray(12) { r.u8().toByte() }.joinToString("") { "%02X".format(it) } else null
        FwVersion(major, minor, hw, uuid)
    } catch (e: IndexOutOfBoundsException) {
        null
    }

    fun parseValues(p: ByteArray): VescValues? = try {
        val r = PayloadReader(p, 1)
        val tempFet = r.f16(10.0)
        val tempMotor = r.f16(10.0)
        val currentMotor = r.f32(100.0)
        val currentIn = r.f32(100.0)
        r.f32(100.0) // id
        r.f32(100.0) // iq
        val duty = r.f16(1000.0)
        val erpm = r.f32(1.0)
        val voltage = r.f16(10.0)
        val ah = r.f32(1e4)
        val ahCharged = r.f32(1e4)
        val wh = r.f32(1e4)
        val whCharged = r.f32(1e4)
        r.i32() // tachometer (signed); only the absolute count is used
        val tachoAbs = r.i32()
        val fault = r.u8()
        var controllerId = -1
        if (r.remaining >= 5) {
            r.f32(1e6) // pid pos
            controllerId = r.u8()
        }
        VescValues(
            tempFet, tempMotor, currentMotor, currentIn, duty, erpm, voltage,
            ah, ahCharged, wh, whCharged, tachoAbs, fault, controllerId,
        )
    } catch (e: IndexOutOfBoundsException) {
        null
    }

    private val faultNames = listOf(
        "NONE", "OVER_VOLTAGE", "UNDER_VOLTAGE", "DRV", "ABS_OVER_CURRENT", "OVER_TEMP_FET",
        "OVER_TEMP_MOTOR", "GATE_DRIVER_OVER_VOLTAGE", "GATE_DRIVER_UNDER_VOLTAGE", "MCU_UNDER_VOLTAGE",
        "BOOTING_FROM_WATCHDOG_RESET", "ENCODER_SPI", "ENCODER_SINCOS_BELOW_MIN_AMPLITUDE",
        "ENCODER_SINCOS_ABOVE_MAX_AMPLITUDE", "FLASH_CORRUPTION", "HIGH_OFFSET_CURRENT_SENSOR_1",
        "HIGH_OFFSET_CURRENT_SENSOR_2", "HIGH_OFFSET_CURRENT_SENSOR_3", "UNBALANCED_CURRENTS", "BRK",
        "RESOLVER_LOT", "RESOLVER_DOS", "RESOLVER_LOS", "FLASH_CORRUPTION_APP_CFG",
        "FLASH_CORRUPTION_MC_CFG", "ENCODER_NO_MAGNET", "ENCODER_MAGNET_TOO_STRONG", "PHASE_FILTER",
        "ENCODER_FAULT", "LV_OUTPUT_FAULT",
    )

    fun faultName(code: Int): String = faultNames.getOrNull(code)?.replace('_', ' ') ?: "FAULT $code"
}
