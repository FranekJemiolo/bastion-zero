package com.bastionzero.power

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutonomousPowerGovernorTest {

    @Test
    fun testNominalBalancedModeTransitions() {
        val governor = AutonomousPowerGovernor()

        val state = governor.updateTelemetry(
            batteryPercent = 45,
            batteryTempCelsius = 28.0f,
            solarHarvestWatts = 0.5f,
        )

        assertEquals(PowerBudgetMode.BALANCED, state.mode)
        assertEquals(30, state.throttleProfile.cameraFps)
        assertEquals(50, state.throttleProfile.imuSamplingHz)
        assertEquals(25, state.throttleProfile.bleScanDutyCyclePercent)
        assertEquals(false, state.throttleProfile.backgroundProcessingPaused)
        assertEquals(false, state.isThermalThrottling)
    }

    @Test
    fun testCriticalBatteryEntersSurvivalThrottle() {
        val governor = AutonomousPowerGovernor()

        val state = governor.updateTelemetry(
            batteryPercent = 14,
            batteryTempCelsius = 30.0f,
            solarHarvestWatts = 0.0f,
        )

        assertEquals(PowerBudgetMode.SURVIVAL_CRITICAL, state.mode)
        assertEquals(10, state.throttleProfile.cameraFps)
        assertEquals(20, state.throttleProfile.imuSamplingHz)
        assertEquals(2, state.throttleProfile.bleScanDutyCyclePercent)
        assertEquals(10, state.throttleProfile.oledBrightnessPercent)
        assertEquals(true, state.throttleProfile.backgroundProcessingPaused)
    }

    @Test
    fun testSolarHarvestSurplusExtendsRuntime() {
        val governor = AutonomousPowerGovernor()

        val withoutSolar = governor.updateTelemetry(
            batteryPercent = 60,
            batteryTempCelsius = 25.0f,
            solarHarvestWatts = 0.0f,
        )

        val withSolar = governor.updateTelemetry(
            batteryPercent = 60,
            batteryTempCelsius = 25.0f,
            solarHarvestWatts = 4.5f,
        )

        assertEquals(PowerBudgetMode.HARVEST_SURPLUS, withSolar.mode)
        assertTrue(withSolar.estimatedRuntimeHours > withoutSolar.estimatedRuntimeHours)
    }

    @Test
    fun testThermalOverheatForcesSurvivalThrottle() {
        val governor = AutonomousPowerGovernor()

        // Battery is high (90%), but phone is overheating at 48°C
        val state = governor.updateTelemetry(
            batteryPercent = 90,
            batteryTempCelsius = 48.0f,
            solarHarvestWatts = 0.0f,
        )

        assertEquals(PowerBudgetMode.SURVIVAL_CRITICAL, state.mode)
        assertEquals(true, state.isThermalThrottling)
        assertEquals(true, state.throttleProfile.backgroundProcessingPaused)
    }
}
