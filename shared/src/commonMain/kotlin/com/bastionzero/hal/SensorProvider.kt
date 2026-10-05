package com.bastionzero.hal

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class MotionSample(
    val timestampNs: Long,
    val ax: Float, // Acceleration X (m/s^2)
    val ay: Float, // Acceleration Y (m/s^2)
    val az: Float, // Acceleration Z (m/s^2)
    val gx: Float, // Gyroscope X (rad/s)
    val gy: Float, // Gyroscope Y (rad/s)
    val gz: Float, // Gyroscope Z (rad/s)
    val mx: Float, // Magnetometer X (uT)
    val my: Float, // Magnetometer Y (uT)
    val mz: Float, // Magnetometer Z (uT)
)

data class EnvironmentalSample(
    val timestampNs: Long,
    val pressureHpa: Float,
    val temperatureCelsius: Float,
    val altitudeMeters: Float,
)

interface SensorStream {
    val motionSamples: SharedFlow<MotionSample>
    val environmentalSamples: SharedFlow<EnvironmentalSample>
    val batteryTemperatureCelsius: StateFlow<Float>

    fun start(intervalMs: Long = 20)
    fun stop()
}

/**
 * Native Hardware Abstraction Layer for device motion and environmental sensors.
 * - androidMain: SensorManager (Accelerometer, Gyroscope, Magnetometer, Barometer, Battery Thermistor)
 * - iosMain: CoreMotion (CMMotionManager, CMAltimeter)
 */
expect class SensorProvider : SensorStream
