package com.bastionzero.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CelestialCompassEngineTest {

    private val engine = CelestialCompassEngine()

    @Test
    fun testSolarEphemerisCoordinates() {
        // Equinox solar noon in Warsaw (52.23° N, 21.01° E) on 2026-03-20 11:00 UTC
        val epochMillis = 1773994800000L

        val ephemeris = engine.computeEphemeris(
            epochMillis = epochMillis,
            latitudeDeg = 52.23,
            longitudeDeg = 21.01
        )

        assertTrue(ephemeris.isSunAboveHorizon, "Sun should be above horizon at noon")
        assertTrue(ephemeris.solarAzimuthDeg in 150f..210f, "Solar azimuth around noon should be roughly South (~180°)")
        assertTrue(ephemeris.solarElevationDeg in 30f..45f, "Solar elevation should be roughly 90 - lat = 37.8°")
        assertEquals(0.5f, ephemeris.polarisAzimuthDeg, 0.5f)
        assertEquals(52.23f, ephemeris.polarisElevationDeg, 0.5f)
    }

    @Test
    fun testShadowHeadingAlignmentNominal() {
        // Solar azimuth ~ 180° (due South).
        // Shadow points North (180° away from Sun, i.e. 360° / 0°).
        // If phone points due North, shadow is straight ahead (0° relative).
        // True heading should be 0° (North).
        val epochMillis = 1773994800000L
        val fix = engine.solveHeadingWithShadow(
            epochMillis = epochMillis,
            latitudeDeg = 52.23,
            longitudeDeg = 21.01,
            shadowRelativeBearingDeg = 0f,
            measuredMagneticHeadingDeg = 358f
        )

        assertFalse(fix.magneticAnomalyDetected, "Deviation of 2 degrees should be nominal")
        assertTrue(fix.calculatedTrueHeadingDeg in 350f..360f || fix.calculatedTrueHeadingDeg in 0f..10f)
    }

    @Test
    fun testMagneticAnomalyAlert() {
        val epochMillis = 1773994800000L
        // Measured magnetic heading is 90° (East), but true heading is ~0° (North)
        val fix = engine.solveHeadingWithShadow(
            epochMillis = epochMillis,
            latitudeDeg = 52.23,
            longitudeDeg = 21.01,
            shadowRelativeBearingDeg = 0f,
            measuredMagneticHeadingDeg = 90f
        )

        assertTrue(fix.magneticAnomalyDetected, "Magnetic deviation > 15° must trigger anomaly alarm")
        assertTrue(fix.magneticDeviationDeg > 80f)
    }
}
