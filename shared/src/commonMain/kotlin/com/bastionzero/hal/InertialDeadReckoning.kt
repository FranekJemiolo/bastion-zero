package com.bastionzero.hal

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Representation of an individual breadcrumb dropped during dead-reckoning navigation.
 */
data class Breadcrumb(
    val lat: Double,
    val lon: Double,
    val altitudeMeters: Float,
    val timestampNs: Long,
    val stepIndex: Long,
)

/**
 * Snapshot of the current inertial dead reckoning state.
 */
data class DeadReckoningState(
    val lat: Double,
    val lon: Double,
    val altitudeMeters: Float,
    val headingDegrees: Float,
    val stepCount: Long,
    val totalDistanceMeters: Double,
    val lastUpdateTimestampNs: Long,
    val breadcrumbs: List<Breadcrumb> = emptyList(),
)

/**
 * Pedestrian Inertial Dead Reckoning (PDR) engine.
 *
 * Implements:
 * 1. Z-axis / Vertical Acceleration Peak Detection step counter with refractory period.
 * 2. 1D Kalman / Complementary heading fusion: merges low-drift high-rate gyroscope yaw
 *    integration with tilt-compensated magnetometer compass heading.
 * 3. Great-circle coordinate projection from the last confirmed GPS coordinate.
 *
 * @param initialLat Starting latitude (degrees).
 * @param initialLon Starting longitude (degrees).
 * @param initialAltitude Initial barometric altitude (meters).
 * @param userHeightMeters Used for biomechanical stride estimation (stride ~= height * 0.415).
 */
class InertialDeadReckoning(
    initialLat: Double = 52.2297,
    initialLon: Double = 21.0122,
    initialAltitude: Float = 110f,
    private val userHeightMeters: Float = 1.78f,
    private val customStrideMeters: Float? = null,
) {
    companion object {
        private const val EARTH_RADIUS_METERS = 6371000.0
        private const val GRAVITY = 9.80665f
        // Minimum time between valid pedestrian steps (~4 Hz maximum human cadence)
        const val MIN_STEP_INTERVAL_NS = 250_000_000L // 250 ms
        // Acceleration peak threshold above baseline gravity (m/s^2)
        const val STEP_ACCEL_THRESHOLD = 1.25f
    }

    val strideLengthMeters: Float = customStrideMeters ?: (userHeightMeters * 0.415f)

    private val _state = MutableStateFlow(
        DeadReckoningState(
            lat = initialLat,
            lon = initialLon,
            altitudeMeters = initialAltitude,
            headingDegrees = 0f,
            stepCount = 0L,
            totalDistanceMeters = 0.0,
            lastUpdateTimestampNs = 0L,
            breadcrumbs = listOf(
                Breadcrumb(
                    lat = initialLat,
                    lon = initialLon,
                    altitudeMeters = initialAltitude,
                    timestampNs = 0L,
                    stepIndex = 0L,
                )
            ),
        )
    )
    val state: StateFlow<DeadReckoningState> = _state.asStateFlow()

    // Heading estimation state (Kalman filter)
    private var estimatedHeadingDegrees = 0.0f
    private var kalmanErrorCovariance = 1.0f
    private val processNoiseQ = 0.02f // Gyro process noise
    private val measurementNoiseR = 0.5f // Magnetometer measurement noise
    private var lastMotionTimestampNs = 0L

    // Step detection state
    private var lastStepTimestampNs = 0L
    private var lastAccelMag = GRAVITY
    private var isRising = false
    private var peakAccelMag = 0f

    /**
     * Resets the dead reckoning anchor to a known valid GPS fix.
     */
    fun resetAnchor(lat: Double, lon: Double, altitudeMeters: Float, timestampNs: Long) {
        val current = _state.value
        _state.value = current.copy(
            lat = lat,
            lon = lon,
            altitudeMeters = altitudeMeters,
            lastUpdateTimestampNs = timestampNs,
            breadcrumbs = current.breadcrumbs + Breadcrumb(
                lat = lat,
                lon = lon,
                altitudeMeters = altitudeMeters,
                timestampNs = timestampNs,
                stepIndex = current.stepCount,
            ),
        )
    }

    /**
     * Updates heading using tilt-compensated magnetometer and gyroscope yaw rate.
     */
    fun updateMotion(sample: MotionSample) {
        val dtSec = if (lastMotionTimestampNs > 0 && sample.timestampNs > lastMotionTimestampNs) {
            (sample.timestampNs - lastMotionTimestampNs) / 1_000_000_000.0f
        } else {
            0.02f
        }
        lastMotionTimestampNs = sample.timestampNs

        // 1. Tilt angles from accelerometer (Roll & Pitch)
        val rollRad = atan2(sample.ay.toDouble(), sample.az.toDouble()).toFloat()
        val pitchRad = atan2(
            -sample.ax.toDouble(),
            sqrt((sample.ay * sample.ay + sample.az * sample.az).toDouble()),
        ).toFloat()

        // 2. Tilt-compensated magnetic field components
        val sinRoll = sin(rollRad.toDouble()).toFloat()
        val cosRoll = cos(rollRad.toDouble()).toFloat()
        val sinPitch = sin(pitchRad.toDouble()).toFloat()
        val cosPitch = cos(pitchRad.toDouble()).toFloat()

        val bxh = sample.mx * cosPitch + sample.my * sinRoll * sinPitch + sample.mz * cosRoll * sinPitch
        val byh = sample.my * cosRoll - sample.mz * sinRoll

        var magHeadingDeg = (atan2(-byh.toDouble(), bxh.toDouble()) * (180.0 / PI)).toFloat()
        if (magHeadingDeg < 0) magHeadingDeg += 360f

        // 3. Kalman filter prediction from gyroscope yaw rate (gz in rad/s)
        val gyroDeltaDeg = (sample.gz.toDouble() * dtSec.toDouble() * (180.0 / PI)).toFloat()
        var predictedHeading = (estimatedHeadingDegrees + gyroDeltaDeg) % 360f
        if (predictedHeading < 0) predictedHeading += 360f
        val predictedError = kalmanErrorCovariance + processNoiseQ

        // 4. Kalman update with magnetometer observation
        // Compute minimal angular difference (handling 0/360 wrap-around)
        var headingDiff = magHeadingDeg - predictedHeading
        while (headingDiff > 180f) headingDiff -= 360f
        while (headingDiff < -180f) headingDiff += 360f

        val kalmanGain = predictedError / (predictedError + measurementNoiseR)
        estimatedHeadingDegrees = (predictedHeading + kalmanGain * headingDiff) % 360f
        if (estimatedHeadingDegrees < 0) estimatedHeadingDegrees += 360f
        kalmanErrorCovariance = (1f - kalmanGain) * predictedError

        // 5. Step Detection using vertical acceleration peak detection
        val currentAccelMag = sqrt(
            (sample.ax * sample.ax + sample.ay * sample.ay + sample.az * sample.az).toDouble()
        ).toFloat()
        detectStep(currentAccelMag, sample.timestampNs)
    }

    /**
     * Updates barometric elevation from environmental sensor sample.
     */
    fun updateEnvironmental(sample: EnvironmentalSample) {
        val current = _state.value
        _state.value = current.copy(
            altitudeMeters = sample.altitudeMeters,
            lastUpdateTimestampNs = sample.timestampNs,
        )
    }

    /**
     * Z-axis peak detection algorithm with dynamic refractory gating.
     */
    private fun detectStep(accelMag: Float, timestampNs: Long) {
        val dynamicAccel = accelMag - GRAVITY

        if (dynamicAccel > lastAccelMag) {
            isRising = true
            peakAccelMag = dynamicAccel
        } else if (isRising && dynamicAccel < lastAccelMag) {
            // Peak detected at previous sample
            isRising = false
            if (peakAccelMag >= STEP_ACCEL_THRESHOLD) {
                val timeSinceLastStep = timestampNs - lastStepTimestampNs
                if (timeSinceLastStep >= MIN_STEP_INTERVAL_NS) {
                    lastStepTimestampNs = timestampNs
                    registerStep(timestampNs)
                }
            }
        }
        lastAccelMag = dynamicAccel
    }

    /**
     * Projects new latitude/longitude dead-reckoning position on confirmed footstep strike.
     */
    private fun registerStep(timestampNs: Long) {
        val current = _state.value
        val headingRad = estimatedHeadingDegrees.toDouble() * (PI / 180.0)

        // Great circle forward projection
        val deltaNorth = strideLengthMeters * cos(headingRad)
        val deltaEast = strideLengthMeters * sin(headingRad)

        val deltaLat = (deltaNorth / EARTH_RADIUS_METERS) * (180.0 / PI)
        val currentLatRad = current.lat * (PI / 180.0)
        val deltaLon = (deltaEast / (EARTH_RADIUS_METERS * cos(currentLatRad))) * (180.0 / PI)

        val newLat = current.lat + deltaLat
        val newLon = current.lon + deltaLon
        val newStepCount = current.stepCount + 1
        val newDistance = current.totalDistanceMeters + strideLengthMeters

        val newBreadcrumb = Breadcrumb(
            lat = newLat,
            lon = newLon,
            altitudeMeters = current.altitudeMeters,
            timestampNs = timestampNs,
            stepIndex = newStepCount,
        )

        // Keep last 1000 breadcrumbs in memory
        val updatedBreadcrumbs = (current.breadcrumbs + newBreadcrumb).takeLast(1000)

        _state.value = current.copy(
            lat = newLat,
            lon = newLon,
            headingDegrees = estimatedHeadingDegrees,
            stepCount = newStepCount,
            totalDistanceMeters = newDistance,
            lastUpdateTimestampNs = timestampNs,
            breadcrumbs = updatedBreadcrumbs,
        )
    }
}
