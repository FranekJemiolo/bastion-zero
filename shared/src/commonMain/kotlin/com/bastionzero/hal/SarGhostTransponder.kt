package com.bastionzero.hal

/**
 * Emergency cellular burst packet for airborne SAR detection.
 */
data class SarBurstPacket(
    val burstId: Long,
    val timestampMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Float,
    val traumaTriageCode: String,
    val batteryPercent: Int,
    val burstDurationMs: Long,
    val estimatedRfPowerDbm: Float
)

/**
 * Status and health metrics for the airborne SAR transponder.
 */
data class TransponderStatus(
    val isArmed: Boolean,
    val totalBurstsSent: Int,
    val lastBurstTimeMillis: Long,
    val nextAllowedBurstTimeMillis: Long,
    val isBatteryInhibited: Boolean,
    val isThermalInhibited: Boolean,
    val energyConsumedMilliwattHours: Float
)

/**
 * Airborne Search & Rescue (SAR) Ghost Transponder.
 *
 * Orchestrates controlled, ultra-low-duty-cycle emergency cellular modem
 * connection setup attempts (e.g. 500 ms burst every 15 minutes) to emit
 * detectable electromagnetic breadcrumbs for airborne SAR IMSI-catchers
 * and directional antenna arrays, without requiring SIM card registration
 * or an active cell network.
 */
class SarGhostTransponder(
    val burstCooldownMillis: Long = 15 * 60 * 1000L, // 15-minute cooldown
    val burstDurationMs: Long = 500L,                // 500 ms transmit pulse
    val minBatteryPercentThreshold: Int = 5,         // Prevent voltage sag
    val maxBatteryTempCelsius: Float = 45.0f         // Thermal safety lockout
) {

    private var isArmed: Boolean = false
    private var totalBursts: Int = 0
    private var lastBurstTimestamp: Long = 0L
    private var totalEnergyConsumedMwh: Float = 0f

    /**
     * Arms or disarms the periodic transponder beacon.
     */
    fun setArmed(armed: Boolean) {
        this.isArmed = armed
    }

    /**
     * Attempts to execute an emergency SAR RF burst.
     *
     * @param currentTimeMillis Current system epoch millis.
     * @param batteryPercent Current battery state of charge (0 - 100).
     * @param batteryTempCelsius Current battery temperature from thermistor.
     * @param lat Current latitude coordinate.
     * @param lon Current longitude coordinate.
     * @param altMeters Current altitude in meters.
     * @param traumaCode Current trauma triage code (e.g. "TRIAGE_RED_IMPACT_14G").
     * @return SarBurstPacket if burst succeeded, null if gated by safety or cooldown.
     */
    fun triggerBurst(
        currentTimeMillis: Long,
        batteryPercent: Int,
        batteryTempCelsius: Float,
        lat: Double,
        lon: Double,
        altMeters: Float,
        traumaCode: String
    ): SarBurstPacket? {
        if (!isArmed) return null

        // 1. Safety Gates
        if (batteryPercent <= minBatteryPercentThreshold) return null
        if (batteryTempCelsius >= maxBatteryTempCelsius) return null

        // 2. Cooldown Gate
        if (lastBurstTimestamp > 0 && (currentTimeMillis - lastBurstTimestamp) < burstCooldownMillis) {
            return null
        }

        // 3. Synthesize and fire RF pulse
        totalBursts++
        lastBurstTimestamp = currentTimeMillis

        // Estimated energy: 2.5W RF modem * 0.5s = 1.25 Joules (~0.35 mWh)
        totalEnergyConsumedMwh += 0.35f

        return SarBurstPacket(
            burstId = totalBursts.toLong(),
            timestampMillis = currentTimeMillis,
            latitude = lat,
            longitude = lon,
            altitudeMeters = altMeters,
            traumaTriageCode = traumaCode,
            batteryPercent = batteryPercent,
            burstDurationMs = burstDurationMs,
            estimatedRfPowerDbm = 23.0f // Standard Class 3 UE max Tx power (+23 dBm / 200 mW)
        )
    }

    /**
     * Queries the transponder status.
     */
    fun getStatus(currentTimeMillis: Long, currentBatteryPercent: Int, currentBatteryTemp: Float): TransponderStatus {
        val batteryInhibited = currentBatteryPercent <= minBatteryPercentThreshold
        val thermalInhibited = currentBatteryTemp >= maxBatteryTempCelsius
        val nextAllowed = if (lastBurstTimestamp > 0) lastBurstTimestamp + burstCooldownMillis else currentTimeMillis

        return TransponderStatus(
            isArmed = isArmed,
            totalBurstsSent = totalBursts,
            lastBurstTimeMillis = lastBurstTimestamp,
            nextAllowedBurstTimeMillis = nextAllowed,
            isBatteryInhibited = batteryInhibited,
            isThermalInhibited = thermalInhibited,
            energyConsumedMilliwattHours = totalEnergyConsumedMwh
        )
    }

    /**
     * Resets counters and timers.
     */
    fun reset() {
        totalBursts = 0
        lastBurstTimestamp = 0L
        totalEnergyConsumedMwh = 0f
    }
}
