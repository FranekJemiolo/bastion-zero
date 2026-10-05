package com.bastionzero.hub

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SdrSignalTriangulationTest {

    @Test
    fun testInsufficientSamplesReturnsNull() {
        val triangulation = SdrSignalTriangulation(minSectorSamples = 12)
        triangulation.recordSample(azimuthDeg = 45f, rssiDbm = -80f)
        assertNull(triangulation.computeBearing())
    }

    @Test
    fun testLocatesPeakRfEmitterAzimuth() {
        val triangulation = SdrSignalTriangulation(minSectorSamples = 12)

        // Sweep 360 degrees: background noise is -95 dBm, strong transmitter at 120 degrees (-45 dBm)
        for (az in 0 until 360 step 15) {
            val azFloat = az.toFloat()
            val rssi = if (az in 105..135) -45.0f else -95.0f
            triangulation.recordSample(azimuthDeg = azFloat, rssiDbm = rssi, frequencyMhz = 406.025f)
        }

        val target = triangulation.computeBearing()
        assertNotNull(target)
        // Transmitter located at ~120 degrees (within the 105-135 degree sector)
        assertTrue(target.peakAzimuthDeg in 100.0f..140.0f, "Expected azimuth near 120, got ${target.peakAzimuthDeg}")
        assertTrue(target.confidence > 0.7f)
        assertEquals("COSPAS-SARSAT Emergency Beacon", target.targetType)
    }
}
