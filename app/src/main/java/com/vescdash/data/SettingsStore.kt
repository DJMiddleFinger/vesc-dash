package com.vescdash.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("vescdash")

class SettingsStore(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private object Keys {
        val VEHICLE = stringPreferencesKey("vehicle")
        val MODES = stringPreferencesKey("modes")
        val DASHBOARDS = stringPreferencesKey("dashboards")
        val ACTIVE_MODE = stringPreferencesKey("active_mode")
        val LAST_DEVICE = stringPreferencesKey("last_device")
    }

    val vehicle: Flow<VehicleSettings> = context.dataStore.data.map { readVehicle(it) }
    val modes: Flow<List<DriveMode>> = context.dataStore.data.map { readModes(it) }
    val dashboards: Flow<List<Dashboard>> = context.dataStore.data.map { readDashboards(it) }
    val activeModeId: Flow<String?> = context.dataStore.data.map { it[Keys.ACTIVE_MODE] }
    val lastDevice: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_DEVICE] }

    suspend fun updateVehicle(f: (VehicleSettings) -> VehicleSettings) {
        context.dataStore.edit { it[Keys.VEHICLE] = json.encodeToString(f(readVehicle(it))) }
    }

    suspend fun updateModes(f: (List<DriveMode>) -> List<DriveMode>) {
        context.dataStore.edit { it[Keys.MODES] = json.encodeToString(f(readModes(it))) }
    }

    suspend fun updateDashboards(f: (List<Dashboard>) -> List<Dashboard>) {
        context.dataStore.edit { it[Keys.DASHBOARDS] = json.encodeToString(f(readDashboards(it))) }
    }

    suspend fun setActiveMode(id: String) {
        context.dataStore.edit { it[Keys.ACTIVE_MODE] = id }
    }

    suspend fun setLastDevice(address: String) {
        context.dataStore.edit { it[Keys.LAST_DEVICE] = address }
    }

    private fun readVehicle(p: Preferences): VehicleSettings =
        p[Keys.VEHICLE]?.let { s -> runCatching { json.decodeFromString<VehicleSettings>(s) }.getOrNull() }
            ?: VehicleSettings()

    private fun readModes(p: Preferences): List<DriveMode> =
        p[Keys.MODES]?.let { s -> runCatching { json.decodeFromString<List<DriveMode>>(s) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() } ?: Defaults.modes

    private fun readDashboards(p: Preferences): List<Dashboard> =
        p[Keys.DASHBOARDS]?.let { s -> runCatching { json.decodeFromString<List<Dashboard>>(s) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() } ?: Defaults.dashboards
}
