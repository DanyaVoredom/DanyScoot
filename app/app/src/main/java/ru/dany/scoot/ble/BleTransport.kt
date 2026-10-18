package ru.dany.scoot.ble

import android.Manifest
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
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.util.UUID

data class ScannedDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val hasUart: Boolean,
)

class BleTransport(private val context: Context) {

    interface Listener {
        fun onDevice(device: ScannedDevice)
        fun onScanFinished()
        fun onConnected()
        fun onServicesReady(write: BluetoothGattCharacteristic, notify: BluetoothGattCharacteristic)
        fun onFrame(data: ByteArray)
        fun onDisconnected(reason: String)
        fun onError(message: String)
    }

    companion object {
        val UART_SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val UART_RX_CHAR: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val UART_TX_CHAR: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val SCAN_PERIOD_MS = 15000L
        const val CONNECT_TIMEOUT_MS = 20000L
        const val REQUESTED_MTU = 247
        const val MIN_CHUNK = 20
        const val WRITE_TIMEOUT_MS = 2000L
        const val DESCRIPTOR_TIMEOUT_MS = 3000L
        const val MAX_WRITE_QUEUE = 64
        private const val GATT_ERROR = 133
        private const val GATT_CLOSE_DELAY_MS = 150L
        private const val DISCOVER_FALLBACK_MS = 1500L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val manager: BluetoothManager? =
        context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? get() = manager?.adapter
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var scanning = false
    private var scanFilterUart = false
    private var mtu = 23
    private var writeWithoutResponse = false
    private val writeQueue = ArrayDeque<ByteArray>()
    private var writeBusy = false
    private var connected = false
    private var servicesReady = false
    private var descriptorPending = false
    private var closing = false
    private val writeTimeout = Runnable {
        if (writeBusy) {
            writeBusy = false
            writeQueue.clear()
            listener?.onError("BLE write timeout")
        }
    }
    private val discoverFallback = Runnable {
        val g = gatt ?: return@Runnable
        if (servicesReady || descriptorPending) return@Runnable
        try {
            g.discoverServices()
        } catch (e: SecurityException) {
        }
    }
    private val connectTimeout = Runnable {
        if (gatt != null && !connected) {
            listener?.onError("Connection timeout")
            closeGatt()
            listener?.onDisconnected("timeout")
        }
    }
    private val scanTimeout = Runnable {
        if (scanning) {
            stopScan()
            listener?.onScanFinished()
        }
    }
    private val descriptorTimeout = Runnable {
        if (descriptorPending) {
            failAndClose("Notification setup timeout")
        }
    }

    var listener: Listener? = null

    val isBluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    val isSupported: Boolean get() = adapter != null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !hasPermission(Manifest.permission.BLUETOOTH)) {
                return
            }
            val device = try {
                result.device
            } catch (e: SecurityException) {
                null
            } ?: return
            val address = try {
                device.address
            } catch (e: SecurityException) {
                return
            }
            val name = deviceName(device)
            val hasUart = scanFilterUart ||
                result.scanRecord?.serviceUuids?.contains(ParcelUuid(UART_SERVICE)) == true
            listener?.onDevice(ScannedDevice(address, name, result.rssi, hasUart))
        }

        override fun onScanFailed(errorCode: Int) {
            handler.post { handleScanFailed(errorCode) }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            handler.post { handleConnectionStateChange(g, status, newState) }
        }

        override fun onMtuChanged(g: BluetoothGatt, newMtu: Int, status: Int) {
            handler.post { handleMtuChanged(g, newMtu, status) }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            handler.post { handleServicesDiscovered(g, status) }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val uuid = descriptor.uuid
            handler.post { handleDescriptorWrite(g, uuid, status) }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            handler.post { handleCharacteristicWrite(g, status) }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            @Suppress("DEPRECATION")
            val value = characteristic.value?.copyOf() ?: return
            val uuid = characteristic.uuid
            handler.post { handleCharacteristicChanged(g, uuid, value) }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            val uuid = characteristic.uuid
            val copy = value.copyOf()
            handler.post { handleCharacteristicChanged(g, uuid, copy) }
        }
    }

    private fun handleScanFailed(errorCode: Int) {
        scanning = false
        listener?.onError("Scan failed: $errorCode")
        listener?.onScanFinished()
    }

    private fun handleConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
        if (g !== gatt || closing) return
        when {
            status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED -> {
                connected = true
                handler.removeCallbacks(connectTimeout)
                listener?.onConnected()
                try {
                    g.requestMtu(REQUESTED_MTU)
                } catch (e: SecurityException) {
                }
                handler.removeCallbacks(discoverFallback)
                handler.postDelayed(discoverFallback, DISCOVER_FALLBACK_MS)
            }
            status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED -> {
                closeGatt()
                listener?.onDisconnected("disconnected")
            }
            else -> {
                listener?.onError("GATT status $status")
                if (status == GATT_ERROR) {
                    closing = true
                    handler.postDelayed({ closeGatt(g) }, GATT_CLOSE_DELAY_MS)
                } else {
                    closeGatt()
                }
                listener?.onDisconnected("status=$status")
            }
        }
    }

    private fun handleMtuChanged(g: BluetoothGatt, newMtu: Int, status: Int) {
        if (g !== gatt) return
        if (status == BluetoothGatt.GATT_SUCCESS && newMtu > 23) {
            mtu = newMtu
        }
        handler.removeCallbacks(discoverFallback)
        if (servicesReady || descriptorPending) return
        try {
            g.discoverServices()
        } catch (e: SecurityException) {
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun handleServicesDiscovered(g: BluetoothGatt, status: Int) {
        if (g !== gatt || closing || servicesReady || descriptorPending) return
        handler.removeCallbacks(discoverFallback)
        if (status != BluetoothGatt.GATT_SUCCESS) {
            failAndClose("Service discovery failed: $status")
            return
        }
        val service = g.getService(UART_SERVICE)
        if (service == null) {
            failAndClose("UART service not found")
            return
        }
        val write = service.getCharacteristic(UART_RX_CHAR)
        val notify = service.getCharacteristic(UART_TX_CHAR)
        if (write == null || notify == null) {
            failAndClose("UART characteristics not found")
            return
        }
        writeChar = write
        notifyChar = notify
        writeWithoutResponse = (write.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) == 0
        val ok = try {
            g.setCharacteristicNotification(notify, true)
        } catch (e: SecurityException) {
            false
        }
        if (!ok) {
            failAndClose("Failed to enable notifications")
            return
        }
        val descriptor = notify.getDescriptor(CCCD)
        if (descriptor == null) {
            markServicesReady(write, notify)
            return
        }
        descriptorPending = true
        handler.removeCallbacks(descriptorTimeout)
        handler.postDelayed(descriptorTimeout, DESCRIPTOR_TIMEOUT_MS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val result = try {
                g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } catch (e: SecurityException) {
                BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION
            }
            if (result != BluetoothStatusCodes.SUCCESS) {
                failAndClose("Notification descriptor write failed: $result")
            }
        } else {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val result = try {
                g.writeDescriptor(descriptor)
            } catch (e: SecurityException) {
                false
            }
            if (!result) {
                failAndClose("Notification descriptor write failed")
            }
        }
    }

    private fun handleDescriptorWrite(g: BluetoothGatt, uuid: UUID, status: Int) {
        if (g !== gatt || closing || servicesReady) return
        if (uuid != CCCD) return
        descriptorPending = false
        handler.removeCallbacks(descriptorTimeout)
        if (status != BluetoothGatt.GATT_SUCCESS) {
            failAndClose("Notification descriptor write failed: $status")
            return
        }
        val write = writeChar
        val notify = notifyChar
        if (write == null || notify == null) {
            failAndClose("UART characteristics not found")
            return
        }
        markServicesReady(write, notify)
    }

    private fun handleCharacteristicWrite(g: BluetoothGatt, status: Int) {
        if (g !== gatt) return
        if (writeWithoutResponse) return
        handler.removeCallbacks(writeTimeout)
        if (status != BluetoothGatt.GATT_SUCCESS) {
            listener?.onError("Write failed: $status")
            writeQueue.clear()
            writeBusy = false
            return
        }
        processWriteQueue()
    }

    private fun handleCharacteristicChanged(g: BluetoothGatt, uuid: UUID, value: ByteArray) {
        if (g !== gatt) return
        if (uuid == UART_TX_CHAR) {
            listener?.onFrame(value)
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return manager.isLocationEnabled
        return Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.LOCATION_MODE,
            Settings.Secure.LOCATION_MODE_OFF,
        ) != Settings.Secure.LOCATION_MODE_OFF
    }

    @SuppressLint("MissingPermission")
    private fun deviceName(device: BluetoothDevice): String {
        return try {
            device.name ?: "Unknown"
        } catch (e: SecurityException) {
            "Unknown"
        }
    }

    private fun markServicesReady(write: BluetoothGattCharacteristic, notify: BluetoothGattCharacteristic) {
        servicesReady = true
        handler.removeCallbacks(discoverFallback)
        listener?.onServicesReady(write, notify)
    }

    private fun failAndClose(message: String) {
        listener?.onError(message)
        closeGatt()
        listener?.onDisconnected(message)
    }

    @SuppressLint("MissingPermission")
    fun startScan(filterUart: Boolean = false) {
        val a = adapter
        if (a == null) {
            listener?.onError("Bluetooth not supported")
            listener?.onScanFinished()
            return
        }
        if (!a.isEnabled) {
            listener?.onError("Bluetooth disabled")
            listener?.onScanFinished()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !isLocationEnabled()) {
            listener?.onError("Location services disabled")
            listener?.onScanFinished()
            return
        }
        if (scanning) return
        scanner = try {
            a.bluetoothLeScanner
        } catch (e: SecurityException) {
            null
        }
        val s = scanner
        if (s == null) {
            listener?.onError("BLE scanner unavailable")
            listener?.onScanFinished()
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanFilterUart = filterUart
        val filters = if (filterUart) {
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(UART_SERVICE)).build())
        } else {
            null
        }
        scanning = true
        handler.removeCallbacks(scanTimeout)
        try {
            s.startScan(filters, settings, scanCallback)
            handler.postDelayed(scanTimeout, SCAN_PERIOD_MS)
        } catch (e: SecurityException) {
            scanning = false
            listener?.onError("Scan permission denied")
            listener?.onScanFinished()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        handler.removeCallbacks(scanTimeout)
        if (!scanning) return
        scanning = false
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        val a = adapter
        if (a == null) {
            listener?.onError("Bluetooth not supported")
            listener?.onDisconnected("not supported")
            return
        }
        if (!a.isEnabled) {
            listener?.onError("Bluetooth disabled")
            listener?.onDisconnected("disabled")
            return
        }
        stopScan()
        closeGatt()
        val device = try {
            a.getRemoteDevice(address)
        } catch (e: Exception) {
            listener?.onError("Bad address: $address")
            listener?.onDisconnected("bad address")
            return
        }
        try {
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) {
                listener?.onError("Failed to start connection")
                listener?.onDisconnected("connect failed")
                return
            }
            handler.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS)
        } catch (e: SecurityException) {
            listener?.onError("Connect permission denied")
            listener?.onDisconnected("permission denied")
        }
    }

    @SuppressLint("MissingPermission")
    fun write(data: ByteArray): Boolean {
        if (gatt == null || writeChar == null || !connected || !servicesReady) return false
        if (data.isEmpty()) return true
        val chunkSize = (mtu - 3).coerceIn(MIN_CHUNK, 512)
        val chunks = (data.size + chunkSize - 1) / chunkSize
        if (chunks > MAX_WRITE_QUEUE || writeQueue.size + chunks > MAX_WRITE_QUEUE) {
            listener?.onError("BLE write queue overflow")
            return false
        }
        var offset = 0
        while (offset < data.size) {
            val end = (offset + chunkSize).coerceAtMost(data.size)
            writeQueue.addLast(data.copyOfRange(offset, end))
            offset = end
        }
        if (!writeBusy) processWriteQueue()
        return true
    }

    @SuppressLint("MissingPermission")
    private fun processWriteQueue() {
        val g = gatt
        val ch = writeChar
        if (g == null || ch == null) {
            writeQueue.clear()
            writeBusy = false
            return
        }
        val chunk = writeQueue.removeFirstOrNull()
        if (chunk == null) {
            writeBusy = false
            return
        }
        writeBusy = true
        val type = if (writeWithoutResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        val started = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(ch, chunk, type) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                ch.value = chunk
                @Suppress("DEPRECATION")
                ch.writeType = type
                @Suppress("DEPRECATION")
                g.writeCharacteristic(ch)
            }
        } catch (e: SecurityException) {
            false
        }
        if (!started) {
            writeQueue.clear()
            writeBusy = false
            listener?.onError("BLE write failed")
        } else if (writeWithoutResponse) {
            handler.post { processWriteQueue() }
        } else {
            handler.removeCallbacks(writeTimeout)
            handler.postDelayed(writeTimeout, WRITE_TIMEOUT_MS)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopScan()
        try {
            gatt?.disconnect()
        } catch (e: SecurityException) {
        }
        closeGatt()
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt(target: BluetoothGatt? = gatt) {
        if (target === gatt) {
            gatt = null
            writeChar = null
            notifyChar = null
            writeQueue.clear()
            writeBusy = false
            connected = false
            servicesReady = false
            descriptorPending = false
            closing = false
            mtu = 23
            writeWithoutResponse = false
            handler.removeCallbacks(writeTimeout)
            handler.removeCallbacks(discoverFallback)
            handler.removeCallbacks(connectTimeout)
            handler.removeCallbacks(descriptorTimeout)
        }
        if (target == null) return
        try {
            target.close()
        } catch (e: SecurityException) {
        }
    }
}
