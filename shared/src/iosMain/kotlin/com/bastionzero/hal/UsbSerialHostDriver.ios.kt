package com.bastionzero.hal

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS actual implementation of [UsbSerialHostDriver].
 * Adheres to Apple iOS USB-C sandboxing constraints (no arbitrary non-MFi USB serial access).
 */
actual class UsbSerialHostDriver() : UsbSerialConnection {

    private val _isConnected = MutableStateFlow(false)
    actual override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectedDevice = MutableStateFlow<UsbSerialDeviceInfo?>(null)
    actual override val connectedDevice: StateFlow<UsbSerialDeviceInfo?> = _connectedDevice.asStateFlow()

    private val _receivedBytes = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    actual override val receivedBytes: SharedFlow<ByteArray> = _receivedBytes.asSharedFlow()

    actual override fun open(baudRate: Int): Boolean {
        // iOS does not expose direct USB Host CDC-ACM endpoints to sandboxed applications
        return false
    }

    actual override fun close() {
        _isConnected.value = false
        _connectedDevice.value = null
    }

    actual override fun write(data: ByteArray): Int {
        return -1
    }

    actual override fun setDtrRts(dtr: Boolean, rts: Boolean) {
        // No-op on iOS
    }
}
