package com.vescdash.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("vescdash")

/** How everything is stored; shared so tests can round-trip models the same way. */
internal val settingsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
}

/** How a connecting controller relates to the settings currently loaded. */
enum class ControllerMatch { FIRST, ACTIVE, KNOWN, NEW }

/**
 * Parks [working] under [from] (the controller that owns it, if any), then returns the saved map and
 * what [to] should show: its saved profile, or for a controller not seen before a copy of [working]
 * (or defaults, with [fresh]). Phone-wide settings such as the appearance carry over either way.
 */
internal fun switchProfile(
    saved: Map<String, Profile>,
    from: String?,
    working: Profile,
    to: String,
    name: String,
    fresh: Boolean,
): Pair<Map<String, Profile>, Profile> {
    val parked = if (from == null) saved else saved + (from to working)
    val shown = parked[to]?.let { it.copy(vehicle = it.vehicle.keepingAppSettings(working.vehicle)) }
        ?: if (fresh) Profile(name, VehicleSettings().keepingAppSettings(working.vehicle)) else working.copy(name = name)
    return (parked + (to to shown)) to shown
}

class SettingsStore(private val context: Context) {
    private object Keys {
        val VEHICLE = stringPreferencesKey("vehicle")
        val MODES = stringPreferencesKey("modes")
        val DASHBOARDS = stringPreferencesKey("dashboards")
        val RIDE_LAYOUT = stringPreferencesKey("ride_layout")
        val ACTIVE_MODE = stringPreferencesKey("active_mode")
        val LAST_DEVICE = stringPreferencesKey("last_device")
        /** Saved snapshots per controller; the live settings sit in the keys above, owned by ACTIVE_PROFILE. */
        val PROFILES = stringPreferencesKey("profiles")
        val ACTIVE_PROFILE = stringPreferencesKey("active_profile")
    }

    val vehicle: Flow<VehicleSettings> = context.dataStore.data.map { readVehicle(it) }
    val modes: Flow<List<DriveMode>> = context.dataStore.data.map { readModes(it) }
    val dashboards: Flow<List<Dashboard>> = context.dataStore.data.map { readDashboards(it) }
    val rideLayout: Flow<RideLayout> = context.dataStore.data.map { readRideLayout(it) }
    val activeModeId: Flow<String?> = context.dataStore.data.map { it[Keys.ACTIVE_MODE] }
    val lastDevice: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_DEVICE] }
    val profileName: Flow<String?> = context.dataStore.data.map { p -> p[Keys.ACTIVE_PROFILE]?.let { readProfiles(p)[it]?.name } }

    suspend fun matchController(key: String): ControllerMatch {
        val p = context.dataStore.data.first()
        val active = p[Keys.ACTIVE_PROFILE]
        return when {
            active == null -> ControllerMatch.FIRST
            active == key -> ControllerMatch.ACTIVE
            key in readProfiles(p) -> ControllerMatch.KNOWN
            else -> ControllerMatch.NEW
        }
    }

    /** Makes [key] the controller the live settings belong to; returns what they now hold. */
    suspend fun activateProfile(key: String, name: String, fresh: Boolean = false): Profile {
        var shown = Profile(name)
        context.dataStore.edit { p ->
            val saved = readProfiles(p)
            val from = p[Keys.ACTIVE_PROFILE]
            val working = Profile(saved[from]?.name ?: name, readVehicle(p), readModes(p), p[Keys.ACTIVE_MODE])
            val (next, now) = switchProfile(saved, from, working, key, name, fresh)
            shown = now
            p[Keys.PROFILES] = settingsJson.encodeToString(next)
            p[Keys.ACTIVE_PROFILE] = key
            p[Keys.VEHICLE] = settingsJson.encodeToString(now.vehicle)
            p[Keys.MODES] = settingsJson.encodeToString(now.modes)
            if (now.activeModeId != null) p[Keys.ACTIVE_MODE] = now.activeModeId else p.remove(Keys.ACTIVE_MODE)
        }
        return shown
    }

    suspend fun renameProfile(name: String) {
        context.dataStore.edit { p ->
            val key = p[Keys.ACTIVE_PROFILE] ?: return@edit
            val saved = readProfiles(p)
            val old = saved[key] ?: Profile(name, readVehicle(p), readModes(p), p[Keys.ACTIVE_MODE])
            p[Keys.PROFILES] = settingsJson.encodeToString(saved + (key to old.copy(name = name)))
        }
    }

    suspend fun updateVehicle(f: (VehicleSettings) -> VehicleSettings) {
        context.dataStore.edit { it[Keys.VEHICLE] = settingsJson.encodeToString(f(readVehicle(it))) }
    }

    suspend fun updateModes(f: (List<DriveMode>) -> List<DriveMode>) {
        context.dataStore.edit { it[Keys.MODES] = settingsJson.encodeToString(f(readModes(it))) }
    }

    suspend fun updateDashboards(f: (List<Dashboard>) -> List<Dashboard>) {
        context.dataStore.edit { it[Keys.DASHBOARDS] = settingsJson.encodeToString(f(readDashboards(it))) }
    }

    suspend fun updateRideLayout(f: (RideLayout) -> RideLayout) {
        context.dataStore.edit { it[Keys.RIDE_LAYOUT] = settingsJson.encodeToString(f(readRideLayout(it))) }
    }

    suspend fun setActiveMode(id: String) {
        context.dataStore.edit { it[Keys.ACTIVE_MODE] = id }
    }

    suspend fun setLastDevice(address: String) {
        context.dataStore.edit { it[Keys.LAST_DEVICE] = address }
    }

    private fun readProfiles(p: Preferences): Map<String, Profile> =
        p[Keys.PROFILES]?.let { s -> runCatching { settingsJson.decodeFromString<Map<String, Profile>>(s) }.getOrNull() }
            ?: emptyMap()

    private fun readVehicle(p: Preferences): VehicleSettings =
        p[Keys.VEHICLE]?.let { s -> runCatching { settingsJson.decodeFromString<VehicleSettings>(s) }.getOrNull() }
            ?: VehicleSettings()

    private fun readModes(p: Preferences): List<DriveMode> =
        p[Keys.MODES]?.let { s -> runCatching { settingsJson.decodeFromString<List<DriveMode>>(s) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() } ?: Defaults.modes

    private fun readDashboards(p: Preferences): List<Dashboard> =
        p[Keys.DASHBOARDS]?.let { s -> runCatching { settingsJson.decodeFromString<List<Dashboard>>(s) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() } ?: Defaults.dashboards

    private fun readRideLayout(p: Preferences): RideLayout =
        p[Keys.RIDE_LAYOUT]?.let { s -> runCatching { settingsJson.decodeFromString<RideLayout>(s) }.getOrNull() }
            ?: Defaults.rideLayout
}
