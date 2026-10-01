package com.vescdash.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileTest {
    private val phone = VehicleSettings(appearance = AppAppearance.STARK, rideStyle = RideStyle.TILES, imperial = true)
    private val a = Profile("A", phone.copy(wheelDiameterMm = 300.0), listOf(DriveMode(id = "a1", name = "Eco", color = 0)), "a1")

    @Test
    fun switchingControllersSwapsVehicleAndModesButKeepsPhoneSettings() {
        val (saved, b) = switchProfile(emptyMap(), "A", a, "B", "B", fresh = true)
        assertEquals(254.0, b.vehicle.wheelDiameterMm, 0.0)
        assertEquals(Defaults.modes, b.modes)
        assertEquals(AppAppearance.STARK, b.vehicle.appearance)
        assertEquals(RideStyle.TILES, b.vehicle.rideStyle)

        val (_, back) = switchProfile(saved, "B", b.copy(vehicle = b.vehicle.copy(wheelDiameterMm = 200.0)), "A", "A", fresh = false)
        assertEquals(300.0, back.vehicle.wheelDiameterMm, 0.0)
        assertEquals("a1", back.activeModeId)
        assertEquals(listOf("Eco"), back.modes.map { it.name })
    }

    @Test
    fun aNewControllerCanStartFromACopyAndTheFirstOneClaimsWhatIsLoaded() {
        val (saved, b) = switchProfile(emptyMap(), "A", a, "B", "Bee", fresh = false)
        assertEquals(a.vehicle, b.vehicle)
        assertEquals("Bee", b.name)
        assertEquals(setOf("A", "B"), saved.keys)

        val (claimed, first) = switchProfile(emptyMap(), null, a, "X", "X", fresh = false)
        assertEquals(a.vehicle, first.vehicle)
        assertEquals(setOf("X"), claimed.keys)
    }

    @Test
    fun savedProfilesSurviveAJsonRoundTrip() {
        val (saved, _) = switchProfile(emptyMap(), "A", a, "B", "B", fresh = true)
        assertEquals(saved, settingsJson.decodeFromString<Map<String, Profile>>(settingsJson.encodeToString(saved)))
    }

    @Test
    fun modesSavedByAnOlderVersionStillDecode() {
        val m = settingsJson.decodeFromString<DriveMode>("""{"id":"x","name":"Old","color":4278190080,"powerPct":50,"regenPct":40}""")
        assertNull(m.batteryPct)
        assertNull(m.throttleExp)
        assertEquals(50, m.powerPct)
    }
}
