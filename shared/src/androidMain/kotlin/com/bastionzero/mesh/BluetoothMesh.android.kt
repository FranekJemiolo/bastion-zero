package com.bastionzero.mesh

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.bastionzero.proto.SurvivalPacket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okio.ByteString.Companion.toByteString

actual class BluetoothMesh(
    private val context: Context,
) : MeshTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _incomingPackets = MutableSharedFlow<SurvivalPacket>(extraBufferCapacity = 64)
    override val incomingPackets: SharedFlow<SurvivalPacket> = _incomingPackets.asSharedFlow()

    private val _peerCount = MutableStateFlow(0)
    override val peerCount: StateFlow<Int> = _peerCount.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    override val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val serviceUuid = UUID.fromString(BASTION_SERVICE_UUID_STRING)
    private val charUuid = UUID.fromString(BASTION_CHARACTERISTIC_UUID_STRING)

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var gattServer: BluetoothGattServer? = null

    private val discoveredPeers = ConcurrentHashMap<String, Long>()
    private var latestOutboundPacketBytes: ByteArray? = null

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            _isAdvertising.value = true
        }

        override fun onStartFailure(errorCode: Int) {
            _isAdvertising.value = false
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val device = result.device ?: return
            val address = device.address ?: return

            discoveredPeers[address] = System.currentTimeMillis()
            _peerCount.value = discoveredPeers.size

            // Attempt GATT exchange with discovered peripheral
            connectToPeer(device)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?,
        ) {
            if (characteristic?.uuid == charUuid) {
                val data = latestOutboundPacketBytes ?: ByteArray(0)
                val chunk = if (offset < data.size) data.copyOfRange(offset, data.size) else ByteArray(0)
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, chunk)
            } else {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            if (characteristic?.uuid == charUuid && value != null) {
                parseAndEmit(value)
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }
            } else if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }
    }

    override fun start() {
        if (!hasPermissions()) return
        startGattServer()
        startAdvertising()
        startScanning()
    }

    override fun stop() {
        stopScanning()
        stopAdvertising()
        stopGattServer()
        discoveredPeers.clear()
        _peerCount.value = 0
    }

    override fun broadcast(packet: SurvivalPacket) {
        val bytes = packet.encode()
        latestOutboundPacketBytes = bytes

        // Update local characteristic
        val service = gattServer?.getService(serviceUuid)
        val char = service?.getCharacteristic(charUuid)
        if (char != null) {
            char.value = bytes
        }

        // Restart advertising with fresh service payload hint if already advertising
        if (_isAdvertising.value) {
            stopAdvertising()
            startAdvertising()
        }
    }

    private fun startAdvertising() {
        val a = adapter?.bluetoothLeAdvertiser ?: return
        advertiser = a
        if (!hasAdvertisePermission()) return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()

        val dataBuilder = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(serviceUuid))
            .setIncludeDeviceName(false)

        latestOutboundPacketBytes?.let { bytes ->
            val beaconSlice = bytes.take(16).toByteArray()
            dataBuilder.addServiceData(ParcelUuid(serviceUuid), beaconSlice)
        }

        try {
            a.startAdvertising(settings, dataBuilder.build(), advertiseCallback)
        } catch (_: SecurityException) {
            _isAdvertising.value = false
        }
    }

    private fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (_: SecurityException) {}
        _isAdvertising.value = false
    }

    private fun startScanning() {
        val s = adapter?.bluetoothLeScanner ?: return
        scanner = s
        if (!hasScanPermission()) return

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(serviceUuid))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        try {
            s.startScan(listOf(filter), settings, scanCallback)
            _isScanning.value = true
        } catch (_: SecurityException) {
            _isScanning.value = false
        }
    }

    private fun stopScanning() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {}
        _isScanning.value = false
    }

    private fun startGattServer() {
        if (!hasConnectPermission()) return
        try {
            val server = bluetoothManager?.openGattServer(context, gattServerCallback) ?: return
            gattServer = server

            val service = BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            val characteristic = BluetoothGattCharacteristic(
                charUuid,
                BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE,
            )
            service.addCharacteristic(characteristic)
            server.addService(service)
        } catch (_: SecurityException) {}
    }

    private fun stopGattServer() {
        try {
            gattServer?.close()
        } catch (_: SecurityException) {}
        gattServer = null
    }

    private fun connectToPeer(device: BluetoothDevice) {
        if (!hasConnectPermission()) return
        try {
            device.connectGatt(
                context,
                false,
                object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            gatt?.discoverServices()
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            gatt?.close()
                        }
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                        if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                            val service = gatt.getService(serviceUuid)
                            val characteristic = service?.getCharacteristic(charUuid)
                            if (characteristic != null) {
                                gatt.readCharacteristic(characteristic)
                            } else {
                                gatt.disconnect()
                            }
                        } else {
                            gatt?.disconnect()
                        }
                    }

                    override fun onCharacteristicRead(
                        gatt: BluetoothGatt?,
                        characteristic: BluetoothGattCharacteristic?,
                        status: Int,
                    ) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            characteristic?.value?.let { parseAndEmit(it) }

                            // If we have an outbound packet to push to this peer, write it
                            val outbound = latestOutboundPacketBytes
                            if (outbound != null && characteristic != null) {
                                characteristic.value = outbound
                                gatt?.writeCharacteristic(characteristic)
                            } else {
                                gatt?.disconnect()
                            }
                        } else {
                            gatt?.disconnect()
                        }
                    }

                    override fun onCharacteristicWrite(
                        gatt: BluetoothGatt?,
                        characteristic: BluetoothGattCharacteristic?,
                        status: Int,
                    ) {
                        gatt?.disconnect()
                    }
                },
                BluetoothDevice.TRANSPORT_LE,
            )
        } catch (_: SecurityException) {}
    }

    private fun parseAndEmit(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        try {
            val packet = SurvivalPacket.ADAPTER.decode(bytes.toByteString())
            scope.launch {
                _incomingPackets.emit(packet)
            }
        } catch (_: Throwable) {
            // Malformed packet
        }
    }

    private fun hasPermissions(): Boolean =
        hasScanPermission() && hasAdvertisePermission() && hasConnectPermission()

    private fun hasScanPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }

    private fun hasAdvertisePermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }

    private fun hasConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
}
