package com.bastionzero.hub

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UsbDeviceType {
    NONE,
    LORA_TRANSCEIVER,
    GEIGER_COUNTER,
    SDR_RECEIVER,
    WEATHER_ANEMOMETER,
}

data class UsbPeripheralStatus(
    val isConnected: Boolean = false,
    val deviceType: UsbDeviceType = UsbDeviceType.NONE,
    val baudRate: Int = 115200,
    val bytesReceivedTotal: Long = 0,
    val packetsFramedTotal: Int = 0,
    val lastError: String? = null,
)

/**
 * Universal USB-OTG Serial Peripheral Bridge.
 * Manages raw byte streams from external survival hardware (Geiger counters,
 * SDR dongles, external weather sensors) connected via USB-C OTG.
 */
class UsbSerialPeripheralBridge {

    private val _status = MutableStateFlow(UsbPeripheralStatus())
    val status: StateFlow<UsbPeripheralStatus> = _status.asStateFlow()

    private val rxBuffer = ArrayList<Byte>()

    fun onDeviceAttached(type: UsbDeviceType, baudRate: Int = 115200) {
        rxBuffer.clear()
        _status.value = _status.value.copy(
            isConnected = true,
            deviceType = type,
            baudRate = baudRate,
            lastError = null,
        )
    }

    fun onDeviceDetached() {
        rxBuffer.clear()
        _status.value = _status.value.copy(
            isConnected = false,
            deviceType = UsbDeviceType.NONE,
        )
    }

    /**
     * Ingest raw bytes from USB UART endpoint.
     * Uses delimiter-based framing (newline 0x0A or packet trailer 0x03 ETX).
     */
    fun ingestRawBytes(bytes: ByteArray): List<ByteArray> {
        val framedPackets = ArrayList<ByteArray>()
        _status.value = _status.value.copy(
            bytesReceivedTotal = _status.value.bytesReceivedTotal + bytes.size
        )

        for (b in bytes) {
            rxBuffer.add(b)
            // Delimiter detection: newline (0x0A) or ETX (0x03)
            if (b == 0x0A.toByte() || b == 0x03.toByte()) {
                val packet = ByteArray(rxBuffer.size)
                for (i in 0 until rxBuffer.size) {
                    packet[i] = rxBuffer[i]
                }
                framedPackets.add(packet)
                rxBuffer.clear()
            }
        }

        if (framedPackets.isNotEmpty()) {
            _status.value = _status.value.copy(
                packetsFramedTotal = _status.value.packetsFramedTotal + framedPackets.size
            )
        }

        return framedPackets
    }
}
