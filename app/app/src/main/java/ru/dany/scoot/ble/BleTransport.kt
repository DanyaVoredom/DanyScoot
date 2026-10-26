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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.UUID

data class ScannedDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val hasUart: Boolean,
    val advert: String = "",
    val hasEdifier: Boolean = false,
    val kind: DeviceKind = DeviceKind.OTHER,
) {
    val distanceMeters: Double get() = DeviceClassifier.distanceMeters(rssi)
}

data class BleProfile(
    val service: UUID,
    val write: UUID,
    val notify: UUID,
    val cccd: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
    val mtu: Int = 247,
    val writeWithoutResponse: Boolean? = null,
    val extraNotify: List<UUID> = emptyList(),
)

class BleTransport(private val context: Context) {

    interface Listener {
        fun onDevice(device: ScannedDevice)
        fun onScanFinished()
        fun onConnected()
        fun onServicesReady(write: BluetoothGattCharacteristic, notify: BluetoothGattCharacteristic)
        fun onGattDump(dump: GattDump)
        fun onCharacteristicRead(uuid: UUID, value: ByteArray)
        fun onFrame(uuid: UUID, data: ByteArray)
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
        private const val DISCOVER_FALLBACK_MS = 3000L
        private const val REDISCOVER_DELAY_MS = 800L
        private const val MAX_REDISCOVER = 3
        private const val BOND_TIMEOUT_MS = 20000L
        private const val BOND_RECONNECT_DELAY_MS = 600L
        private const val DISCOVERY_TIMEOUT_MS = 12000L
    }

    private data class PendingWrite(
        val characteristic: BluetoothGattCharacteristic,
        val data: ByteArray,
        val withoutResponse: Boolean,
    )

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
    private val writeQueue = ArrayDeque<PendingWrite>()
    private var writeBusy = false
    private var currentWriteWithoutResponse = false
    private var connected = false
    private var servicesReady = false
    private var descriptorPending = false
    private var closing = false
    private var rediscoverCount = 0
    private var bondRequested = false
    private var bondReceiverActive = false
    private var failureFatal = false
    private var gattAddress = ""
    private val subscribed = LinkedHashSet<UUID>()
    private val cccdQueue = ArrayDeque<BluetoothGattCharacteristic>()
    private var cccdActive: BluetoothGattCharacteristic? = null
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
    private val rediscover = Runnable {
        val g = gatt ?: return@Runnable
        if (servicesReady || descriptorPending || closing) return@Runnable
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
    private val discoveryTimeout = Runnable {
        if (!servicesReady && !descriptorPending) {
            failAndClose("Service discovery timeout")
        }
    }

    var listener: Listener? = null
    var profile: BleProfile = BleProfiles.URENT
    var allowAutoProfile: Boolean = false

    val isBluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    val isSupported: Boolean get() = adapter != null

    val negotiatedMtu: Int get() = mtu

    val lastFailureIsFatal: Boolean get() = failureFatal

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
            val uuids = try {
                result.scanRecord?.serviceUuids?.map { it.uuid }
            } catch (e: SecurityException) {
                null
            }
            val kind = DeviceClassifier.classify(name, uuids)
            val hasUart = kind == DeviceKind.SCOOTER
            val hasEdifier = kind == DeviceKind.HEADSET
            listener?.onDevice(
                ScannedDevice(
                    address = address,
                    name = name,
                    rssi = result.rssi,
                    hasUart = hasUart,
                    advert = advertisement(result),
                    hasEdifier = hasEdifier,
                    kind = kind,
                ),
            )
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

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            val uuid = characteristic.uuid
            val copy = value.copyOf()
            handler.post { handleCharacteristicRead(g, uuid, copy, status) }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            @Suppress("DEPRECATION")
            val value = characteristic.value?.copyOf() ?: ByteArray(0)
            val uuid = characteristic.uuid
            handler.post { handleCharacteristicRead(g, uuid, value, status) }
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
                failureFatal = false
                rediscoverCount = 0
                bondRequested = false
                handler.removeCallbacks(connectTimeout)
                listener?.onConnected()
                try {
                    g.requestMtu(profile.mtu)
                } catch (e: SecurityException) {
                }
                handler.removeCallbacks(discoverFallback)
                handler.postDelayed(discoverFallback, DISCOVER_FALLBACK_MS)
                handler.removeCallbacks(discoveryTimeout)
                handler.postDelayed(discoveryTimeout, DISCOVERY_TIMEOUT_MS)
            }
            status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED -> {
                closeGatt()
                listener?.onDisconnected("disconnected")
            }
            else -> {
                val reason = "GATT status $status (${gattStatusText(status)})"
                failureFatal = status == 5 || status == 8 || status == 15
                listener?.onError(reason)
                if ((status == 5 || status == 8 || status == 15) && startBonding(g)) return
                if (status == GATT_ERROR || status == 147) {
                    closing = true
                    handler.postDelayed({ closeGatt(g) }, GATT_CLOSE_DELAY_MS)
                } else {
                    closeGatt()
                }
                listener?.onDisconnected("status=$status (${gattStatusText(status)})")
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
            if ((status == 5 || status == 8 || status == 15) && startBonding(g)) return
            if (rediscoverCount < MAX_REDISCOVER) {
                rediscoverCount += 1
                handler.removeCallbacks(rediscover)
                handler.postDelayed(rediscover, REDISCOVER_DELAY_MS)
                return
            }
            failAndClose("Service discovery failed: $status")
            return
        }
        val dump = buildDump(g)
        listener?.onGattDump(dump)
        val activeProfile = profile
        val service = g.getService(activeProfile.service)
        var write = service?.getCharacteristic(activeProfile.write)
        var notify = service?.getCharacteristic(activeProfile.notify)
        var extras = activeProfile.extraNotify
        if (write == null || notify == null) {
            val auto = if (allowAutoProfile) findAutoPair(g) else null
            if (auto != null) {
                write = auto.first
                notify = auto.second
                extras = emptyList()
                listener?.onError("Профиль не найден, используется GATT ${shortUuid(auto.first.uuid)} / ${shortUuid(auto.second.uuid)}")
            } else {
                if (service == null && rediscoverCount < MAX_REDISCOVER) {
                    rediscoverCount += 1
                    handler.removeCallbacks(rediscover)
                    handler.postDelayed(rediscover, REDISCOVER_DELAY_MS)
                    return
                }
                if (service == null) {
                    failAndClose("Service ${activeProfile.service} not found", fatal = true)
                } else {
                    failAndClose(
                        "Characteristics ${activeProfile.write}/${activeProfile.notify} not found",
                        fatal = true,
                    )
                }
                return
            }
        }
        writeChar = write
        notifyChar = notify
        writeWithoutResponse = activeProfile.writeWithoutResponse
            ?: ((write.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) == 0)
        val notifyChars = ArrayList<BluetoothGattCharacteristic>()
        notifyChars.add(notify)
        for (uuid in extras) {
            val extra = findCharacteristic(g, uuid) ?: continue
            if (extra.uuid != notify.uuid) notifyChars.add(extra)
        }
        for (char in notifyChars) {
            val ok = try {
                g.setCharacteristicNotification(char, true)
            } catch (e: SecurityException) {
                false
            }
            if (!ok) {
                if (char.uuid == notify.uuid) {
                    failAndClose("Failed to enable notifications")
                    return
                }
                listener?.onError("Notify ${shortUuid(char.uuid)} недоступен")
                continue
            }
            subscribed.add(char.uuid)
        }
        cccdQueue.clear()
        val cccd = activeProfile.cccd
        for (char in notifyChars) {
            if (char.getDescriptor(cccd) != null) cccdQueue.addLast(char)
        }
        if (cccdQueue.isEmpty()) {
            markServicesReady(write, notify)
            return
        }
        descriptorPending = true
        writeNextCccd()
    }

    private fun writeNextCccd() {
        val g = gatt
        if (g == null) {
            descriptorPending = false
            return
        }
        val char = cccdQueue.removeFirstOrNull()
        if (char == null) {
            descriptorPending = false
            cccdActive = null
            handler.removeCallbacks(descriptorTimeout)
            val write = writeChar
            val notify = notifyChar
            if (write != null && notify != null) markServicesReady(write, notify)
            return
        }
        val descriptor = char.getDescriptor(profile.cccd)
        if (descriptor == null) {
            writeNextCccd()
            return
        }
        cccdActive = char
        handler.removeCallbacks(descriptorTimeout)
        handler.postDelayed(descriptorTimeout, DESCRIPTOR_TIMEOUT_MS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val result = try {
                g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } catch (e: SecurityException) {
                BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION
            }
            if (result != BluetoothStatusCodes.SUCCESS) {
                if (char.uuid == notifyChar?.uuid) {
                    failAndClose("Notification descriptor write failed: $result")
                } else {
                    descriptorPending = false
                    listener?.onError("Notify ${shortUuid(char.uuid)}: $result")
                    writeNextCccd()
                }
            }
        } else {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val result = try {
                g.writeDescriptor(descriptor)
            } catch (e: SecurityException) {
                false
            }
            if (!result) {
                if (char.uuid == notifyChar?.uuid) {
                    failAndClose("Notification descriptor write failed")
                } else {
                    descriptorPending = false
                    listener?.onError("Notify ${shortUuid(char.uuid)} не включен")
                    writeNextCccd()
                }
            }
        }
    }

    private fun handleDescriptorWrite(g: BluetoothGatt, uuid: UUID, status: Int) {
        if (g !== gatt || closing) return
        if (uuid != profile.cccd) return
        if (servicesReady) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                if ((status == 5 || status == 8 || status == 15) && startBonding(g)) return
                listener?.onError("Descriptor write failed: $status")
            }
            return
        }
        val char = cccdActive
        cccdActive = null
        descriptorPending = false
        handler.removeCallbacks(descriptorTimeout)
        if (status != BluetoothGatt.GATT_SUCCESS) {
            if ((status == 5 || status == 8 || status == 15) && startBonding(g)) return
            if (char != null && char.uuid == notifyChar?.uuid) {
                failAndClose("Notification descriptor write failed: $status")
                return
            }
            listener?.onError("Notify ${char?.let { shortUuid(it.uuid) } ?: "?"}: $status")
        }
        writeNextCccd()
    }

    private fun handleCharacteristicWrite(g: BluetoothGatt, status: Int) {
        if (g !== gatt) return
        if (currentWriteWithoutResponse) return
        handler.removeCallbacks(writeTimeout)
        if (status != BluetoothGatt.GATT_SUCCESS) {
            if ((status == 5 || status == 8 || status == 15) && startBonding(g)) {
                writeQueue.clear()
                writeBusy = false
                return
            }
            listener?.onError("Write failed: $status")
            writeQueue.clear()
            writeBusy = false
            return
        }
        processWriteQueue()
    }

    private fun handleCharacteristicRead(g: BluetoothGatt, uuid: UUID, value: ByteArray, status: Int) {
        if (g !== gatt) return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            if ((status == 5 || status == 8 || status == 15) && startBonding(g)) return
            listener?.onError("Read ${shortUuid(uuid)} failed: $status")
            return
        }
        listener?.onCharacteristicRead(uuid, value)
    }

    private fun handleCharacteristicChanged(g: BluetoothGatt, uuid: UUID, value: ByteArray) {
        if (g !== gatt) return
        if (!subscribed.contains(uuid)) return
        listener?.onFrame(uuid, value)
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
        handler.removeCallbacks(discoveryTimeout)
        listener?.onServicesReady(write, notify)
    }

    private fun failAndClose(message: String, fatal: Boolean = false) {
        failureFatal = fatal
        listener?.onError(message)
        closeGatt()
        listener?.onDisconnected(message)
    }

    private val bondTimeout = Runnable {
        if (bondRequested) {
            bondRequested = false
            unregisterBondReceiver()
            failAndClose("Bond timeout", fatal = true)
        }
    }

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } ?: return
            val address = try {
                device.address
            } catch (e: SecurityException) {
                return
            }
            if (gattAddress.isNotEmpty() && address != gattAddress) return
            val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
            when (state) {
                BluetoothDevice.BOND_BONDED -> {
                    if (!bondRequested) return
                    bondRequested = false
                    handler.removeCallbacks(bondTimeout)
                    unregisterBondReceiver()
                    val addr = gattAddress
                    listener?.onError("Спаривание завершено, переподключение")
                    closeGatt()
                    if (addr.isNotEmpty()) {
                        handler.postDelayed({ connect(addr) }, BOND_RECONNECT_DELAY_MS)
                    }
                }
                BluetoothDevice.BOND_NONE -> {
                    val prev = intent.getIntExtra(
                        BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                        BluetoothDevice.BOND_NONE,
                    )
                    if (bondRequested && prev == BluetoothDevice.BOND_BONDING) {
                        bondRequested = false
                        handler.removeCallbacks(bondTimeout)
                        unregisterBondReceiver()
                        failAndClose("Спаривание отклонено", fatal = true)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBonding(g: BluetoothGatt): Boolean {
        if (bondRequested) return true
        val device = try {
            g.device
        } catch (e: SecurityException) {
            null
        } ?: return false
        val bondState = try {
            device.bondState
        } catch (e: SecurityException) {
            BluetoothDevice.BOND_NONE
        }
        if (bondState == BluetoothDevice.BOND_BONDED) return false
        bondRequested = true
        handler.removeCallbacks(discoveryTimeout)
        handler.removeCallbacks(discoverFallback)
        handler.removeCallbacks(rediscover)
        registerBondReceiver()
        handler.removeCallbacks(bondTimeout)
        handler.postDelayed(bondTimeout, BOND_TIMEOUT_MS)
        if (bondState != BluetoothDevice.BOND_BONDING) {
            val ok = try {
                device.createBond()
            } catch (e: SecurityException) {
                false
            }
            if (!ok) {
                bondRequested = false
                handler.removeCallbacks(bondTimeout)
                unregisterBondReceiver()
                listener?.onError("Не удалось запросить спаривание")
                return false
            }
        }
        listener?.onError("Требуется спаривание, подтвердите на устройстве")
        return true
    }

    private fun registerBondReceiver() {
        if (bondReceiverActive) return
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(bondReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(bondReceiver, filter)
            }
            bondReceiverActive = true
        } catch (e: SecurityException) {
        }
    }

    private fun unregisterBondReceiver() {
        if (!bondReceiverActive) return
        bondReceiverActive = false
        try {
            context.unregisterReceiver(bondReceiver)
        } catch (e: Exception) {
        }
    }

    private fun findAutoPair(g: BluetoothGatt): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic>? {
        val services = try {
            g.services
        } catch (e: SecurityException) {
            return null
        }
        val metadata = setOf(
            "00001800-0000-1000-8000-00805f9b34fb",
            "00001801-0000-1000-8000-00805f9b34fb",
            "0000180a-0000-1000-8000-00805f9b34fb",
            "0000180f-0000-1000-8000-00805f9b34fb",
            "0000fe2c-0000-1000-8000-00805f9b34fb",
        )
        val ordered = services.filterNot { metadata.contains(it.uuid.toString()) } +
            services.filter { metadata.contains(it.uuid.toString()) }
        for (service in ordered) {
            var write: BluetoothGattCharacteristic? = null
            var notify: BluetoothGattCharacteristic? = null
            for (char in service.characteristics) {
                val props = char.properties
                if (write == null && props and (
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                    ) != 0
                ) {
                    write = char
                }
                if (notify == null && props and (
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                        BluetoothGattCharacteristic.PROPERTY_INDICATE
                    ) != 0
                ) {
                    notify = char
                }
            }
            if (write != null && notify != null) return write to notify
        }
        return null
    }

    private fun findCharacteristic(g: BluetoothGatt, uuid: UUID): BluetoothGattCharacteristic? {
        val services = try {
            g.services
        } catch (e: SecurityException) {
            return null
        }
        for (service in services) {
            val char = try {
                service.getCharacteristic(uuid)
            } catch (e: SecurityException) {
                null
            }
            if (char != null) return char
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun buildDump(g: BluetoothGatt): GattDump {
        val services = try {
            g.services
        } catch (e: SecurityException) {
            null
        } ?: emptyList()
        val infos = services.mapNotNull { service ->
            try {
                GattServiceInfo(
                    uuid = service.uuid,
                    characteristics = service.characteristics.map { char ->
                        GattCharacteristicInfo(char.uuid, char.properties)
                    },
                )
            } catch (e: SecurityException) {
                null
            }
        }
        return GattDump(address = gattAddress, name = deviceName(g.device), services = infos)
    }

    private fun advertisement(result: ScanResult): String {
        val record = try {
            result.scanRecord
        } catch (e: SecurityException) {
            null
        } ?: return ""
        val parts = ArrayList<String>()
        try {
            record.serviceUuids?.takeIf { it.isNotEmpty() }?.let { uuids ->
                parts.add("svc=" + uuids.joinToString(",") { shortUuid(it.uuid) })
            }
        } catch (e: Exception) {
        }
        try {
            val data = record.manufacturerSpecificData
            if (data != null) {
                for (i in 0 until data.size()) {
                    val id = data.keyAt(i)
                    val value = data.valueAt(i) ?: continue
                    parts.add("mfr=" + String.format(Locale.ROOT, "%04X", id) + ":" + hex(value, ""))
                }
            }
        } catch (e: Exception) {
        }
        if (parts.isEmpty()) {
            val bytes = try {
                record.bytes
            } catch (e: Exception) {
                null
            }
            if (bytes != null && bytes.isNotEmpty()) parts.add("raw=" + hex(bytes, ""))
        }
        return parts.joinToString(" ")
    }

    @SuppressLint("MissingPermission")
    fun startScan(filterUuid: UUID? = null) {
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
        val filters = filterUuid?.let {
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(it)).build())
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
        failureFatal = false
        rediscoverCount = 0
        bondRequested = false
        gattAddress = address
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
        val ch = writeChar ?: return false
        if (data.isEmpty()) return true
        return enqueue(ch, data, writeWithoutResponse)
    }

    @SuppressLint("MissingPermission")
    fun writeTo(uuid: UUID, data: ByteArray, withoutResponse: Boolean? = null): Boolean {
        val g = gatt ?: return false
        if (!connected || !servicesReady) return false
        val ch = findCharacteristic(g, uuid) ?: return false
        val props = ch.properties
        val writable = props and (
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
            ) != 0
        if (!writable) return false
        if (data.isEmpty()) return true
        val noResponse = withoutResponse ?: ((props and BluetoothGattCharacteristic.PROPERTY_WRITE) == 0)
        return enqueue(ch, data, noResponse)
    }

    private fun enqueue(ch: BluetoothGattCharacteristic, data: ByteArray, withoutResponse: Boolean): Boolean {
        if (gatt == null || !connected || !servicesReady) return false
        val chunkSize = (mtu - 3).coerceIn(MIN_CHUNK, 512)
        val chunks = (data.size + chunkSize - 1) / chunkSize
        if (chunks > MAX_WRITE_QUEUE || writeQueue.size + chunks > MAX_WRITE_QUEUE) {
            listener?.onError("BLE write queue overflow")
            return false
        }
        var offset = 0
        while (offset < data.size) {
            val end = (offset + chunkSize).coerceAtMost(data.size)
            writeQueue.addLast(PendingWrite(ch, data.copyOfRange(offset, end), withoutResponse))
            offset = end
        }
        if (!writeBusy) processWriteQueue()
        return true
    }

    @SuppressLint("MissingPermission")
    private fun processWriteQueue() {
        val g = gatt
        if (g == null) {
            writeQueue.clear()
            writeBusy = false
            return
        }
        val op = writeQueue.removeFirstOrNull()
        if (op == null) {
            writeBusy = false
            currentWriteWithoutResponse = false
            return
        }
        writeBusy = true
        currentWriteWithoutResponse = op.withoutResponse
        val type = if (op.withoutResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        val started = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(op.characteristic, op.data, type) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                op.characteristic.value = op.data
                @Suppress("DEPRECATION")
                op.characteristic.writeType = type
                @Suppress("DEPRECATION")
                g.writeCharacteristic(op.characteristic)
            }
        } catch (e: SecurityException) {
            false
        }
        if (!started) {
            writeQueue.clear()
            writeBusy = false
            listener?.onError("BLE write failed")
        } else if (op.withoutResponse) {
            handler.post { processWriteQueue() }
        } else {
            handler.removeCallbacks(writeTimeout)
            handler.postDelayed(writeTimeout, WRITE_TIMEOUT_MS)
        }
    }

    @SuppressLint("MissingPermission")
    fun read(uuid: UUID): Boolean {
        val g = gatt ?: return false
        if (!connected || !servicesReady) return false
        val ch = findCharacteristic(g, uuid) ?: return false
        if (ch.properties and BluetoothGattCharacteristic.PROPERTY_READ == 0) return false
        return try {
            @Suppress("DEPRECATION")
            g.readCharacteristic(ch)
        } catch (e: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun setNotify(uuid: UUID, enable: Boolean): Boolean {
        val g = gatt ?: return false
        if (!connected || !servicesReady) return false
        val ch = findCharacteristic(g, uuid) ?: return false
        val props = ch.properties
        val notifiable = props and (
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                BluetoothGattCharacteristic.PROPERTY_INDICATE
            ) != 0
        if (!notifiable) return false
        val ok = try {
            g.setCharacteristicNotification(ch, enable)
        } catch (e: SecurityException) {
            false
        }
        if (!ok) return false
        if (enable) subscribed.add(uuid) else subscribed.remove(uuid)
        val descriptor = ch.getDescriptor(profile.cccd) ?: return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val value = if (enable) {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            }
            val result = try {
                g.writeDescriptor(descriptor, value)
            } catch (e: SecurityException) {
                BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION
            }
            if (result != BluetoothStatusCodes.SUCCESS) {
                if (enable) {
                    subscribed.remove(uuid)
                    try {
                        g.setCharacteristicNotification(ch, false)
                    } catch (e: SecurityException) {
                    }
                }
                listener?.onError("Descriptor write failed: $result")
                return false
            }
        } else {
            descriptor.value = if (enable) {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            }
            val result = try {
                g.writeDescriptor(descriptor)
            } catch (e: SecurityException) {
                false
            }
            if (!result) {
                if (enable) {
                    subscribed.remove(uuid)
                    try {
                        g.setCharacteristicNotification(ch, false)
                    } catch (e: SecurityException) {
                    }
                }
                listener?.onError("Descriptor write failed")
                return false
            }
        }
        return true
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
            currentWriteWithoutResponse = false
            connected = false
            servicesReady = false
            descriptorPending = false
            closing = false
            bondRequested = false
            rediscoverCount = 0
            mtu = 23
            writeWithoutResponse = false
            subscribed.clear()
            cccdQueue.clear()
            cccdActive = null
            unregisterBondReceiver()
            handler.removeCallbacks(writeTimeout)
            handler.removeCallbacks(discoverFallback)
            handler.removeCallbacks(rediscover)
            handler.removeCallbacks(discoveryTimeout)
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
