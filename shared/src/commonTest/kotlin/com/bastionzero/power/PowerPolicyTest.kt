package com.bastionzero.power

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PowerPolicyTest {
    private fun inputs(pct: Int, charging: Boolean = false, moving: Boolean = true) =
        PowerInputs(pct, charging, moving)

    @Test
    fun tierBoundaries() {
        assertEquals(PowerTier.NORMAL, PowerPolicyCalculator.tierFor(inputs(50)))
        assertEquals(PowerTier.SAVER, PowerPolicyCalculator.tierFor(inputs(49)))
        assertEquals(PowerTier.SAVER, PowerPolicyCalculator.tierFor(inputs(20)))
        assertEquals(PowerTier.CRITICAL, PowerPolicyCalculator.tierFor(inputs(19)))
        assertEquals(PowerTier.CRITICAL, PowerPolicyCalculator.tierFor(inputs(10)))
        assertEquals(PowerTier.SURVIVAL, PowerPolicyCalculator.tierFor(inputs(9)))
    }

    @Test
    fun chargingAlwaysNormal() {
        assertEquals(PowerTier.NORMAL, PowerPolicyCalculator.tierFor(inputs(3, charging = true)))
    }

    /** The design-doc scenario: at 10% UI drops to 15 Hz and GPS to once per 5 minutes. */
    @Test
    fun tenPercentMatchesDesignExample() {
        val p = PowerPolicyCalculator.compute(inputs(10))
        assertEquals(15, p.uiRefreshHz)
        assertEquals(300_000L, p.gpsIntervalMs)
        assertFalse(p.holdWakeLock)
    }

    @Test
    fun stillnessStretchesIntervals() {
        val moving = PowerPolicyCalculator.compute(inputs(80, moving = true))
        val still = PowerPolicyCalculator.compute(inputs(80, moving = false))
        assertEquals(moving.gpsIntervalMs * 6, still.gpsIntervalMs)
        assertEquals(moving.sensorIntervalMs * 3, still.sensorIntervalMs)
        assertEquals(moving.uiRefreshHz, still.uiRefreshHz)
    }

    @Test
    fun policyNeverGetsMoreExpensiveAsBatteryDrops() {
        val tiers = listOf(80, 40, 15, 5).map { PowerPolicyCalculator.compute(inputs(it)) }
        for ((a, b) in tiers.zipWithNext()) {
            assertTrue(b.uiRefreshHz <= a.uiRefreshHz)
            assertTrue(b.gpsIntervalMs >= a.gpsIntervalMs)
            assertTrue(b.meshScanDutyPercent <= a.meshScanDutyPercent)
            assertTrue(b.screenBrightness <= a.screenBrightness)
        }
    }
}
