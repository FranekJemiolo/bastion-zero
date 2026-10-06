package com.bastionzero.hal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GnssRawMeasurementIngestorTest {

    @Test
    fun testRawEpochDataStructure() {
        val sat = RawGnssSatellite(
            svid = 14,
            constellationType = 1, // GPS
            carrierFrequencyHz = 1575420000.0,
            cn0DbHz = 38.5,
            agcDb = -8.5,
            pseudorangeRateMps = -120.4
        )

        val epoch = RawGnssEpoch(
            timestampNs = 1000000000L,
            clockDriftNanosPerSec = 540L,
            satellites = listOf(sat),
            averageAgcDb = -8.5f
        )

        assertEquals(1, epoch.satellites.size)
        assertEquals(14, epoch.satellites[0].svid)
        assertEquals(-8.5f, epoch.averageAgcDb)
    }

    @Test
    fun testIngestorFeedsSpoofingDetector() {
        val detector = GnssSpoofingDetector(agcSpikeThresholdDb = 14.0f)
        val ingestor = GnssRawMeasurementIngestor()

        ingestor.attachToSpoofingDetector(detector)
        ingestor.startListening()
        assertTrue(ingestor.isListening.value)

        // Baseline sampling
        for (i in 0..25) {
            detector.processGnssMeasurement(
                agcLevelDb = -10.0f,
                clockDriftNanosPerSec = 200L,
                satelliteCount = 12,
                timestampNs = i * 1000000000L
            )
        }
        assertFalse(detector.integrityReport.value.isSpoofed)

        // Spoofing attack: sudden +25 dB AGC spike
        detector.processGnssMeasurement(
            agcLevelDb = +15.0f,
            clockDriftNanosPerSec = 500_000L, // clock jump
            satelliteCount = 12,
            timestampNs = 30_000_000_000L
        )

        assertTrue(detector.integrityReport.value.isSpoofed)
        assertTrue(detector.integrityReport.value.fallbackToDeadReckoning)
        assertTrue(detector.integrityReport.value.confidence > 0.9f)

        ingestor.stopListening()
        assertFalse(ingestor.isListening.value)
    }
}
