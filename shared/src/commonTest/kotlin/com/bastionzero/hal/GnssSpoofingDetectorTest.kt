package com.bastionzero.hal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GnssSpoofingDetectorTest {

    @Test
    fun testAuthenticGnssMeasurements() {
        val detector = GnssSpoofingDetector(agcSpikeThresholdDb = 14.0f)

        // Lock baseline AGC with 20 normal samples around -5.0 dB
        for (i in 0 until 25) {
            detector.processGnssMeasurement(
                agcLevelDb = -5.0f,
                clockDriftNanosPerSec = 1200L,
                satelliteCount = 14,
                timestampNs = i * 1_000_000_000L,
            )
        }

        val report = detector.integrityReport.value
        assertFalse(report.isSpoofed)
        assertFalse(report.fallbackToDeadReckoning)
        assertEquals(-5.0f, report.baselineAgcDb, 0.1f)
    }

    @Test
    fun testSpoofingDetectedOnAgcPowerSpike() {
        val detector = GnssSpoofingDetector(agcSpikeThresholdDb = 14.0f)

        // Establish baseline at -5.0 dB
        for (i in 0 until 25) {
            detector.processGnssMeasurement(
                agcLevelDb = -5.0f,
                clockDriftNanosPerSec = 1000L,
                satelliteCount = 14,
                timestampNs = i * 1_000_000_000L,
            )
        }

        // Hostile ground transmitter blasts fake signal: AGC spikes to +15.0 dB (delta = +20 dB > 14 dB threshold)
        detector.processGnssMeasurement(
            agcLevelDb = 15.0f,
            clockDriftNanosPerSec = 1000L,
            satelliteCount = 18,
            timestampNs = 26_000_000_000L,
        )

        val report = detector.integrityReport.value
        assertTrue(report.isSpoofed, "Spoofing should be detected when AGC spikes abnormally")
        assertTrue(report.fallbackToDeadReckoning, "Should trigger fallback to Inertial Dead Reckoning")
        assertTrue(report.confidence >= 0.85f)
    }
}
