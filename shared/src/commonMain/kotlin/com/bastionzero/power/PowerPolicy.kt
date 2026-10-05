package com.bastionzero.power

/** Raw facts the governor reasons about. */
data class PowerInputs(
    val batteryPercent: Int,
    val isCharging: Boolean,
    /** Best-effort: Android uses TYPE_SIGNIFICANT_MOTION; iOS assumes true until CoreMotion (Phase 4). */
    val isMoving: Boolean,
)

enum class PowerTier { NORMAL, SAVER, CRITICAL, SURVIVAL }

/** What the rest of the app is allowed to spend. Consumers must honour these numbers. */
data class PowerPolicy(
    val tier: PowerTier,
    val uiRefreshHz: Int,
    val gpsIntervalMs: Long,
    val sensorIntervalMs: Long,
    /** Share of time BLE scanning may be active (Phase 3). */
    val meshScanDutyPercent: Int,
    /** 0f..1f window brightness. */
    val screenBrightness: Float,
    /** Whether holding a partial WakeLock is worth its cost (Android). */
    val holdWakeLock: Boolean,
)

/** Pure, deterministic policy function. All tuning lives here so it is testable. */
object PowerPolicyCalculator {
    private const val STILL_GPS_MULTIPLIER = 6
    private const val STILL_SENSOR_MULTIPLIER = 3

    fun tierFor(inputs: PowerInputs): PowerTier = when {
        inputs.isCharging -> PowerTier.NORMAL
        inputs.batteryPercent >= 50 -> PowerTier.NORMAL
        inputs.batteryPercent >= 20 -> PowerTier.SAVER
        inputs.batteryPercent >= 10 -> PowerTier.CRITICAL
        else -> PowerTier.SURVIVAL
    }

    fun compute(inputs: PowerInputs): PowerPolicy {
        val tier = tierFor(inputs)
        val base = when (tier) {
            PowerTier.NORMAL -> PowerPolicy(tier, 60, 1_000, 1_000, 50, 1.0f, true)
            PowerTier.SAVER -> PowerPolicy(tier, 30, 10_000, 5_000, 30, 0.6f, true)
            PowerTier.CRITICAL -> PowerPolicy(tier, 15, 300_000, 15_000, 15, 0.3f, false)
            PowerTier.SURVIVAL -> PowerPolicy(tier, 10, 600_000, 30_000, 5, 0.1f, false)
        }
        if (inputs.isMoving) return base
        return base.copy(
            gpsIntervalMs = base.gpsIntervalMs * STILL_GPS_MULTIPLIER,
            sensorIntervalMs = base.sensorIntervalMs * STILL_SENSOR_MULTIPLIER,
        )
    }
}
