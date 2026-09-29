package com.vescdash.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * BLE link to a VESC through its Nordic UART Service bridge (NRF51/52 modules,
 * VESC Express, and most controllers with built-in Bluetooth).
 *
 * Callers must hold BLUETOOTH_SCAN / BLUETOOTH_CONNECT (Android 12+) or location (older).
 */
@SuppressLint("MissingPermission")
class VescBleTransport(private val context: Context) {

    sealed interface State {
        data object Idle : State
        data class Connecting(val address: String) : State
        data class Connected(val address: String, val name: String?) : State
        data class Failed(val address: String?, val reason: String) : State
    }

    data class Device(val address: String, val name: String?, val rssi: Int, val likelyVesc: Boolean)

    private enum class Op { CONNECT, MTU, DISCOVER, DESCRIPTOR }

    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = manager.adapter
    val isEnabled: Boolean get() = adapter?.isEnabled == true

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 256)
    /** Raw bytes as they arrive from the VESC (not yet framed into packets). */
    val incoming: SharedFlow<ByteArray> = _incoming.asSharedFlow()

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var rxChar: BluetoothGattCharacteristic? = null
    @Volatile private var chunkSize = 20
    @Volatile private var pendingOp: Op? = null
    @Volatile private var stepWaiter: CompletableDeferred<Boolean>? = null
    @Volatile private var writeWaiter: CompletableDeferred<Boolean>? = null
    @Volatile private var userClosed = false

    private val connectMutex = Mutex()
    private val writeMutex = Mutex()

    fun scan(): Flow<List<Device>> = callbackFlow {
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            close(IllegalStateException("Bluetooth is turned off"))
            return@callbackFlow
        }
        val found = LinkedHashMap<String, Device>()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val record = result.scanRecord
                val name = record?.deviceName ?: result.device.name
                val advertisesUart = record?.serviceUuids?.any { it.uuid == SERVICE_UUID } == true
                val likely = advertisesUart || name?.contains("vesc", ignoreCase = true) == true
                found[result.device.address] = Device(result.device.address, name, result.rssi, likely)
                trySend(
                    found.values.sortedWith(
                        compareByDescending<Device> { it.likelyVesc }
                            .thenByDescending { it.name != null }
                            .thenBy { it.name ?: it.address },
                    ),
                )
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("Scan failed (code $errorCode)"))
            }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, settings, cb)
        awaitClose { runCatching { scanner.stopScan(cb) } }
    }

    suspend fun connect(address: String): Boolean = connectMutex.withLock {
        closeGatt()
        userClosed = false
        val adapter = adapter
        if (adapter == null || !adapter.isEnabled) return@withLock fail(address, "Bluetooth is turned off")
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            return@withLock fail(address, "Invalid device address")
        }
        _state.value = State.Connecting(address)

        val linked = step(Op.CONNECT, 12_000) {
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            gatt != null
        }
        val g = gatt
        if (!linked || g == null) return@withLock fail(address, "Couldn't connect — is the VESC powered and VESC Tool closed?")

        chunkSize = 20
        step(Op.MTU, 3_000) { g.requestMtu(512) } // optional; falls back to 20-byte writes
        if (!step(Op.DISCOVER, 10_000) { g.discoverServices() }) return@withLock fail(address, "Service discovery failed")

        val service = g.getService(SERVICE_UUID)
            ?: return@withLock fail(address, "This device has no VESC UART service")
        val rx = service.getCharacteristic(RX_UUID)
        val tx = service.getCharacteristic(TX_UUID)
        val cccd = tx?.getDescriptor(CCCD_UUID)
        if (rx == null || tx == null || cccd == null) return@withLock fail(address, "UART characteristics missing")

        g.setCharacteristicNotification(tx, true)
        if (!step(Op.DESCRIPTOR, 5_000) { writeDescriptor(g, cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) }) {
            return@withLock fail(address, "Couldn't enable notifications")
        }
        rxChar = rx
        g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
        _state.value = State.Connected(address, device.name)
        true
    }

    fun disconnect() {
        userClosed = true
        stepWaiter?.complete(false)
        writeWaiter?.complete(false)
        closeGatt()
        _state.value = State.Idle
    }

    /** Sends one framed packet, split into MTU-sized writes. */
    suspend fun send(data: ByteArray): Boolean = writeMutex.withLock {
        var offset = 0
        while (offset < data.size) {
            val g = gatt
            val ch = rxChar
            if (g == null || ch == null) return@withLock false
            val end = minOf(offset + chunkSize, data.size)
            if (!writeChunk(g, ch, data.copyOfRange(offset, end))) return@withLock false
            offset = end
        }
        true
    }

    private suspend fun writeChunk(g: BluetoothGatt, ch: BluetoothGattCharacteristic, chunk: ByteArray): Boolean {
        val noResponse = (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
        val type = if (noResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        repeat(5) {
            val waiter = CompletableDeferred<Boolean>()
            writeWaiter = waiter
            val started = if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(ch, chunk, type) == BluetoothStatusCodes.SUCCESS
            } else {
                legacyWrite(g, ch, chunk, type)
            }
            if (started) return withTimeoutOrNull(1_000) { waiter.await() } ?: false
            delay(15) // stack busy, retry
        }
        return false
    }

    private suspend fun step(op: Op, timeoutMs: Long, action: () -> Boolean): Boolean {
        val waiter = CompletableDeferred<Boolean>()
        pendingOp = op
        stepWaiter = waiter
        val ok = if (action()) withTimeoutOrNull(timeoutMs) { waiter.await() } ?: false else false
        pendingOp = null
        stepWaiter = null
        return ok
    }

    private fun complete(op: Op, ok: Boolean) {
        if (pendingOp == op) stepWaiter?.complete(ok)
    }

    private fun fail(address: String?, reason: String): Boolean {
        closeGatt()
        _state.value = if (userClosed) State.Idle else State.Failed(address, reason)
        return false
    }

    private fun closeGatt() {
        val g = gatt
        gatt = null
        rxChar = null
        if (g != null) {
            runCatching { g.disconnect() }
            runCatching { g.close() }
        }
    }

    private fun writeDescriptor(g: BluetoothGatt, d: BluetoothGattDescriptor, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= 33) {
            g.writeDescriptor(d, value) == BluetoothStatusCodes.SUCCESS
        } else {
            legacyWriteDescriptor(g, d, value)
        }

    @Suppress("DEPRECATION")
    private fun legacyWrite(g: BluetoothGatt, ch: BluetoothGattCharacteristic, chunk: ByteArray, type: Int): Boolean {
        ch.writeType = type
        ch.value = chunk
        return g.writeCharacteristic(ch)
    }

    @Suppress("DEPRECATION")
    private fun legacyWriteDescriptor(g: BluetoothGatt, d: BluetoothGattDescriptor, value: ByteArray): Boolean {
        d.value = value
        return g.writeDescriptor(d)
    }

    @Suppress("DEPRECATION")
    private fun legacyValue(ch: BluetoothGattCharacteristic): ByteArray? = ch.value

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                complete(Op.CONNECT, true)
                return
            }
            stepWaiter?.complete(false)
            writeWaiter?.complete(false)
            if (g === gatt) {
                closeGatt()
                val s = _state.value
                if (s is State.Connected) _state.value = State.Failed(s.address, "Connection lost")
            } else {
                runCatching { g.close() }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) chunkSize = (mtu - 3).coerceIn(20, 509)
            complete(Op.MTU, true)
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            complete(Op.DISCOVER, status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            complete(Op.DESCRIPTOR, status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            writeWaiter?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        // Android 13+
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            if (c.uuid == TX_UUID) _incoming.tryEmit(value.copyOf())
        }

        // Android 12 and below
        @Deprecated("Replaced by the ByteArray overload on Android 13")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33 && c.uuid == TX_UUID) {
                legacyValue(c)?.let { _incoming.tryEmit(it.copyOf()) }
            }
        }
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val RX_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e") // phone -> VESC
        val TX_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e") // VESC -> phone
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
