package com.bastionzero.power

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

enum class PowerBudgetMode {
    HARVEST_SURPLUS,
    BALANCED,
    SURVIVAL_CRITICAL,
}

data class HardwareThrottleProfile(
    val cameraFps: Int,
    val imuSamplingHz: Int,
    val bleScanDutyCyclePercent: Int,
    val oledBrightnessPercent: Int,
    val backgroundProcessingPaused: Boolean,
)

data class AutonomousPowerState(
    val batteryPercent: Int = 100,
    val batteryTemperatureCelsius: Float = 25.0f,
    val solarHarvestWatts: Float = 0.0f,
    val mode: PowerBudgetMode = PowerBudgetMode.BALANCED,
    val throttleProfile: HardwareThrottleProfile = HardwareThrottleProfile(
        cameraFps = 30,
        imuSamplingHz = 50,
        bleScanDutyCyclePercent = 25,
        oledBrightnessPercent = 80,
        backgroundProcessingPaused = false,
    ),
    val estimatedRuntimeHours: Float = 48.0f,
    val isThermalThrottling: Boolean = false,
)

/**
 * Autonomous Power & Thermal Governor.
 * Dynamically throttles hardware sub-systems (Camera, IMU, BLE radio, Display)
 * by coupling device state with real-time solar harvest telemetry.
 */
class AutonomousPowerGovernor(
    private val batteryCapacityMilliAmpHours: Int = 4000,
) {
    private val _state = MutableStateFlow(AutonomousPowerState())
    val state: StateFlow<AutonomousPowerState> = _state.asStateFlow()

    /**
     * Update environmental and telemetry readings to recalculate throttle profile.
     */
    fun updateTelemetry(
        batteryPercent: Int,
        batteryTempCelsius: Float,
        solarHarvestWatts: Float,
    ): AutonomousPowerState {
        val isThermalAlarm = batteryTempCelsius >= 45.0f

        val mode = when {
            batteryPercent < 20 || isThermalAlarm -> PowerBudgetMode.SURVIVAL_CRITICAL
            solarHarvestWatts >= 3.0f && batteryPercent >= 40 -> PowerBudgetMode.HARVEST_SURPLUS
            batteryPercent >= 85 -> PowerBudgetMode.HARVEST_SURPLUS
            else -> PowerBudgetMode.BALANCED
        }

        val profile = when (mode) {
            PowerBudgetMode.HARVEST_SURPLUS -> HardwareThrottleProfile(
                cameraFps = 30,
                imuSamplingHz = 100,
                bleScanDutyCyclePercent = 50,
                oledBrightnessPercent = 100,
                backgroundProcessingPaused = false,
            )
            PowerBudgetMode.BALANCED -> HardwareThrottleProfile(
                cameraFps = 30,
                imuSamplingHz = 50,
                bleScanDutyCyclePercent = 25,
                oledBrightnessPercent = 70,
                backgroundProcessingPaused = false,
            )
            PowerBudgetMode.SURVIVAL_CRITICAL -> HardwareThrottleProfile(
                cameraFps = 10,
                imuSamplingHz = 20,
                bleScanDutyCyclePercent = 2,
                oledBrightnessPercent = 10,
                backgroundProcessingPaused = true,
            )
        }

        // Base device baseline consumption ~0.8W in Balanced, 1.8W in Surplus, 0.25W in Critical
        val baselineDrawWatts = when (mode) {
            PowerBudgetMode.HARVEST_SURPLUS -> 1.8f
            PowerBudgetMode.BALANCED -> 0.8f
            PowerBudgetMode.SURVIVAL_CRITICAL -> 0.25f
        }

        val netPowerWatts = max(0.05f, baselineDrawWatts - (solarHarvestWatts * 0.70f))
        val nominalBatteryWattHours = (batteryCapacityMilliAmpHours * 3.85f) / 1000.0f
        val remainingWattHours = (nominalBatteryWattHours * batteryPercent) / 100.0f
        val estimatedHours = (remainingWattHours / netPowerWatts).coerceIn(1.0f, 999.0f)

        val newState = AutonomousPowerState(
            batteryPercent = batteryPercent.coerceIn(0, 100),
            batteryTemperatureCelsius = batteryTempCelsius,
            solarHarvestWatts = max(0f, solarHarvestWatts),
            mode = mode,
            throttleProfile = profile,
            estimatedRuntimeHours = estimatedHours,
            isThermalThrottling = isThermalAlarm,
        )

        _state.value = newState
        return newState
    }
}
