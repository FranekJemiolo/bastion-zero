package com.bastionzero.hal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GnssIntegrityReport(
    val isSpoofed: Boolean,
    val agcLevelDb: Float,
    val baselineAgcDb: Float,
    val clockDriftNanosPerSec: Long,
    val satelliteCount: Int,
    val confidence: Float,
    val fallbackToDeadReckoning: Boolean,
    val timestampNs: Long,
)

/**
 * GNSS Anti-Spoofing & Electronic Warfare Defense Engine.
 *
 * Implements mathematical detection of GNSS spoofers:
 * 1. Automatic Gain Control (AGC) power anomaly detection: Real satellite signals from 20,000 km
 *    are at ~-160 dBW. Ground-based military spoofers scream with high RF power, causing
 *    the receiver AGC to spike by > 12-15 dB.
 * 2. Hardware Clock bias drift detection: Fake signals synthesized locally produce discontinuous
 *    microsecond jumps in the hardware clock drift.
 */
class GnssSpoofingDetector(
    private val agcSpikeThresholdDb: Float = 14.0f,
    private val maxRealisticClockDriftNanos: Long = 100_000L, // 100 microseconds/sec
) {
    private val _integrityReport = MutableStateFlow(
        GnssIntegrityReport(
            isSpoofed = false,
            agcLevelDb = 0f,
            baselineAgcDb = 0f,
            clockDriftNanosPerSec = 0L,
            satelliteCount = 0,
            confidence = 1.0f,
            fallbackToDeadReckoning = false,
            timestampNs = 0L,
        )
    )
    val integrityReport: StateFlow<GnssIntegrityReport> = _integrityReport.asStateFlow()

    private var baselineAgcDb = 0f
    private var agcSampleCount = 0
    private var isBaselineLocked = false

    fun processGnssMeasurement(
        agcLevelDb: Float,
        clockDriftNanosPerSec: Long,
        satelliteCount: Int,
        timestampNs: Long,
    ) {
        if (!isBaselineLocked) {
            baselineAgcDb = (baselineAgcDb * agcSampleCount + agcLevelDb) / (agcSampleCount + 1)
            agcSampleCount++
            if (agcSampleCount >= 20) {
                isBaselineLocked = true
            }
        }

        val agcDelta = agcLevelDb - baselineAgcDb
        val isAgcAbnormal = isBaselineLocked && agcDelta >= agcSpikeThresholdDb
        val isClockAbnormal = kotlin.math.abs(clockDriftNanosPerSec) > maxRealisticClockDriftNanos

        val isSpoofed = isAgcAbnormal || isClockAbnormal
        val confidence = when {
            isAgcAbnormal && isClockAbnormal -> 0.98f
            isAgcAbnormal -> 0.88f
            isClockAbnormal -> 0.75f
            else -> 0.05f
        }

        _integrityReport.value = GnssIntegrityReport(
            isSpoofed = isSpoofed,
            agcLevelDb = agcLevelDb,
            baselineAgcDb = baselineAgcDb,
            clockDriftNanosPerSec = clockDriftNanosPerSec,
            satelliteCount = satelliteCount,
            confidence = confidence,
            fallbackToDeadReckoning = isSpoofed,
            timestampNs = timestampNs,
        )
    }

    fun resetBaseline() {
        baselineAgcDb = 0f
        agcSampleCount = 0
        isBaselineLocked = false
    }
}
