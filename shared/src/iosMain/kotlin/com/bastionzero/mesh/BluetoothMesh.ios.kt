package com.bastionzero.mesh

import com.bastionzero.proto.SurvivalPacket
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
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
import platform.CoreBluetooth.CBATTErrorSuccess
import platform.CoreBluetooth.CBATTRequest
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.CoreBluetooth.CBAttributePermissionsReadable
import platform.CoreBluetooth.CBAttributePermissionsWriteable
import platform.CoreBluetooth.CBCentral
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicPropertyNotify
import platform.CoreBluetooth.CBCharacteristicPropertyRead
import platform.CoreBluetooth.CBCharacteristicPropertyWrite
import platform.CoreBluetooth.CBCharacteristicPropertyWriteWithoutResponse
import platform.CoreBluetooth.CBCharacteristicWriteWithoutResponse
import platform.CoreBluetooth.CBError
import platform.CoreBluetooth.CBManagerState
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBMutableCharacteristic
import platform.CoreBluetooth.CBMutableService
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBPeripheralManager
import platform.CoreBluetooth.CBPeripheralManagerDelegateProtocol
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.Foundation.create
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class BluetoothMesh : MeshTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _incomingPackets = MutableSharedFlow<SurvivalPacket>(extraBufferCapacity = 64)
    override val incomingPackets: SharedFlow<SurvivalPacket> = _incomingPackets.asSharedFlow()

    private val _peerCount = MutableStateFlow(0)
    override val peerCount: StateFlow<Int> = _peerCount.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    override val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val serviceUuid = CBUUID.UUIDWithString(BASTION_SERVICE_UUID_STRING)
    private val charUuid = CBUUID.UUIDWithString(BASTION_CHARACTERISTIC_UUID_STRING)

    private var centralManager: CBCentralManager? = null
    private var peripheralManager: CBPeripheralManager? = null
    private var mutableCharacteristic: CBMutableCharacteristic? = null

    private val discoveredPeers = mutableSetOf<String>()
    private val activePeripherals = mutableMapOf<String, CBPeripheral>()
    private var latestOutboundPacketBytes: ByteArray? = null

    private val peripheralDelegate = object : NSObject(), CBPeripheralManagerDelegateProtocol {
        override fun peripheralManagerDidUpdateState(peripheral: CBPeripheralManager) {
            if (peripheral.state == CBManagerStatePoweredOn) {
                setupServiceAndAdvertise()
            } else {
                _isAdvertising.value = false
            }
        }

        override fun peripheralManagerDidStartAdvertising(peripheral: CBPeripheralManager, error: platform.Foundation.NSError?) {
            _isAdvertising.value = (error == null)
        }

        override fun peripheralManager(peripheral: CBPeripheralManager, didReceiveReadRequest: CBATTRequest) {
            if (didReceiveReadRequest.characteristic.UUID == charUuid) {
                val bytes = latestOutboundPacketBytes ?: ByteArray(0)
                didReceiveReadRequest.setValue(bytes.toNSData())
                peripheral.respondToRequest(didReceiveReadRequest, withResult = CBATTErrorSuccess)
            }
        }

        override fun peripheralManager(peripheral: CBPeripheralManager, didReceiveWriteRequests: List<*>) {
            for (req in didReceiveWriteRequests) {
                if (req is CBATTRequest && req.characteristic.UUID == charUuid) {
                    val data = req.value
                    if (data != null) {
                        parseAndEmit(data.toByteArray())
                    }
                    peripheral.respondToRequest(req, withResult = CBATTErrorSuccess)
                }
            }
        }
    }

    private val peripheralClientDelegate = object : NSObject(), CBPeripheralDelegateProtocol {
        override fun peripheral(peripheral: CBPeripheral, didDiscoverServices: platform.Foundation.NSError?) {
            val services = peripheral.services ?: return
            for (service in services) {
                if (service is CBService && service.UUID == serviceUuid) {
                    peripheral.discoverCharacteristics(listOf(charUuid), forService = service)
                }
            }
        }

        override fun peripheral(
            peripheral: CBPeripheral,
            didDiscoverCharacteristicsForService: CBService,
            error: platform.Foundation.NSError?,
        ) {
            val chars = didDiscoverCharacteristicsForService.characteristics ?: return
            for (c in chars) {
                if (c is CBCharacteristic && c.UUID == charUuid) {
                    peripheral.readValueForCharacteristic(c)
                }
            }
        }

        override fun peripheral(
            peripheral: CBPeripheral,
            didUpdateValueForCharacteristic: CBCharacteristic,
            error: platform.Foundation.NSError?,
        ) {
            val data = didUpdateValueForCharacteristic.value
            if (data != null) {
                parseAndEmit(data.toByteArray())
            }

            // Write our outbound packet if available
            val outbound = latestOutboundPacketBytes
            if (outbound != null && didUpdateValueForCharacteristic.UUID == charUuid) {
                peripheral.writeValue(
                    outbound.toNSData(),
                    forCharacteristic = didUpdateValueForCharacteristic,
                    type = CBCharacteristicWriteWithoutResponse,
                )
            }

            // Disconnect to conserve battery and allow scanning other peers
            centralManager?.cancelPeripheralConnection(peripheral)
        }
    }

    private val centralDelegate = object : NSObject(), CBCentralManagerDelegateProtocol {
        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            if (central.state == CBManagerStatePoweredOn) {
                startScanning()
            } else {
                _isScanning.value = false
            }
        }

        override fun centralManager(
            central: CBCentralManager,
            didDiscoverPeripheral: CBPeripheral,
            advertisementData: Map<Any?, *>,
            RSSI: NSNumber,
        ) {
            val peerId = didDiscoverPeripheral.identifier.UUIDString
            discoveredPeers.add(peerId)
            _peerCount.value = discoveredPeers.size

            activePeripherals[peerId] = didDiscoverPeripheral
            didDiscoverPeripheral.delegate = peripheralClientDelegate
            central.connectPeripheral(didDiscoverPeripheral, options = null)
        }

        override fun centralManager(central: CBCentralManager, didConnectPeripheral: CBPeripheral) {
            didConnectPeripheral.discoverServices(listOf(serviceUuid))
        }

        override fun centralManager(
            central: CBCentralManager,
            didDisconnectPeripheral: CBPeripheral,
            error: platform.Foundation.NSError?,
        ) {
            activePeripherals.remove(didDisconnectPeripheral.identifier.UUIDString)
        }
    }

    override fun start() {
        if (centralManager == null) {
            centralManager = CBCentralManager(centralDelegate, dispatch_get_main_queue())
        } else if (centralManager?.state == CBManagerStatePoweredOn) {
            startScanning()
        }

        if (peripheralManager == null) {
            peripheralManager = CBPeripheralManager(peripheralDelegate, dispatch_get_main_queue())
        } else if (peripheralManager?.state == CBManagerStatePoweredOn) {
            setupServiceAndAdvertise()
        }
    }

    override fun stop() {
        centralManager?.stopScan()
        _isScanning.value = false

        peripheralManager?.stopAdvertising()
        _isAdvertising.value = false

        discoveredPeers.clear()
        _peerCount.value = 0
    }

    override fun broadcast(packet: SurvivalPacket) {
        val bytes = packet.encode()
        latestOutboundPacketBytes = bytes

        val char = mutableCharacteristic
        if (char != null) {
            char.setValue(bytes.toNSData())
            peripheralManager?.updateValue(
                bytes.toNSData(),
                forCharacteristic = char,
                onSubscribedCentrals = null,
            )
        }
    }

    private fun startScanning() {
        val cm = centralManager ?: return
        if (cm.state != CBManagerStatePoweredOn) return

        // CRITICAL FOR IOS BACKGROUND:
        // Scanning in background MUST specify non-nil service UUID list.
        cm.scanForPeripheralsWithServices(
            serviceUUIDs = listOf(serviceUuid),
            options = null,
        )
        _isScanning.value = true
    }

    private fun setupServiceAndAdvertise() {
        val pm = peripheralManager ?: return
        if (pm.state != CBManagerStatePoweredOn) return

        pm.removeAllServices()

        val char = CBMutableCharacteristic(
            type = charUuid,
            properties = CBCharacteristicPropertyRead or
                CBCharacteristicPropertyWrite or
                CBCharacteristicPropertyWriteWithoutResponse or
                CBCharacteristicPropertyNotify,
            value = latestOutboundPacketBytes?.toNSData(),
            permissions = CBAttributePermissionsReadable or CBAttributePermissionsWriteable,
        )
        mutableCharacteristic = char

        val service = CBMutableService(type = serviceUuid, primary = true)
        service.setCharacteristics(listOf(char))
        pm.addService(service)

        // Start advertising with Service UUID
        pm.startAdvertising(mapOf(CBAdvertisementDataServiceUUIDsKey to listOf(serviceUuid)))
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

    private fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        if (size == 0) return ByteArray(0)
        val bytes = ByteArray(size)
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, length)
        }
        return bytes
    }

    private fun ByteArray.toNSData(): NSData {
        if (isEmpty()) return NSData()
        return usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
        }
    }
}
