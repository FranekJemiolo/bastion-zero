package com.bastionzero.acoustic

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Microphone position in handset coordinate space (meters relative to chassis center).
 */
data class MicPosition(
    val id: Int,
    val xMeters: Float, // Negative = left, Positive = right
    val yMeters: Float  // Negative = bottom, Positive = top
)

/**
 * Acoustic threat classification based on impulse rise time and harmonic frequency.
 */
enum class AcousticThreatType {
    GUNSHOT_SUPERSONIC,       // Fast rise time (<1ms), high peak pressure
    ROTOR_BLADE_FREQUENCY,    // Sustained periodic low frequency (15 - 50 Hz blade pass)
    IMPACT_COLLAPSE,          // Low-frequency rubble collapse rumble
    DISTRESS_SHOUT,           // Mid-frequency human vocal acoustic range (300 - 3000 Hz)
    UNKNOWN_IMPULSE           // Unclassified loud acoustic event
}

/**
 * Triangulated acoustic bearing and threat telemetry.
 */
data class AcousticBearingResult(
    val azimuthDegrees: Float,       // 0 to 360 degrees clockwise from phone top heading
    val elevationDegrees: Float,     // -90 to +90 degrees relative to screen plane
    val confidence: Float,           // 0.0 to 1.0 based on cross-correlation correlation peak
    val threatType: AcousticThreatType,
    val speedOfSoundMps: Float,
    val peakDelayMicros: Long,
    val isReliableFix: Boolean
)

/**
 * Multi-Microphone Acoustic Triangulation Engine.
 *
 * Utilizes Time Difference of Arrival (TDoA) cross-correlation across spaced
 * smartphone microphones (e.g. Top, Bottom-Left, Bottom-Right) to compute
 * the azimuth and elevation angle toward acoustic threats.
 */
class AcousticTriangulationEngine(
    val microphones: List<MicPosition> = listOf(
        MicPosition(id = 0, xMeters = 0.000f, yMeters = 0.075f),  // Top mic (e.g. camera / noise cancel)
        MicPosition(id = 1, xMeters = -0.035f, yMeters = -0.075f), // Bottom-left primary mic
        MicPosition(id = 2, xMeters = 0.035f, yMeters = -0.075f)  // Bottom-right secondary mic
    ),
    private val sampleRateHz: Int = 48000
) {

    /**
     * Computes the ambient speed of sound in dry air given temperature in Celsius.
     * c(T) = 331.3 * sqrt(1 + T / 273.15)
     */
    fun computeSoundSpeed(tempCelsius: Float): Float {
        val safeTemp = max(-50f, min(60f, tempCelsius))
        return (331.3f * sqrt(1.0f + safeTemp / 273.15f))
    }

    /**
     * Triangulates sound origin from multi-channel audio buffers.
     *
     * @param channelBuffers List of FloatArrays representing audio samples from each mic.
     * @param ambientTempCelsius Ambient temperature in Celsius for sonic velocity tuning.
     * @param dominantFrequencyHz Dominant FFT frequency of the acoustic event.
     * @param riseTimeMs Time in milliseconds from onset to peak amplitude.
     */
    fun triangulate(
        channelBuffers: List<FloatArray>,
        ambientTempCelsius: Float = 20.0f,
        dominantFrequencyHz: Float = 1000f,
        riseTimeMs: Float = 5.0f
    ): AcousticBearingResult {
        val soundSpeed = computeSoundSpeed(ambientTempCelsius)

        if (channelBuffers.size < 2 || channelBuffers.any { it.isEmpty() }) {
            return AcousticBearingResult(
                azimuthDegrees = 0f,
                elevationDegrees = 0f,
                confidence = 0f,
                threatType = AcousticThreatType.UNKNOWN_IMPULSE,
                speedOfSoundMps = soundSpeed,
                peakDelayMicros = 0L,
                isReliableFix = false
            )
        }

        // Compute cross-correlation peak sample delay between Mic 0 (top) and Mic 1 (bottom-left)
        val delay01Samples = findDelaySamples(channelBuffers[0], channelBuffers[1])
        // Compute cross-correlation peak sample delay between Mic 1 (bottom-left) and Mic 2 (bottom-right)
        val delay12Samples = if (channelBuffers.size > 2) {
            findDelaySamples(channelBuffers[1], channelBuffers[2])
        } else {
            0
        }

        val delay01Seconds = delay01Samples.toFloat() / sampleRateHz.toFloat()
        val delay12Seconds = delay12Samples.toFloat() / sampleRateHz.toFloat()

        // Geometric baseline separation
        val baselineY = abs(microphones[0].yMeters - microphones[1].yMeters)
        val baselineX = if (microphones.size > 2) abs(microphones[1].xMeters - microphones[2].xMeters) else 0.07f

        // Projected distance deltas along axes
        val deltaY = (delay01Seconds * soundSpeed).coerceIn(-baselineY, baselineY)
        val deltaX = (delay12Seconds * soundSpeed).coerceIn(-baselineX, baselineX)

        // Azimuth angle from X and Y TDoA projections
        // cos(angleY) = deltaY / baselineY
        val ratioY = (deltaY / baselineY).coerceIn(-1.0f, 1.0f)
        val ratioX = (deltaX / baselineX).coerceIn(-1.0f, 1.0f)

        var azimuthDeg = (atan2(ratioX, ratioY) * (180f / kotlin.math.PI.toFloat()))
        if (azimuthDeg < 0f) azimuthDeg += 360f

        // Elevation approximation relative to device horizontal plane
        val combinedMagnitude = sqrt(ratioX * ratioX + ratioY * ratioY).coerceIn(0f, 1f)
        val elevationDeg = (kotlin.math.acos(combinedMagnitude) * (180f / kotlin.math.PI.toFloat())).coerceIn(0f, 90f)

        // Confidence based on signal strength & correlation consistency
        val maxAmp0 = channelBuffers[0].maxOfOrNull { abs(it) } ?: 0f
        val maxAmp1 = channelBuffers[1].maxOfOrNull { abs(it) } ?: 0f
        val ampRatio = if (max(maxAmp0, maxAmp1) > 0f) min(maxAmp0, maxAmp1) / max(maxAmp0, maxAmp1) else 0f
        val confidence = (ampRatio * 0.9f).coerceIn(0f, 1f)

        val threatType = when {
            riseTimeMs < 1.5f && dominantFrequencyHz > 1500f -> AcousticThreatType.GUNSHOT_SUPERSONIC
            dominantFrequencyHz in 15f..60f -> AcousticThreatType.ROTOR_BLADE_FREQUENCY
            dominantFrequencyHz < 200f && riseTimeMs > 20f -> AcousticThreatType.IMPACT_COLLAPSE
            dominantFrequencyHz in 300f..3000f && riseTimeMs in 5f..30f -> AcousticThreatType.DISTRESS_SHOUT
            else -> AcousticThreatType.UNKNOWN_IMPULSE
        }

        val delayMicros = (abs(delay01Seconds) * 1_000_000).toLong()

        return AcousticBearingResult(
            azimuthDegrees = azimuthDeg,
            elevationDegrees = elevationDeg,
            confidence = confidence,
            threatType = threatType,
            speedOfSoundMps = soundSpeed,
            peakDelayMicros = delayMicros,
            isReliableFix = confidence >= 0.40f
        )
    }

    /**
     * Finds the sample lag tau maximizing the discrete cross-correlation R_xy[tau].
     */
    private fun findDelaySamples(bufA: FloatArray, bufB: FloatArray): Int {
        val maxLag = min(120, min(bufA.size / 2, bufB.size / 2)) // ~2.5 ms window
        var bestLag = 0
        var maxCorr = Float.NEGATIVE_INFINITY

        val len = min(bufA.size, bufB.size) - maxLag

        for (lag in -maxLag..maxLag) {
            var sum = 0f
            for (i in 0 until len) {
                val idxB = i + lag
                if (idxB in bufB.indices) {
                    sum += bufA[i] * bufB[idxB]
                }
            }
            if (sum > maxCorr) {
                maxCorr = sum
                bestLag = lag
            }
        }

        return bestLag
    }
}
