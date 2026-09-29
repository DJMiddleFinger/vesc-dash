package com.vescdash.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vescdash.VescDashApp
import com.vescdash.ble.VescBleTransport
import com.vescdash.data.DashWidget
import com.vescdash.data.Dashboard
import com.vescdash.data.Defaults
import com.vescdash.data.DriveMode
import com.vescdash.data.VehicleSettings
import com.vescdash.data.WidgetSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as VescDashApp).repository

    val connection = repo.connection
    val telemetry = repo.telemetry
    val history = repo.history
    val firmware = repo.firmware
    val stale = repo.stale
    val reconnecting = repo.reconnecting
    val modeStatus = repo.modeStatus
    val vehicle = repo.vehicle
    val modes = repo.modes
    val activeModeId = repo.activeModeId
    val dashboards = repo.dashboards
    val lastDevice = repo.lastDevice
    val demoActive = repo.demoActive
    val rideTimeMs = repo.rideTimeMs

    // ---- scanning ---------------------------------------------------------

    private val _scanResults = MutableStateFlow<List<VescBleTransport.Device>>(emptyList())
    val scanResults: StateFlow<List<VescBleTransport.Device>> = _scanResults.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()
    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()
    private var scanJob: Job? = null
    private var scanToken = 0

    fun bluetoothOn(): Boolean = repo.transport.isEnabled

    fun startScan() {
        scanJob?.cancel()
        val token = ++scanToken
        _scanError.value = null
        _scanning.value = true
        scanJob = viewModelScope.launch {
            try {
                withTimeoutOrNull(12_000) {
                    repo.transport.scan().collect { _scanResults.value = it }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _scanError.value = e.message ?: "Scan failed"
            } finally {
                if (token == scanToken) _scanning.value = false
            }
        }
    }

    fun stopScan() {
        scanToken++
        scanJob?.cancel()
        scanJob = null
        _scanning.value = false
    }

    fun connect(address: String) {
        stopScan()
        repo.connect(address)
    }

    fun disconnect() = repo.disconnect()

    // ---- modes ------------------------------------------------------------

    fun selectMode(id: String) = repo.selectMode(id)
    fun saveMode(mode: DriveMode, activate: Boolean) = repo.saveMode(mode, activate)
    fun deleteMode(id: String) = repo.deleteMode(id)
    fun reapplyMode() = repo.reapplyActiveMode()

    /** Creates a new mode and returns its id so the tuner can open it. */
    fun addMode(): String {
        val n = modes.value.size
        val mode = DriveMode(
            name = "Mode ${n + 1}",
            color = Defaults.modeColors[n % Defaults.modeColors.size],
            powerPct = 60,
            regenPct = 50,
        )
        repo.saveMode(mode, activate = false)
        return mode.id
    }

    // ---- vehicle ----------------------------------------------------------

    fun updateVehicle(f: (VehicleSettings) -> VehicleSettings) =
        repo.updateVehicle { f(it).copy(configured = true) }

    // ---- dashboards -------------------------------------------------------

    private fun updateDashboard(id: String, f: (Dashboard) -> Dashboard) =
        repo.updateDashboards { list -> list.map { if (it.id == id) f(it) else it } }

    fun upsertWidget(dashId: String, w: DashWidget) = updateDashboard(dashId) { d ->
        val exists = d.widgets.any { it.id == w.id }
        d.copy(widgets = if (exists) d.widgets.map { if (it.id == w.id) w else it } else d.widgets + w)
    }

    fun deleteWidget(dashId: String, widgetId: String) = updateDashboard(dashId) { d ->
        d.copy(widgets = d.widgets.filterNot { it.id == widgetId })
    }

    fun moveWidget(dashId: String, widgetId: String, delta: Int) = updateDashboard(dashId) { d ->
        val list = d.widgets.toMutableList()
        val i = list.indexOfFirst { it.id == widgetId }
        val j = i + delta
        if (i < 0 || j !in list.indices) d else {
            Collections.swap(list, i, j)
            d.copy(widgets = list)
        }
    }

    fun toggleWidgetSize(dashId: String, widgetId: String) = updateDashboard(dashId) { d ->
        d.copy(
            widgets = d.widgets.map {
                if (it.id != widgetId) it
                else it.copy(size = if (it.size == WidgetSize.FULL) WidgetSize.HALF else WidgetSize.FULL)
            },
        )
    }

    fun addDashboard() = repo.updateDashboards { it + Dashboard(name = "Page ${it.size + 1}", widgets = emptyList()) }

    fun renameDashboard(id: String, name: String) = updateDashboard(id) { it.copy(name = name) }

    fun deleteDashboard(id: String) = repo.updateDashboards { list ->
        if (list.size <= 1) list else list.filterNot { it.id == id }
    }
}
