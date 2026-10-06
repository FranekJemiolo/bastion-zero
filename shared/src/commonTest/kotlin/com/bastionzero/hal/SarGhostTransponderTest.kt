package com.bastionzero.hal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SarGhostTransponderTest {

    private val transponder = SarGhostTransponder(
        burstCooldownMillis = 60_000L, // 60s for testing
        burstDurationMs = 500L,
        minBatteryPercentThreshold = 5,
        maxBatteryTempCelsius = 45.0f
    )

    @Test
    fun testUnarmedTransponderYieldsNoBurst() {
        transponder.setArmed(false)
        val burst = transponder.triggerBurst(
            currentTimeMillis = 1000L,
            batteryPercent = 80,
            batteryTempCelsius = 25.0f,
            lat = 52.23,
            lon = 21.01,
            altMeters = 120f,
            traumaCode = "NOMINAL"
        )
        assertNull(burst)
    }

    @Test
    fun testArmedTransponderFiresBurstAndEnforcesCooldown() {
        transponder.setArmed(true)
        val burst1 = transponder.triggerBurst(
            currentTimeMillis = 1000L,
            batteryPercent = 50,
            batteryTempCelsius = 30.0f,
            lat = 52.23,
            lon = 21.01,
            altMeters = 120f,
            traumaCode = "TRIAGE_YELLOW_FALL"
        )
        assertNotNull(burst1)
        assertEquals(1L, burst1.burstId)
        assertEquals(23.0f, burst1.estimatedRfPowerDbm)

        // Attempt second burst immediately (within cooldown)
        val burst2 = transponder.triggerBurst(
            currentTimeMillis = 1500L,
            batteryPercent = 50,
            batteryTempCelsius = 30.0f,
            lat = 52.23,
            lon = 21.01,
            altMeters = 120f,
            traumaCode = "TRIAGE_YELLOW_FALL"
        )
        assertNull(burst2, "Second burst must be rejected during cooldown")

        // Advance beyond cooldown
        val burst3 = transponder.triggerBurst(
            currentTimeMillis = 62000L,
            batteryPercent = 49,
            batteryTempCelsius = 30.5f,
            lat = 52.23,
            lon = 21.01,
            altMeters = 120f,
            traumaCode = "TRIAGE_YELLOW_FALL"
        )
        assertNotNull(burst3, "Burst after cooldown should succeed")
        assertEquals(2L, burst3.burstId)
    }

    @Test
    fun testSafetyGatingOnBatteryAndTemperature() {
        transponder.setArmed(true)

        // Low battery lockout (<= 5%)
        val burstLowBat = transponder.triggerBurst(
            currentTimeMillis = 1000L,
            batteryPercent = 4,
            batteryTempCelsius = 25.0f,
            lat = 52.23,
            lon = 21.01,
            altMeters = 120f,
            traumaCode = "LOW_BAT"
        )
        assertNull(burstLowBat, "Low battery must prevent burst")

        // Overheat lockout (>= 45C)
        val burstOverheat = transponder.triggerBurst(
            currentTimeMillis = 1000L,
            batteryPercent = 50,
            batteryTempCelsius = 46.5f,
            lat = 52.23,
            lon = 21.01,
            altMeters = 120f,
            traumaCode = "OVERHEAT"
        )
        assertNull(burstOverheat, "Overheating must prevent burst")
    }
}
