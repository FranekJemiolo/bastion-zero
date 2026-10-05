package com.bastionzero.hal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StructuralTiltMonitorTest {

    @Test
    fun testButterworthFilterSmoothing() {
        val filter = Vector3ButterworthFilter(cutoffHz = 0.5, sampleRateHz = 50.0)

        // Inject initial sample
        val (x0, y0, z0) = filter.filter(0f, 0f, 9.8f)
        assertEquals(0f, x0, 0.001f)
        assertEquals(9.8f, z0, 0.001f)

        // Inject high-frequency noise spike (1 sample of 15 m/s^2)
        val (_, _, spikeZ) = filter.filter(0f, 0f, 15.0f)
        // Butterworth filter should heavily attenuate high-frequency instantaneous spike
        assertTrue(spikeZ < 10.5f, "Spike should be aggressively smoothed by low-pass filter, was $spikeZ")
    }

    @Test
    fun testBaselineCalibrationAndShiftAlarm() {
        val monitor = StructuralTiltMonitor(
            alarmThresholdDeg = 0.5f,
            warningThresholdDeg = 0.2f,
            calibrationSampleCountTarget = 10,
        )

        monitor.startCalibration()
        // Feed 10 baseline samples resting flat on foundation (Z = 9.80665) at 20°C
        for (i in 0 until 10) {
            monitor.processSample(0f, 0f, 9.80665f, 20.0f)
        }

        val statusCalibrated = monitor.status.value
        assertTrue(statusCalibrated.isCalibrated, "Monitor should be calibrated")
        assertEquals(TiltSeverity.NORMAL, statusCalibrated.severity)
        assertFalse(statusCalibrated.isAlarmTriggered)
        assertEquals(0f, statusCalibrated.temperatureCompensatedDeltaDeg, 0.05f)

        // Feed steady reading with 0.1° deviation (Normal)
        // 0.1 deg tilt on X: ax = 9.80665 * sin(0.1 deg) = 0.017 m/s^2
        for (i in 0 until 20) {
            monitor.processSample(0.017f, 0f, 9.80665f, 20.0f)
        }
        assertEquals(TiltSeverity.NORMAL, monitor.status.value.severity)

        // Feed steady reading with 0.7° foundation shift (Critical Alarm!)
        // 0.7 deg tilt on X: ax = 9.80665 * sin(0.7 deg) = ~0.12 m/s^2
        for (i in 0 until 30) {
            monitor.processSample(0.13f, 0f, 9.806f, 20.0f)
        }

        val statusAlarm = monitor.status.value
        assertEquals(TiltSeverity.CRITICAL, statusAlarm.severity)
        assertTrue(statusAlarm.isAlarmTriggered, "Alarm should be triggered when tilt > 0.5 deg")
        assertTrue(statusAlarm.temperatureCompensatedDeltaDeg >= 0.5f)
    }

    @Test
    fun testTemperatureCompensation() {
        val monitor = StructuralTiltMonitor(
            alarmThresholdDeg = 0.5f,
            tempCoeffDegPerCelsius = 0.02f,
            calibrationSampleCountTarget = 5,
        )

        monitor.startCalibration()
        for (i in 0 until 5) {
            monitor.processSample(0f, 0f, 9.80665f, 10.0f) // Calibrated at 10°C
        }

        // Suppose temperature rises to 30°C (delta = +20°C)
        // Expected thermal bias drift = 20 * 0.02 = 0.4°
        // If raw tilt is 0.4°, temperature compensation should cancel it out to ~0°!
        for (i in 0 until 20) {
            // ax corresponding to ~0.4 deg
            monitor.processSample(0.07f, 0f, 9.806f, 30.0f)
        }

        val status = monitor.status.value
        assertTrue(status.temperatureCompensatedDeltaDeg < 0.2f, "Temperature compensation should reduce thermal drift")
        assertFalse(status.isAlarmTriggered)
    }
}
