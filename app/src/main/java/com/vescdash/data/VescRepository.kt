package com.vescdash.data

import android.content.Context
import android.os.SystemClock
import com.vescdash.ble.VescBleTransport
import com.vescdash.vesc.CommPacketId
import com.vescdash.vesc.FwVersion
import com.vescdash.vesc.PacketDecoder
import com.vescdash.vesc.VescPacket
import com.vescdash.vesc.VescProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

sealed interface ModeApplyStatus {
    data object Idle : ModeApplyStatus
    data class Pending(val modeId: String) : ModeApplyStatus
    data class Applying(val modeId: String) : ModeApplyStatus
    data class Applied(val modeId: String) : ModeApplyStatus
    data class Failed(val modeId: String) : ModeApplyStatus
}

/**
 * Owns the VESC link: frames packets, polls telemetry, applies drive modes and
 * reconnects automatically if the link drops mid-ride.
 */
class VescRepository(
    context: Context,
    private val store: SettingsStore,
    private val scope: CoroutineScope,
) {
    val transport = VescBleTransport(context.applicationContext)
    val connection: StateFlow<VescBleTransport.State> = transport.state

    val vehicle: StateFlow<VehicleSettings> = store.vehicle.stateIn(scope, SharingStarted.Eagerly, VehicleSettings())
    val modes: StateFlow<List<DriveMode>> = store.modes.stateIn(scope, SharingStarted.Eagerly, Defaults.modes)
    val activeModeId: StateFlow<String?> = store.activeModeId.stateIn(scope, SharingStarted.Eagerly, null)
    val dashboards: StateFlow<List<Dashboard>> = store.dashboards.stateIn(scope, SharingStarted.Eagerly, Defaults.dashboards)
    val lastDevice: StateFlow<String?> = store.lastDevice.stateIn(scope, SharingStarted.Eagerly, null)

    private val _telemetry = MutableStateFlow<Telemetry?>(null)
    val telemetry: StateFlow<Telemetry?> = _telemetry.asStateFlow()

    private val _history = MutableStateFlow<List<Telemetry>>(emptyList())
    /** Last ~30 s of samples for graph widgets. */
    val history: StateFlow<List<Telemetry>> = _history.asStateFlow()

    private val _firmware = MutableStateFlow<FwVersion?>(null)
    val firmware: StateFlow<FwVersion?> = _firmware.asStateFlow()

    private val _stale = MutableStateFlow(false)
    /** Connected, but the VESC stopped answering. */
    val stale: StateFlow<Boolean> = _stale.asStateFlow()

    private val _reconnecting = MutableStateFlow(false)
    val reconnecting: StateFlow<Boolean> = _reconnecting.asStateFlow()

    private val _modeStatus = MutableStateFlow<ModeApplyStatus>(ModeApplyStatus.Idle)
    val modeStatus: StateFlow<ModeApplyStatus> = _modeStatus.asStateFlow()

    private val _demoActive = MutableStateFlow(false)
    /** Simulated data is playing because demo mode is on and no VESC is connected. */
    val demoActive: StateFlow<Boolean> = _demoActive.asStateFlow()

    private val _rideTimeMs = MutableStateFlow(0L)
    /** Time spent moving since the app started. */
    val rideTimeMs: StateFlow<Long> = _rideTimeMs.asStateFlow()

    private val historyBuf = ArrayDeque<Telemetry>()
    private val batteryEstimator = BatteryEstimator()
    private var demoJob: Job? = null

    private val decoder = PacketDecoder()
    private val payloads = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    private val requestMutex = Mutex()

    @Volatile private var targetAddress: String? = null
    @Volatile private var hadConnection = false
    private var pollJob: Job? = null
    private var reconnectJob: Job? = null

    private val isConnected get() = transport.state.value is VescBleTransport.State.Connected

    init {
        scope.launch {
            transport.incoming.collect { chunk ->
                for (p in decoder.feed(chunk)) payloads.emit(p)
            }
        }
        scope.launch {
            transport.state.collect { s ->
                when (s) {
                    is VescBleTransport.State.Connected -> {
                        hadConnection = true
                        reconnectJob?.cancel()
                        _reconnecting.value = false
                        startPolling()
                    }
                    is VescBleTransport.State.Failed -> {
                        stopPolling()
                        if (hadConnection) scheduleReconnect()
                    }
                    is VescBleTransport.State.Idle -> stopPolling()
                    is VescBleTransport.State.Connecting -> Unit
                }
            }
        }
        scope.launch {
            combine(vehicle, transport.state) { v, s -> v.demoMode && s !is VescBleTransport.State.Connected }
                .distinctUntilChanged()
                .collect { on -> if (on) startDemo() else stopDemo() }
        }
    }

    fun activeMode(): DriveMode? =
        modes.value.firstOrNull { it.id == activeModeId.value } ?: modes.value.firstOrNull()

    // ---- connection -------------------------------------------------------

    fun connect(address: String) {
        targetAddress = address
        hadConnection = false
        reconnectJob?.cancel()
        _reconnecting.value = false
        scope.launch {
            store.setLastDevice(address)
            transport.connect(address)
        }
    }

    fun disconnect() {
        targetAddress = null
        hadConnection = false
        reconnectJob?.cancel()
        _reconnecting.value = false
        transport.disconnect()
    }

    private fun scheduleReconnect() {
        val address = targetAddress ?: return
        if (reconnectJob?.isActive == true) return
        _reconnecting.value = true
        reconnectJob = scope.launch {
            var attempt = 0
            while (isActive && targetAddress == address && !isConnected) {
                delay(if (attempt++ < 5) 1_500L else 5_000L)
                transport.connect(address)
            }
        }
    }

    // ---- polling ----------------------------------------------------------

    private fun startPolling() {
        pollJob?.cancel()
        decoder.reset()
        _stale.value = false
        pollJob = scope.launch {
            clearHistory()

            _firmware.value = request(VescProtocol.fwVersion(), CommPacketId.FW_VERSION, 1_500)
                ?.let(VescProtocol::parseFwVersion)

            if (vehicle.value.applyModeOnConnect) activeMode()?.let { applyMode(it) }

            var misses = 0
            while (isActive) {
                val started = SystemClock.elapsedRealtime()
                val v = vehicle.value

                val local = request(VescProtocol.getValues(), CommPacketId.GET_VALUES)
                    ?.let(VescProtocol::parseValues)
                val remote = if (v.dualController && local != null) {
                    request(VescProtocol.forwardCan(v.canSlaveId, VescProtocol.getValues()), CommPacketId.GET_VALUES)
                        ?.let(VescProtocol::parseValues)
                } else {
                    null
                }

                if (local != null) {
                    misses = 0
                    _stale.value = false
                    publish(Telemetry.from(local, remote, System.currentTimeMillis()))
                } else if (++misses >= 5) {
                    _stale.value = true
                }

                val period = 1000L / v.pollHz.coerceIn(1, 30)
                delay((period - (SystemClock.elapsedRealtime() - started)).coerceAtLeast(5L))
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        _telemetry.value = null
        _stale.value = false
    }

    private fun publish(raw: Telemetry) {
        val v = vehicle.value
        val pct = if (v.batteryStabilize) {
            batteryEstimator.update(
                raw.voltage, raw.batteryCurrent, raw.ahUsed - raw.ahCharged, raw.timeMs,
                v.cellsSeries, v.batteryCapacityAh,
            )
        } else {
            Battery.percent(raw.voltage / v.cellsSeries.coerceAtLeast(1))
        }
        val t = raw.copy(batteryPct = pct)
        val prev = _telemetry.value
        if (prev != null && abs(VehicleMath.speedKmhForErpm(t.erpm, v)) > 1.5) {
            _rideTimeMs.value += (t.timeMs - prev.timeMs).coerceIn(0L, 2_000L)
        }
        _telemetry.value = t
        synchronized(historyBuf) {
            historyBuf.addLast(t)
            val maxSamples = 30 * v.pollHz.coerceIn(1, 30)
            while (historyBuf.size > maxSamples) historyBuf.removeFirst()
            _history.value = historyBuf.toList()
        }
    }

    private fun clearHistory() = synchronized(historyBuf) {
        batteryEstimator.reset()
        historyBuf.clear()
        _history.value = emptyList()
    }

    // ---- demo -------------------------------------------------------------

    private fun startDemo() {
        demoJob?.cancel()
        _demoActive.value = true
        clearHistory()
        demoJob = scope.launch {
            val sim = DemoSimulator()
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(1000L / vehicle.value.pollHz.coerceIn(1, 30))
                val now = SystemClock.elapsedRealtime()
                publish(sim.step((now - last) / 1000.0, vehicle.value, activeMode()))
                last = now
            }
        }
    }

    private fun stopDemo() {
        demoJob?.cancel()
        demoJob = null
        if (_demoActive.value) {
            _demoActive.value = false
            if (!isConnected) {
                _telemetry.value = null
                clearHistory()
            }
        }
    }

    /** Send a command and wait for the reply whose first byte is [expect]. One request in flight at a time. */
    private suspend fun request(payload: ByteArray, expect: Int, timeoutMs: Long = 400): ByteArray? =
        requestMutex.withLock {
            coroutineScope {
                val reply = async(start = CoroutineStart.UNDISPATCHED) {
                    payloads.first { it.isNotEmpty() && (it[0].toInt() and 0xFF) == expect }
                }
                if (!transport.send(VescPacket.encode(payload))) {
                    reply.cancel()
                    return@coroutineScope null
                }
                val result = withTimeoutOrNull(timeoutMs) { reply.await() }
                if (result == null) reply.cancel()
                result
            }
        }

    // ---- drive modes ------------------------------------------------------

    suspend fun applyMode(mode: DriveMode): Boolean {
        val v = vehicle.value
        _modeStatus.value = ModeApplyStatus.Applying(mode.id)
        val payload = VescProtocol.setMcconfTemp(
            VehicleMath.limitsFor(mode, v),
            forwardCan = v.dualController,
        )
        val ok = request(payload, CommPacketId.SET_MCCONF_TEMP, 1_500) != null
        _modeStatus.value = if (ok) ModeApplyStatus.Applied(mode.id) else ModeApplyStatus.Failed(mode.id)
        return ok
    }

    fun selectMode(id: String) {
        scope.launch {
            store.setActiveMode(id)
            val mode = modes.value.firstOrNull { it.id == id } ?: return@launch
            if (isConnected) applyMode(mode) else _modeStatus.value = ModeApplyStatus.Pending(id)
        }
    }

    fun reapplyActiveMode() {
        scope.launch { if (isConnected) activeMode()?.let { applyMode(it) } }
    }

    fun saveMode(mode: DriveMode, activate: Boolean) {
        scope.launch {
            val wasActive = activeMode()?.id == mode.id
            store.updateModes { list ->
                if (list.any { it.id == mode.id }) list.map { if (it.id == mode.id) mode else it } else list + mode
            }
            if (activate) store.setActiveMode(mode.id)
            if ((activate || wasActive) && isConnected) applyMode(mode)
        }
    }

    fun deleteMode(id: String) {
        scope.launch {
            val remaining = modes.value.filterNot { it.id == id }
            if (remaining.isEmpty()) return@launch
            val wasActive = activeMode()?.id == id
            store.updateModes { list -> list.filterNot { it.id == id }.ifEmpty { list } }
            if (wasActive) {
                val next = remaining.first()
                store.setActiveMode(next.id)
                if (isConnected) applyMode(next)
            }
        }
    }

    // ---- settings ---------------------------------------------------------

    fun updateVehicle(f: (VehicleSettings) -> VehicleSettings) {
        scope.launch { store.updateVehicle(f) }
    }

    fun updateDashboards(f: (List<Dashboard>) -> List<Dashboard>) {
        scope.launch { store.updateDashboards(f) }
    }
}
