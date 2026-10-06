package com.bastionzero.hal

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class UsbChipsetType {
    CDC_ACM,
    FTDI,
    CP210X,
    CH34X,
    GENERIC_UART,
    NONE,
}

data class UsbSerialDeviceInfo(
    val vendorId: Int,
    val productId: Int,
    val deviceName: String,
    val chipset: UsbChipsetType,
    val serialNumber: String? = null,
)

interface UsbSerialConnection {
    val isConnected: StateFlow<Boolean>
    val connectedDevice: StateFlow<UsbSerialDeviceInfo?>
    val receivedBytes: SharedFlow<ByteArray>

    fun open(baudRate: Int = 115200): Boolean
    fun close()
    fun write(data: ByteArray): Int
    fun setDtrRts(dtr: Boolean, rts: Boolean)
}

/**
 * Native USB-C OTG Serial Host Driver.
 * - androidMain: Android UsbManager host driver supporting CDC-ACM, FTDI, CP210x, and CH34x chipsets.
 * - iosMain: Simulated / MFi serial bridge adhering to iOS sandboxing constraints.
 */
expect class UsbSerialHostDriver : UsbSerialConnection {
    override val isConnected: StateFlow<Boolean>
    override val connectedDevice: StateFlow<UsbSerialDeviceInfo?>
    override val receivedBytes: SharedFlow<ByteArray>

    override fun open(baudRate: Int): Boolean
    override fun close()
    override fun write(data: ByteArray): Int
    override fun setDtrRts(dtr: Boolean, rts: Boolean)
}
