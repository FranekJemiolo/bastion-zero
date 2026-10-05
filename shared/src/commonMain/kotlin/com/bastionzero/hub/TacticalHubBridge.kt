package com.bastionzero.hub

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.round

enum class RadiationHazardLevel {
    BACKGROUND_NORMAL,     // < 0.3 uSv/h
    ELEVATED,              // 0.3 - 10 uSv/h
    HAZARDOUS,             // 10 - 100 uSv/h
    ACUTE_EXCLUSION_ZONE,  // > 100 uSv/h
}

data class RadiationTelemetry(
    val currentDoseRateMicroSvPerHour: Float,
    val accumulatedDoseMicroSv: Float,
    val safeStayTimeHoursRemaining: Float,
    val hazardLevel: RadiationHazardLevel,
    val isExclusionZoneTriggered: Boolean,
)

data class TacticalHubState(
    val isLoRaConnected: Boolean = false,
    val loraFrequencyMhz: Float = 915.0f,
    val packetsTransmittedLoRa: Int = 0,
    val packetsReceivedLoRa: Int = 0,
    val lastRadiationTelemetry: RadiationTelemetry? = null,
)

/**
 * Tactical Hub Peripheral Bridge for external USB-OTG and LoRa transceivers.
 * Decodes serial telemetry from external sensors (Geiger counters / dosimeters,
 * weather stations) and encapsulates Protobuf SurvivalPackets into LoRa radio frames.
 */
class TacticalHubBridge(
    private val maxEmergencyDoseMicroSv: Float = 250_000.0f, // 250 mSv acute emergency limit
) {
    companion object {
        const val LORA_SYNC_WORD: Short = 0xBA70.toShort()
    }

    private val _state = MutableStateFlow(TacticalHubState())
    val state: StateFlow<TacticalHubState> = _state.asStateFlow()

    private var accumulatedDoseMicroSv: Float = 0f
    private var lastReadingTimestampNs: Long? = null

    /**
     * Encapsulate a binary payload (e.g. wire-serialized SurvivalPacket)
     * into a robust LoRa PHY frame with sync word and CRC16.
     */
    fun frameLoRaPacket(payload: ByteArray): ByteArray {
        val length = payload.size.coerceAtMost(255)
        val frame = ByteArray(4 + length)
        // Sync word (0xBA, 0x70)
        frame[0] = 0xBA.toByte()
        frame[1] = 0x70.toByte()
        frame[2] = length.toByte()
        payload.copyInto(frame, destinationOffset = 3, startIndex = 0, endIndex = length)

        val crc = computeCrc16(payload, length)
        frame[3 + length] = (crc and 0xFF).toByte()

        _state.value = _state.value.copy(
            packetsTransmittedLoRa = _state.value.packetsTransmittedLoRa + 1
        )
        return frame
    }

    /**
     * Validate and unwrap an incoming LoRa PHY frame.
     */
    fun unframeLoRaPacket(frame: ByteArray): ByteArray? {
        if (frame.size < 4) return null
        if (frame[0] != 0xBA.toByte() || frame[1] != 0x70.toByte()) return null

        val length = frame[2].toInt() and 0xFF
        if (frame.size < 4 + length) return null

        val payload = ByteArray(length)
        frame.copyInto(payload, destinationOffset = 0, startIndex = 3, endIndex = 3 + length)

        val expectedCrc = computeCrc16(payload, length)
        val receivedCrc = frame[3 + length].toInt() and 0xFF
        if (expectedCrc != receivedCrc) return null

        _state.value = _state.value.copy(
            packetsReceivedLoRa = _state.value.packetsReceivedLoRa + 1
        )
        return payload
    }

    /**
     * Process real-time dosimeter readings from a USB-C connected Geiger counter.
     * Calculates dose accumulation and remaining safe stay-time before acute sickness.
     */
    fun processRadiationSample(
        doseRateMicroSvPerHour: Float,
        timestampNs: Long,
    ): RadiationTelemetry {
        val elapsedSec = lastReadingTimestampNs?.let { start ->
            (timestampNs - start).coerceAtLeast(0L).toDouble() / 1_000_000_000.0
        }?.toFloat() ?: 1.0f

        lastReadingTimestampNs = timestampNs

        // Integrate dose: dose = rate * (elapsed_sec / 3600)
        val deltaDose = doseRateMicroSvPerHour * (elapsedSec / 3600.0f)
        accumulatedDoseMicroSv += deltaDose

        val remainingDose = (maxEmergencyDoseMicroSv - accumulatedDoseMicroSv).coerceAtLeast(0f)
        val safeStayTimeHours = if (doseRateMicroSvPerHour > 0.01f) {
            remainingDose / doseRateMicroSvPerHour
        } else {
            9999.0f
        }

        val hazardLevel = when {
            doseRateMicroSvPerHour > 100.0f -> RadiationHazardLevel.ACUTE_EXCLUSION_ZONE
            doseRateMicroSvPerHour > 10.0f -> RadiationHazardLevel.HAZARDOUS
            doseRateMicroSvPerHour > 0.3f -> RadiationHazardLevel.ELEVATED
            else -> RadiationHazardLevel.BACKGROUND_NORMAL
        }

        val telemetry = RadiationTelemetry(
            currentDoseRateMicroSvPerHour = doseRateMicroSvPerHour,
            accumulatedDoseMicroSv = accumulatedDoseMicroSv,
            safeStayTimeHoursRemaining = safeStayTimeHours,
            hazardLevel = hazardLevel,
            isExclusionZoneTriggered = hazardLevel == RadiationHazardLevel.ACUTE_EXCLUSION_ZONE,
        )

        _state.value = _state.value.copy(lastRadiationTelemetry = telemetry)
        return telemetry
    }

    fun setLoRaConnected(connected: Boolean, frequencyMhz: Float = 915.0f) {
        _state.value = _state.value.copy(
            isLoRaConnected = connected,
            loraFrequencyMhz = frequencyMhz,
        )
    }

    fun resetDose() {
        accumulatedDoseMicroSv = 0f
        lastReadingTimestampNs = null
        _state.value = _state.value.copy(lastRadiationTelemetry = null)
    }

    private fun computeCrc16(data: ByteArray, length: Int): Int {
        var crc = 0xFFFF
        for (i in 0 until length) {
            crc = (crc xor (data[i].toInt() and 0xFF))
            for (j in 0 until 8) {
                crc = if ((crc and 1) != 0) (crc ushr 1) xor 0xA001 else (crc ushr 1)
            }
        }
        return crc and 0xFF
    }
}
