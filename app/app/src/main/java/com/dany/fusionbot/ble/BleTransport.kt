package com.dany.fusionbot.ble

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
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.dany.fusionbot.core.LogBus
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
        const val REQUESTED_MTU = 247
        const val MIN_CHUNK = 20
        const val WRITE_TIMEOUT_MS = 2000L
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
    private var mtu = 23
    private var writeWithoutResponse = false
    private val writeQueue = ArrayDeque<ByteArray>()
    private var writeBusy = false
    private val writeTimeout = Runnable {
        if (writeBusy) {
            writeBusy = false
            writeQueue.clear()
            listener?.onError("BLE write timeout")
        }
    }
    private val discoverFallback = Runnable {
        try {
            gatt?.discoverServices()
        } catch (e: SecurityException) {
        }
    }

    var listener: Listener? = null

    val isBluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    val isSupported: Boolean get() = adapter != null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = deviceName(device)
            val hasUart = result.scanRecord?.serviceUuids?.contains(ParcelUuid(UART_SERVICE)) == true
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !hasPermission(Manifest.permission.BLUETOOTH)) {
                return
            }
            listener?.onDevice(ScannedDevice(device.address, name, result.rssi, hasUart))
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            listener?.onError("Scan failed: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when {
                status == BluetoothGatt.GATT_FAILURE || status == 133 -> {
                    listener?.onError("GATT status $status")
                    handler.postDelayed({ closeGatt() }, 150)
                    listener?.onDisconnected("status=$status")
                }
                status != BluetoothGatt.GATT_SUCCESS -> {
                    listener?.onError("GATT status $status")
                    closeGatt()
                    listener?.onDisconnected("status=$status")
                }
                newState == BluetoothProfile.STATE_CONNECTED -> {
                    listener?.onConnected()
                    try {
                        g.requestMtu(REQUESTED_MTU)
                    } catch (e: SecurityException) {
                    }
                    handler.removeCallbacks(discoverFallback)
                    handler.postDelayed(discoverFallback, 1500)
                }
                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    closeGatt()
                    listener?.onDisconnected("disconnected")
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, newMtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newMtu > 23) {
                mtu = newMtu
            }
            handler.removeCallbacks(discoverFallback)
            try {
                g.discoverServices()
            } catch (e: SecurityException) {
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener?.onError("Service discovery failed: $status")
                return
            }
            val service = g.getService(UART_SERVICE)
            if (service == null) {
                listener?.onError("UART service not found")
                return
            }
            val write = service.getCharacteristic(UART_RX_CHAR)
            val notify = service.getCharacteristic(UART_TX_CHAR)
            if (write == null || notify == null) {
                listener?.onError("UART characteristics not found")
                return
            }
            writeChar = write
            notifyChar = notify
            writeWithoutResponse = (write.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) == 0
            val ok = g.setCharacteristicNotification(notify, true)
            if (!ok) {
                listener?.onError("Failed to enable notifications")
                return
            }
            val descriptor = notify.getDescriptor(CCCD)
            if (descriptor == null) {
                listener?.onServicesReady(write, notify)
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(descriptor)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val write = writeChar
            val notify = notifyChar
            if (write != null && notify != null) {
                if (descriptor.uuid == CCCD && status != BluetoothGatt.GATT_SUCCESS) {
                    listener?.onError("Notification descriptor write failed: $status")
                } else {
                    listener?.onServicesReady(write, notify)
                }
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            handler.removeCallbacks(writeTimeout)
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener?.onError("Write failed: $status")
                writeQueue.clear()
                writeBusy = false
                return
            }
            processWriteQueue()
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                listener?.onFrame(value)
            }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            listener?.onFrame(value)
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun deviceName(device: BluetoothDevice): String {
        return try {
            device.name ?: "Unknown"
        } catch (e: SecurityException) {
            "Unknown"
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val a = adapter
        if (a == null) {
            listener?.onError("Bluetooth not supported")
            return
        }
        if (!a.isEnabled) {
            listener?.onError("Bluetooth disabled")
            return
        }
        if (scanning) return
        scanner = a.bluetoothLeScanner
        val s = scanner
        if (s == null) {
            listener?.onError("BLE scanner unavailable")
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanning = true
        try {
            s.startScan(null, settings, scanCallback)
            handler.postDelayed({
                if (scanning) {
                    stopScan()
                    listener?.onScanFinished()
                }
            }, SCAN_PERIOD_MS)
        } catch (e: SecurityException) {
            scanning = false
            listener?.onError("Scan permission denied")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) return
        scanning = false
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        val a = adapter ?: run {
            listener?.onError("Bluetooth not supported")
            return
        }
        stopScan()
        closeGatt()
        val device = try {
            a.getRemoteDevice(address)
        } catch (e: Exception) {
            listener?.onError("Bad address: $address")
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                gatt = device.connectGatt(context, false, gattCallback)
            }
        } catch (e: SecurityException) {
            listener?.onError("Connect permission denied")
        }
    }

    @SuppressLint("MissingPermission")
    fun write(data: ByteArray): Boolean {
        if (gatt == null || writeChar == null) return false
        val chunkSize = (mtu - 3).coerceAtLeast(MIN_CHUNK)
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
                g.writeCharacteristic(ch, chunk, type) == BluetoothGatt.GATT_SUCCESS
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
        try {
            gatt?.disconnect()
        } catch (e: SecurityException) {
        }
        closeGatt()
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        val g = gatt ?: return
        gatt = null
        writeChar = null
        notifyChar = null
        writeQueue.clear()
        writeBusy = false
        handler.removeCallbacks(writeTimeout)
        handler.removeCallbacks(discoverFallback)
        mtu = 23
        try {
            g.close()
        } catch (e: SecurityException) {
        }
    }
}
