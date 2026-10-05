package com.bastionzero.trauma

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.round
import kotlin.math.sqrt

enum class TraumaSeverity {
    NORMAL,
    MODERATE_IMPACT,   // 5G - 10G
    SEVERE_TRAUMA,     // 10G - 20G
    CATASTROPHIC,      // > 20G
}

data class MotionTelemetry(
    val timestampNs: Long,
    val axG: Float,
    val ayG: Float,
    val azG: Float,
    val gxDegPerSec: Float,
    val gyDegPerSec: Float,
    val gzDegPerSec: Float,
) {
    val totalAccelerationG: Float
        get() = sqrt(axG * axG + ayG * ayG + azG * azG)

    val totalRotationalRateDegPerSec: Float
        get() = sqrt(gxDegPerSec * gxDegPerSec + gyDegPerSec * gyDegPerSec + gzDegPerSec * gzDegPerSec)
}

data class TraumaEvent(
    val timestampNs: Long,
    val peakAccelerationG: Float,
    val estimatedDropHeightMeters: Float,
    val freeFallDurationSec: Float,
    val peakRotationalRateDegPerSec: Float,
    val severity: TraumaSeverity,
    val lockScreenTriageAlert: String,
)

data class TraumaLoggerState(
    val currentAccelerationG: Float = 1.0f,
    val peakRecordedG: Float = 1.0f,
    val isInFreeFall: Boolean = false,
    val activeSeverity: TraumaSeverity = TraumaSeverity.NORMAL,
    val lastTraumaEvent: TraumaEvent? = null,
    val circularBufferCount: Int = 0,
)

/**
 * Autonomous Kinematic Trauma Black-Box Logger.
 * Records high-G impacts, rotational shocks, and free-fall duration during avalanches,
 * ravine falls, or building structural collapses. Generates instant lock-screen medical
 * alerts for first responders when the victim is incapacitated.
 */
class KinematicTraumaLogger(
    private val highGThreshold: Float = 10.0f,
    private val moderateGThreshold: Float = 5.0f,
    private val freeFallThresholdG: Float = 0.25f,
    private val bufferCapacity: Int = 100,
) {
    private val _state = MutableStateFlow(TraumaLoggerState())
    val state: StateFlow<TraumaLoggerState> = _state.asStateFlow()

    private val circularBuffer = ArrayList<MotionTelemetry>(bufferCapacity)
    private var freeFallStartNs: Long? = null

    /**
     * Process high-rate accelerometer (in Gs, where 1.0G = 9.80665 m/s^2) and
     * gyroscope (in degrees per second) telemetry from native sensor HAL.
     */
    fun processSample(
        timestampNs: Long,
        axG: Float,
        ayG: Float,
        azG: Float,
        gxDegPerSec: Float = 0f,
        gyDegPerSec: Float = 0f,
        gzDegPerSec: Float = 0f,
    ): TraumaEvent? {
        val sample = MotionTelemetry(
            timestampNs = timestampNs,
            axG = axG,
            ayG = ayG,
            azG = azG,
            gxDegPerSec = gxDegPerSec,
            gyDegPerSec = gyDegPerSec,
            gzDegPerSec = gzDegPerSec,
        )

        // Maintain fixed-size circular memory buffer (no disk I/O)
        if (circularBuffer.size >= bufferCapacity) {
            circularBuffer.removeAt(0)
        }
        circularBuffer.add(sample)

        val totalG = sample.totalAccelerationG
        val rotRate = sample.totalRotationalRateDegPerSec

        // 1. Detect near-zero G state (Free Fall)
        if (totalG < freeFallThresholdG) {
            if (freeFallStartNs == null) {
                freeFallStartNs = timestampNs
            }
        }

        // 2. Detect Impact & Check Free-fall transition
        var traumaEvent: TraumaEvent? = null
        if (totalG >= moderateGThreshold) {
            val freeFallDurationSec = freeFallStartNs?.let { start ->
                val durationNanos = (timestampNs - start).coerceAtLeast(0L)
                durationNanos.toDouble() / 1_000_000_000.0
            }?.toFloat() ?: 0f

            // Physics drop height: h = 0.5 * g * t^2
            val dropHeightMeters = if (freeFallDurationSec > 0.05f) {
                0.5f * 9.80665f * freeFallDurationSec * freeFallDurationSec
            } else {
                0f
            }

            val severity = when {
                totalG >= 20.0f -> TraumaSeverity.CATASTROPHIC
                totalG >= highGThreshold -> TraumaSeverity.SEVERE_TRAUMA
                else -> TraumaSeverity.MODERATE_IMPACT
            }

            val alertString = formatTriageAlert(
                totalG = totalG,
                dropHeightMeters = dropHeightMeters,
                freeFallSec = freeFallDurationSec,
                rotRate = rotRate,
                severity = severity,
            )

            traumaEvent = TraumaEvent(
                timestampNs = timestampNs,
                peakAccelerationG = totalG,
                estimatedDropHeightMeters = dropHeightMeters,
                freeFallDurationSec = freeFallDurationSec,
                peakRotationalRateDegPerSec = rotRate,
                severity = severity,
                lockScreenTriageAlert = alertString,
            )

            // Reset free-fall state on impact
            freeFallStartNs = null
        } else if (totalG >= 0.8f && freeFallStartNs != null) {
            // Gradual recovery without impact
            val elapsed = (timestampNs - (freeFallStartNs ?: timestampNs)) / 1_000_000_000.0
            if (elapsed > 3.0) {
                freeFallStartNs = null
            }
        }

        val currentPeak = maxOf(_state.value.peakRecordedG, totalG)
        _state.value = _state.value.copy(
            currentAccelerationG = totalG,
            peakRecordedG = currentPeak,
            isInFreeFall = freeFallStartNs != null,
            activeSeverity = traumaEvent?.severity ?: _state.value.activeSeverity,
            lastTraumaEvent = traumaEvent ?: _state.value.lastTraumaEvent,
            circularBufferCount = circularBuffer.size,
        )

        return traumaEvent
    }

    fun reset() {
        circularBuffer.clear()
        freeFallStartNs = null
        _state.value = TraumaLoggerState()
    }

    private fun formatTriageAlert(
        totalG: Float,
        dropHeightMeters: Float,
        freeFallSec: Float,
        rotRate: Float,
        severity: TraumaSeverity,
    ): String {
        val gStr = totalG.formatDecimals(1)
        val hStr = dropHeightMeters.formatDecimals(1)
        val feet = (dropHeightMeters * 3.28084f).formatDecimals(1)
        val rotStr = rotRate.formatDecimals(0)

        val prefix = when (severity) {
            TraumaSeverity.CATASTROPHIC -> "CRITICAL CATASTROPHIC TRAUMA ALERT"
            TraumaSeverity.SEVERE_TRAUMA -> "SEVERE TRAUMA IMPACT ALERT"
            TraumaSeverity.MODERATE_IMPACT -> "MODERATE IMPACT ALERT"
            TraumaSeverity.NORMAL -> "PATIENT TELEMETRY"
        }

        val fallContext = if (dropHeightMeters > 0.5f) {
            " · Vertical drop: ${hStr}m (${feet}ft) in ${freeFallSec.formatDecimals(2)}s freefall"
        } else {
            ""
        }

        val rotContext = if (rotRate > 180f) {
            " · Rotational shock: ${rotStr}°/s"
        } else {
            ""
        }

        return "$prefix: Patient sustained ${gStr}G impact$fallContext$rotContext. Suspected severe trauma. BlackBox triage code: BB-${severity.name}."
    }

    private fun Float.formatDecimals(decimals: Int): String {
        val factor = when (decimals) {
            0 -> 1.0
            1 -> 10.0
            2 -> 100.0
            3 -> 1000.0
            else -> 10.0
        }
        val rounded = round(this * factor) / factor
        if (decimals == 0) return rounded.toLong().toString()
        val str = rounded.toString()
        val parts = str.split('.')
        if (parts.size == 1) return "$str." + "0".repeat(decimals)
        val dec = parts[1].padEnd(decimals, '0').take(decimals)
        return "${parts[0]}.$dec"
    }
}
