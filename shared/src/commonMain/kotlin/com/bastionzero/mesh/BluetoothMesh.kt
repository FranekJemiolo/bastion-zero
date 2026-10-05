package com.bastionzero.mesh

import com.bastionzero.proto.SurvivalPacket
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

const val BASTION_SERVICE_UUID_STRING = "b0z00001-0000-1000-8000-00805f9b34fb"
const val BASTION_CHARACTERISTIC_UUID_STRING = "b0z00002-0000-1000-8000-00805f9b34fb"

/**
 * Common abstraction for the Bluetooth Low Energy transport layer.
 */
interface MeshTransport {
    /** Emits every packet received over BLE from nearby nodes. */
    val incomingPackets: SharedFlow<SurvivalPacket>

    /** Current estimate of unique nodes seen in the vicinity. */
    val peerCount: StateFlow<Int>

    val isScanning: StateFlow<Boolean>
    val isAdvertising: StateFlow<Boolean>

    fun start()
    fun stop()
    fun broadcast(packet: SurvivalPacket)
}

/**
 * Platform-specific Bluetooth Low Energy mesh implementation.
 * - androidMain: BluetoothLeAdvertiser + BluetoothLeScanner (GATT server/client)
 * - iosMain: CoreBluetooth CBCentralManager + CBPeripheralManager
 */
expect class BluetoothMesh : MeshTransport
