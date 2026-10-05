package com.bastionzero.hal

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TiltSeverity {
    NORMAL,
    WARNING,
    CRITICAL,
}

data class TiltStatus(
    val isCalibrated: Boolean,
    val currentTiltDeg: Float,
    val baselineTiltDeg: Float,
    val temperatureCompensatedDeltaDeg: Float,
    val currentTempCelsius: Float,
    val baselineTempCelsius: Float,
    val severity: TiltSeverity,
    val isAlarmTriggered: Boolean,
    val sampleCount: Long,
)

/**
 * 2nd-order digital Butterworth Low-Pass Filter (IIR) for 3D vector smoothing.
 */
class Vector3ButterworthFilter(
    cutoffHz: Double = 0.5,
    sampleRateHz: Double = 50.0,
) {
    private var b0: Double = 0.0
    private var b1: Double = 0.0
    private var b2: Double = 0.0
    private var a1: Double = 0.0
    private var a2: Double = 0.0

    // Filter memory per axis (X, Y, Z)
    private var inX1 = 0.0; private var inX2 = 0.0; private var outX1 = 0.0; private var outX2 = 0.0
    private var inY1 = 0.0; private var inY2 = 0.0; private var outY1 = 0.0; private var outY2 = 0.0
    private var inZ1 = 0.0; private var inZ2 = 0.0; private var outZ1 = 0.0; private var outZ2 = 0.0

    private var initialized = false

    init {
        configure(cutoffHz, sampleRateHz)
    }

    fun configure(cutoffHz: Double, sampleRateHz: Double) {
        val wc = 2.0 * PI * cutoffHz / sampleRateHz
        val k = tan(wc / 2.0)
        val k2 = k * k
        val sqrt2k = sqrt(2.0) * k
        val norm = 1.0 / (1.0 + sqrt2k + k2)

        b0 = k2 * norm
        b1 = 2.0 * b0
        b2 = b0
        a1 = 2.0 * (k2 - 1.0) * norm
        a2 = (1.0 - sqrt2k + k2) * norm
    }

    fun filter(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
        val dx = x.toDouble()
        val dy = y.toDouble()
        val dz = z.toDouble()

        if (!initialized) {
            inX1 = dx; inX2 = dx; outX1 = dx; outX2 = dx
            inY1 = dy; inY2 = dy; outY1 = dy; outY2 = dy
            inZ1 = dz; inZ2 = dz; outZ1 = dz; outZ2 = dz
            initialized = true
            return Triple(x, y, z)
        }

        val outX = b0 * dx + b1 * inX1 + b2 * inX2 - a1 * outX1 - a2 * outX2
        inX2 = inX1; inX1 = dx; outX2 = outX1; outX1 = outX

        val outY = b0 * dy + b1 * inY1 + b2 * inY2 - a1 * outY1 - a2 * outY2
        inY2 = inY1; inY1 = dy; outY2 = outY1; outY1 = outY

        val outZ = b0 * dz + b1 * inZ1 + b2 * inZ2 - a1 * outZ1 - a2 * outZ2
        inZ2 = inZ1; inZ1 = dz; outZ2 = outZ1; outZ1 = outZ

        return Triple(outX.toFloat(), outY.toFloat(), outZ.toFloat())
    }

    fun reset() {
        initialized = false
    }
}

/**
 * Structural Tilt Monitor for post-disaster foundation shift detection.
 *
 * Placed on a flat surface or load-bearing wall:
 * 1. Zeroes against gravity baseline vector during initial calibration (averaging N samples).
 * 2. Applies 2nd-order Butterworth low-pass filter to reject human footsteps/vibration.
 * 3. Compensates for battery-thermistor thermal expansion coefficient in MEMS silicon.
 * 4. Triggers alarm when deviation exceeds [alarmThresholdDeg] (default 0.5°).
 */
class StructuralTiltMonitor(
    private val alarmThresholdDeg: Float = 0.5f,
    private val warningThresholdDeg: Float = 0.2f,
    // MEMS silicon thermal drift compensation coefficient (degrees tilt per °C)
    private val tempCoeffDegPerCelsius: Float = 0.015f,
    val calibrationSampleCountTarget: Int = 40,
) {
    private val filter = Vector3ButterworthFilter(cutoffHz = 0.2, sampleRateHz = 50.0)

    private val _status = MutableStateFlow(
        TiltStatus(
            isCalibrated = false,
            currentTiltDeg = 0f,
            baselineTiltDeg = 0f,
            temperatureCompensatedDeltaDeg = 0f,
            currentTempCelsius = 20f,
            baselineTempCelsius = 20f,
            severity = TiltSeverity.NORMAL,
            isAlarmTriggered = false,
            sampleCount = 0L,
        )
    )
    val status: StateFlow<TiltStatus> = _status.asStateFlow()

    // Calibration accumulator
    private var isCalibrating = false
    private var calibAccumX = 0.0
    private var calibAccumY = 0.0
    private var calibAccumZ = 0.0
    private var calibAccumTemp = 0.0
    private var calibCount = 0

    // Stored baseline
    private var baselineGx = 0f
    private var baselineGy = 0f
    private var baselineGz = 9.80665f
    private var baselineTemp = 20f
    private var isCalibrated = false
    private var totalSampleCount = 0L

    /**
     * Begins or resets baseline calibration.
     */
    fun startCalibration() {
        isCalibrating = true
        calibAccumX = 0.0
        calibAccumY = 0.0
        calibAccumZ = 0.0
        calibAccumTemp = 0.0
        calibCount = 0
        filter.reset()
    }

    /**
     * Feeds raw accelerometer and temperature readings.
     */
    fun processSample(ax: Float, ay: Float, az: Float, temperatureCelsius: Float) {
        totalSampleCount++

        if (isCalibrating) {
            calibAccumX += ax
            calibAccumY += ay
            calibAccumZ += az
            calibAccumTemp += temperatureCelsius
            calibCount++

            if (calibCount >= calibrationSampleCountTarget) {
                baselineGx = (calibAccumX / calibCount).toFloat()
                baselineGy = (calibAccumY / calibCount).toFloat()
                baselineGz = (calibAccumZ / calibCount).toFloat()
                baselineTemp = (calibAccumTemp / calibCount).toFloat()
                isCalibrating = false
                isCalibrated = true
            }
        }

        // Apply low-pass Butterworth filter to remove high-frequency vibrations
        val (fx, fy, fz) = filter.filter(ax, ay, az)

        val rawDeltaDeg = if (isCalibrated) {
            calculateAngleBetween(baselineGx, baselineGy, baselineGz, fx, fy, fz)
        } else {
            0f
        }

        // Temperature compensation: subtract expected thermal bias drift
        val deltaTemp = temperatureCelsius - baselineTemp
        val thermalDriftDeg = tempCoeffDegPerCelsius * deltaTemp
        val compensatedDeltaDeg = (rawDeltaDeg - thermalDriftDeg).coerceAtLeast(0f)

        val severity = when {
            compensatedDeltaDeg >= alarmThresholdDeg -> TiltSeverity.CRITICAL
            compensatedDeltaDeg >= warningThresholdDeg -> TiltSeverity.WARNING
            else -> TiltSeverity.NORMAL
        }

        val isAlarm = isCalibrated && compensatedDeltaDeg >= alarmThresholdDeg

        _status.value = TiltStatus(
            isCalibrated = isCalibrated,
            currentTiltDeg = rawDeltaDeg,
            baselineTiltDeg = 0f,
            temperatureCompensatedDeltaDeg = compensatedDeltaDeg,
            currentTempCelsius = temperatureCelsius,
            baselineTempCelsius = baselineTemp,
            severity = severity,
            isAlarmTriggered = isAlarm,
            sampleCount = totalSampleCount,
        )
    }

    private fun calculateAngleBetween(
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
    ): Float {
        val dot = (x1 * x2 + y1 * y2 + z1 * z2).toDouble()
        val mag1 = sqrt((x1 * x1 + y1 * y1 + z1 * z1).toDouble())
        val mag2 = sqrt((x2 * x2 + y2 * y2 + z2 * z2).toDouble())

        if (mag1 < 1e-6 || mag2 < 1e-6) return 0f

        val cosTheta = (dot / (mag1 * mag2)).coerceIn(-1.0, 1.0)
        return (acos(cosTheta) * (180.0 / PI)).toFloat()
    }
}
