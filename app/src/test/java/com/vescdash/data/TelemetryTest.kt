package com.vescdash.data

import com.vescdash.vesc.VescValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelemetryTest {
    private fun values(tempFet: Double, tempMotor: Double) = VescValues(
        tempFet = tempFet, tempMotor = tempMotor, currentMotor = 0.0, currentIn = 0.0, duty = 0.0,
        erpm = 0.0, voltage = 48.0, ampHours = 0.0, ampHoursCharged = 0.0, wattHours = 0.0,
        wattHoursCharged = 0.0, tachometerAbs = 0, fault = 0, controllerId = 0,
    )

    @Test
    fun zeroMeansNoSensor() {
        // Motor sensor type "Disabled" reports exactly 0.0
        val t = Telemetry.from(values(tempFet = 34.5, tempMotor = 0.0), null, 0L)
        assertEquals(34.5, t.tempFet!!, 1e-9)
        assertNull(t.tempMotor)
    }

    @Test
    fun unpluggedThermistorIsIgnored() {
        assertNull(sensorTemp(-120.0))
        assertNull(sensorTemp(400.0))
        assertEquals(-5.2, sensorTemp(-5.2)!!, 1e-9)
    }

    @Test
    fun dualControllerUsesWhicheverHasAReading() {
        val t = Telemetry.from(values(40.0, 0.0), values(45.0, 61.3), 0L)
        assertEquals(45.0, t.tempFet!!, 1e-9)
        assertEquals(61.3, t.tempMotor!!, 1e-9)
    }
}
