package com.bastionzero.hub

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SolarInsolationOptimizerTest {

    @Test
    fun testSolarPositionAndDaylightCalculation() {
        val optimizer = SolarInsolationOptimizer()

        // Warsaw / Central Europe latitude ~52.2° N, Day 172 (Summer Solstice), 12:00 solar noon
        val noonCoords = optimizer.calculateSolarPosition(
            latDegrees = 52.2f,
            dayOfYear = 172,
            hourOfDay = 12.0f,
        )

        assertTrue(noonCoords.isDaylight)
        // At solar noon on summer solstice at 52°N, solar elevation is roughly 90 - 52 + 23.45 ≈ 61°
        assertTrue(noonCoords.elevationDegrees in 55.0f..65.0f)
        assertEquals(180.0f, noonCoords.azimuthDegrees) // Due South

        // Midnight calculation
        val midnightCoords = optimizer.calculateSolarPosition(
            latDegrees = 52.2f,
            dayOfYear = 172,
            hourOfDay = 0.0f,
        )
        assertEquals(false, midnightCoords.isDaylight)
    }

    @Test
    fun testPowerTriageDistribution() {
        val optimizer = SolarInsolationOptimizer()

        val triage = optimizer.triagePower(
            incomingSolarWatts = 21.0f,
            primaryTarget = "Medical Radio",
            primaryPercent = 80,
            secondaryTarget = "Phone Battery",
        )

        assertEquals(21.0f, triage.incomingSolarWatts)
        assertEquals(80, triage.primaryPercent)
        assertEquals(20, triage.secondaryPercent)
        // 21 * 0.8 = 16.8W
        assertTrue(triage.primaryAllocatedWatts in 16.7f..16.9f)
        // 21 * 0.2 = 4.2W
        assertTrue(triage.secondaryAllocatedWatts in 4.1f..4.3f)
    }
}
