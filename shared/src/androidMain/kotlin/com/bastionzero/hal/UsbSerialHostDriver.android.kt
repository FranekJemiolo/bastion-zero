package com.bastionzero.hal

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Android actual implementation of [UsbSerialHostDriver] using Android's native [UsbManager].
 * Identifies CDC-ACM, FTDI, CP210x, and CH34x survival serial hardware peripherals.
 */
actual class UsbSerialHostDriver(
    private val context: Context? = null,
) : UsbSerialConnection {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var readJob: Job? = null

    private val _isConnected = MutableStateFlow(false)
    actual override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectedDevice = MutableStateFlow<UsbSerialDeviceInfo?>(null)
    actual override val connectedDevice: StateFlow<UsbSerialDeviceInfo?> = _connectedDevice.asStateFlow()

    private val _receivedBytes = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    actual override val receivedBytes: SharedFlow<ByteArray> = _receivedBytes.asSharedFlow()

    private val usbManager: UsbManager? = context?.getSystemService(Context.USB_SERVICE) as? UsbManager
    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null

    actual override fun open(baudRate: Int): Boolean {
        if (_isConnected.value) return true
        val manager = usbManager ?: return false

        val deviceList = manager.deviceList
        if (deviceList.isEmpty()) return false

        // Pick the first serial-capable device
        var targetDevice: UsbDevice? = null
        var identifiedChipset = UsbChipsetType.NONE

        for (device in deviceList.values) {
            val chipset = identifyChipset(device.vendorId, device.productId)
            if (chipset != UsbChipsetType.NONE) {
                targetDevice = device
                identifiedChipset = chipset
                break
            }
        }

        val device = targetDevice ?: return false
        val conn = manager.openDevice(device) ?: return false

        // Find data interface and bulk endpoints
        var foundInterface: UsbInterface? = null
        var epIn: UsbEndpoint? = null
        var epOut: UsbEndpoint? = null

        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN && epIn == null) {
                        epIn = ep
                    } else if (ep.direction == UsbConstants.USB_DIR_OUT && epOut == null) {
                        epOut = ep
                    }
                }
            }
            if (epIn != null && epOut != null) {
                foundInterface = intf
                break
            }
        }

        if (foundInterface == null || epIn == null || epOut == null) {
            conn.close()
            return false
        }

        if (!conn.claimInterface(foundInterface, true)) {
            conn.close()
            return false
        }

        connection = conn
        usbInterface = foundInterface
        inEndpoint = epIn
        outEndpoint = epOut

        _connectedDevice.value = UsbSerialDeviceInfo(
            vendorId = device.vendorId,
            productId = device.productId,
            deviceName = device.deviceName,
            chipset = identifiedChipset,
            serialNumber = try { device.serialNumber } catch (_: Throwable) { null }
        )
        _isConnected.value = true

        startReadLoop()
        return true
    }

    actual override fun close() {
        readJob?.cancel()
        readJob = null

        usbInterface?.let { intf ->
            connection?.releaseInterface(intf)
        }
        connection?.close()

        connection = null
        usbInterface = null
        inEndpoint = null
        outEndpoint = null

        _isConnected.value = false
        _connectedDevice.value = null
    }

    actual override fun write(data: ByteArray): Int {
        val conn = connection ?: return -1
        val ep = outEndpoint ?: return -1
        return conn.bulkTransfer(ep, data, data.size, 1000)
    }

    actual override fun setDtrRts(dtr: Boolean, rts: Boolean) {
        val conn = connection ?: return
        val value = (if (dtr) 1 else 0) or (if (rts) 2 else 0)
        // Standard CDC-ACM SET_CONTROL_LINE_STATE
        conn.controlTransfer(0x21, 0x22, value, 0, null, 0, 1000)
    }

    private fun startReadLoop() {
        readJob = scope.launch {
            val buffer = ByteArray(1024)
            while (isActive && _isConnected.value) {
                val conn = connection
                val ep = inEndpoint
                if (conn != null && ep != null) {
                    val bytesRead = conn.bulkTransfer(ep, buffer, buffer.size, 200)
                    if (bytesRead > 0) {
                        val packet = buffer.copyOf(bytesRead)
                        _receivedBytes.emit(packet)
                    }
                }
            }
        }
    }

    private fun identifyChipset(vid: Int, pid: Int): UsbChipsetType {
        return when (vid) {
            0x0403 -> UsbChipsetType.FTDI            // FTDI FT232R / FT2232
            0x10C4 -> UsbChipsetType.CP210X          // Silicon Labs CP2102 / CP2104
            0x1A86 -> UsbChipsetType.CH34X           // WCH CH340 / CH341
            else -> {
                // Common CDC-ACM / Arduino / LilyGO / Raspberry Pi Pico VIDs
                if (vid == 0x2341 || vid == 0x2E8A || vid == 0x303A || vid == 0x1B4F) {
                    UsbChipsetType.CDC_ACM
                } else {
                    UsbChipsetType.GENERIC_UART
                }
            }
        }
    }
}
