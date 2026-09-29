package com.vescdash.vesc

/** Subset of COMM_PACKET_ID from the VESC firmware (datatypes.h). */
object CommPacketId {
    const val FW_VERSION = 0
    const val GET_VALUES = 4
    const val FORWARD_CAN = 34
    const val SET_MCCONF_TEMP = 48
}

data class FwVersion(val major: Int, val minor: Int, val hardware: String) {
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

    fun parseFwVersion(p: ByteArray): FwVersion? = try {
        val r = PayloadReader(p, 1)
        val major = r.u8()
        val minor = r.u8()
        val hw = if (r.remaining > 0) r.cString() else ""
        FwVersion(major, minor, hw)
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
