package com.bastionzero.hal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InertialDeadReckoningTest {

    @Test
    fun testInitialAnchorState() {
        val pdr = InertialDeadReckoning(
            initialLat = 52.2297,
            initialLon = 21.0122,
            initialAltitude = 110f,
        )
        val state = pdr.state.value
        assertEquals(52.2297, state.lat, 0.0001)
        assertEquals(21.0122, state.lon, 0.0001)
        assertEquals(110f, state.altitudeMeters, 0.1f)
        assertEquals(0L, state.stepCount)
        assertEquals(0.0, state.totalDistanceMeters)
        assertEquals(1, state.breadcrumbs.size)
    }

    @Test
    fun testStepDetectionAndBreadcrumbProjection() {
        val pdr = InertialDeadReckoning(
            initialLat = 52.0,
            initialLon = 21.0,
            initialAltitude = 100f,
            customStrideMeters = 0.8f,
        )

        val t0 = 1_000_000_000L

        // Feed baseline gravity
        pdr.updateMotion(
            MotionSample(
                timestampNs = t0,
                ax = 0f, ay = 0f, az = 9.8f,
                gx = 0f, gy = 0f, gz = 0f,
                mx = 25f, my = 0f, mz = -40f, // North heading
            )
        )

        // Step 1: Acceleration rising peak above threshold (9.8 + 2.0 = 11.8 m/s^2)
        pdr.updateMotion(
            MotionSample(
                timestampNs = t0 + 100_000_000L,
                ax = 0f, ay = 0f, az = 12.0f,
                gx = 0f, gy = 0f, gz = 0f,
                mx = 25f, my = 0f, mz = -40f,
            )
        )

        // Valley / step completion
        pdr.updateMotion(
            MotionSample(
                timestampNs = t0 + 150_000_000L,
                ax = 0f, ay = 0f, az = 9.5f,
                gx = 0f, gy = 0f, gz = 0f,
                mx = 25f, my = 0f, mz = -40f,
            )
        )

        val stateAfterStep1 = pdr.state.value
        assertEquals(1L, stateAfterStep1.stepCount)
        assertEquals(0.8, stateAfterStep1.totalDistanceMeters, 0.01)
        assertTrue(stateAfterStep1.lat > 52.0, "Latitude should advance North")
        assertEquals(2, stateAfterStep1.breadcrumbs.size)

        // Test refractory period: A rapid peak within 100ms should be rejected
        pdr.updateMotion(
            MotionSample(
                timestampNs = t0 + 200_000_000L,
                ax = 0f, ay = 0f, az = 13.0f,
                gx = 0f, gy = 0f, gz = 0f,
                mx = 25f, my = 0f, mz = -40f,
            )
        )
        pdr.updateMotion(
            MotionSample(
                timestampNs = t0 + 220_000_000L,
                ax = 0f, ay = 0f, az = 9.0f,
                gx = 0f, gy = 0f, gz = 0f,
                mx = 25f, my = 0f, mz = -40f,
            )
        )
        // Step count must remain 1 because < 250ms refractory period
        assertEquals(1L, pdr.state.value.stepCount)

        // After refractory period (>250ms), next step should register
        pdr.updateMotion(
            MotionSample(
                timestampNs = t0 + 500_000_000L,
                ax = 0f, ay = 0f, az = 12.5f,
                gx = 0f, gy = 0f, gz = 0f,
                mx = 25f, my = 0f, mz = -40f,
            )
        )
        pdr.updateMotion(
            MotionSample(
                timestampNs = t0 + 550_000_000L,
                ax = 0f, ay = 0f, az = 9.2f,
                gx = 0f, gy = 0f, gz = 0f,
                mx = 25f, my = 0f, mz = -40f,
            )
        )
        assertEquals(2L, pdr.state.value.stepCount)
        assertEquals(1.6, pdr.state.value.totalDistanceMeters, 0.01)
    }

    @Test
    fun testBarometricAltitudeUpdate() {
        val pdr = InertialDeadReckoning()
        pdr.updateEnvironmental(
            EnvironmentalSample(
                timestampNs = 1_000_000_000L,
                pressureHpa = 995.0f,
                temperatureCelsius = 18.0f,
                altitudeMeters = 152.5f,
            )
        )
        assertEquals(152.5f, pdr.state.value.altitudeMeters, 0.1f)
    }
}
