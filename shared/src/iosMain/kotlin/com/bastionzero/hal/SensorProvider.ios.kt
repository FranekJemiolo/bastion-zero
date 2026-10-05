package com.bastionzero.hal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreMotion.CMAltimeter
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue

/**
 * iOS implementation of [SensorProvider] utilizing Apple's [CMMotionManager] and [CMAltimeter].
 */
@OptIn(ExperimentalForeignApi::class)
actual class SensorProvider : SensorStream {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _motionSamples = MutableSharedFlow<MotionSample>(extraBufferCapacity = 64)
    override val motionSamples: SharedFlow<MotionSample> = _motionSamples.asSharedFlow()

    private val _environmentalSamples = MutableSharedFlow<EnvironmentalSample>(extraBufferCapacity = 32)
    override val environmentalSamples: SharedFlow<EnvironmentalSample> = _environmentalSamples.asSharedFlow()

    private val _batteryTemperatureCelsius = MutableStateFlow(22.0f)
    override val batteryTemperatureCelsius: StateFlow<Float> = _batteryTemperatureCelsius.asStateFlow()

    private val motionManager = CMMotionManager()
    private val altimeter = CMAltimeter()
    private val motionQueue = NSOperationQueue()

    private var isRunning = false

    override fun start(intervalMs: Long) {
        if (isRunning) return
        isRunning = true

        val updateIntervalSec = intervalMs / 1000.0

        if (motionManager.deviceMotionAvailable) {
            motionManager.deviceMotionUpdateInterval = updateIntervalSec
            motionManager.startDeviceMotionUpdatesToQueue(motionQueue) { motion, _ ->
                if (motion != null) {
                    val userAcc = motion.userAcceleration
                    val grav = motion.gravity
                    val rotRate = motion.rotationRate
                    val magField = motion.magneticField

                    val nowNs = (motion.timestamp * 1_000_000_000.0).toLong()

                    val ax = ((userAcc.useContents { x } + grav.useContents { x }) * 9.80665).toFloat()
                    val ay = ((userAcc.useContents { y } + grav.useContents { y }) * 9.80665).toFloat()
                    val az = ((userAcc.useContents { z } + grav.useContents { z }) * 9.80665).toFloat()

                    val gx = rotRate.useContents { x }.toFloat()
                    val gy = rotRate.useContents { y }.toFloat()
                    val gz = rotRate.useContents { z }.toFloat()

                    val mx = magField.useContents { field.x }.toFloat()
                    val my = magField.useContents { field.y }.toFloat()
                    val mz = magField.useContents { field.z }.toFloat()

                    _motionSamples.tryEmit(
                        MotionSample(
                            timestampNs = nowNs,
                            ax = ax, ay = ay, az = az,
                            gx = gx, gy = gy, gz = gz,
                            mx = mx, my = my, mz = mz,
                        )
                    )
                }
            }
        }

        if (CMAltimeter.isRelativeAltitudeAvailable()) {
            altimeter.startRelativeAltitudeUpdatesToQueue(motionQueue) { altitudeData, _ ->
                if (altitudeData != null) {
                    val pressureKpa = altitudeData.pressure.doubleValue
                    val pressureHpa = (pressureKpa * 10.0).toFloat()
                    val relativeAltMeters = altitudeData.relativeAltitude.floatValue
                    val nowNs = (altitudeData.timestamp * 1_000_000_000.0).toLong()

                    _environmentalSamples.tryEmit(
                        EnvironmentalSample(
                            timestampNs = nowNs,
                            pressureHpa = pressureHpa,
                            temperatureCelsius = _batteryTemperatureCelsius.value,
                            altitudeMeters = relativeAltMeters,
                        )
                    )
                }
            }
        }
    }

    override fun stop() {
        if (!isRunning) return
        isRunning = false

        motionManager.stopDeviceMotionUpdates()
        altimeter.stopRelativeAltitudeUpdates()
    }
}
