package com.bastionzero.medical

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

enum class VitalsStatus {
    STANDBY,
    CALIBRATING,
    NORMAL,
    TACHYCARDIA,       // BPM > 100
    BRADYCARDIA,       // BPM < 50
    HYPOXEMIA,         // SpO2 < 90%
    SEVERE_HYPOXIA,    // SpO2 < 85%
}

data class OpticalVitalsSample(
    val timestampNs: Long,
    val redIntensity: Float,
    val infraredOrGreenIntensity: Float,
)

data class VitalsReading(
    val timestampNs: Long,
    val heartRateBpm: Int,
    val spo2Percent: Int,
    val confidence: Float,
    val status: VitalsStatus,
    val diagnosticSummary: String,
)

data class OpticalVitalsState(
    val isMonitoring: Boolean = false,
    val currentReading: VitalsReading? = null,
    val samplesProcessed: Int = 0,
    val signalQualityPercent: Int = 0,
)

/**
 * Optical Vitals & Photoplethysmography (PPG) Monitor.
 * Uses the camera lens and LED flash to measure capillary blood volume pulses.
 * Estimates heart rate and arterial blood oxygen saturation (SpO2) to diagnose
 * shock, altitude sickness, hypothermia, or crush syndrome without smartwatches.
 */
class OpticalVitalsMonitor(
    private val minPeakIntervalNs: Long = 250_000_000L, // Max 240 BPM (250ms refractory)
    private val maxPeakIntervalNs: Long = 1_500_000_000L, // Min 40 BPM (1500ms)
) {
    private val _state = MutableStateFlow(OpticalVitalsState())
    val state: StateFlow<OpticalVitalsState> = _state.asStateFlow()

    private var lastPeakNs: Long = 0L
    private val peakIntervals = ArrayList<Long>()
    private val redBuffer = ArrayList<Float>()
    private val irBuffer = ArrayList<Float>()
    private var lastSample: OpticalVitalsSample? = null
    private var isAscending = false

    fun start() {
        reset()
        _state.value = _state.value.copy(isMonitoring = true)
    }

    fun stop() {
        _state.value = _state.value.copy(isMonitoring = false)
    }

    fun reset() {
        lastPeakNs = 0L
        peakIntervals.clear()
        redBuffer.clear()
        irBuffer.clear()
        lastSample = null
        isAscending = false
        _state.value = OpticalVitalsState()
    }

    /**
     * Ingest an optical frame reading from camera PPG stream.
     */
    fun processFrame(sample: OpticalVitalsSample): VitalsReading? {
        if (!_state.value.isMonitoring) return null

        redBuffer.add(sample.redIntensity)
        irBuffer.add(sample.infraredOrGreenIntensity)
        if (redBuffer.size > 150) redBuffer.removeAt(0)
        if (irBuffer.size > 150) irBuffer.removeAt(0)

        val prev = lastSample
        var newReading: VitalsReading? = null

        if (prev != null) {
            val delta = sample.redIntensity - prev.redIntensity
            if (delta > 0.001f) {
                isAscending = true
            } else if (delta < -0.001f && isAscending) {
                // Peak inflection detected
                isAscending = false
                val intervalNs = sample.timestampNs - lastPeakNs

                if (lastPeakNs != 0L && intervalNs in minPeakIntervalNs..maxPeakIntervalNs) {
                    peakIntervals.add(intervalNs)
                    if (peakIntervals.size > 8) peakIntervals.removeAt(0)

                    if (peakIntervals.size >= 3) {
                        newReading = calculateVitals(sample.timestampNs)
                    }
                }
                lastPeakNs = sample.timestampNs
            }
        }

        lastSample = sample
        val signalQuality = min(100, (peakIntervals.size * 100) / 6)

        _state.value = _state.value.copy(
            samplesProcessed = _state.value.samplesProcessed + 1,
            signalQualityPercent = signalQuality,
            currentReading = newReading ?: _state.value.currentReading,
        )

        return newReading
    }

    private fun calculateVitals(timestampNs: Long): VitalsReading {
        val avgIntervalNs = peakIntervals.average()
        val calculatedBpm = ((60.0 * 1_000_000_000.0) / avgIntervalNs).toInt().coerceIn(35, 230)

        // Calculate AC/DC components for SpO2 ratio of ratios
        val redMax = redBuffer.maxOrNull() ?: 1.0f
        val redMin = redBuffer.minOrNull() ?: 0.5f
        val redDc = redBuffer.average().toFloat()
        val redAc = max(0.0001f, redMax - redMin)

        val irMax = irBuffer.maxOrNull() ?: 1.0f
        val irMin = irBuffer.minOrNull() ?: 0.5f
        val irDc = irBuffer.average().toFloat()
        val irAc = max(0.0001f, irMax - irMin)

        // R = (AC_red / DC_red) / (AC_ir / DC_ir)
        val rRatio = (redAc / max(0.01f, redDc)) / (irAc / max(0.01f, irDc))
        // Standard empirical calibration curve: SpO2 = 110 - 25 * R
        val estimatedSpo2 = (110.0 - 25.0 * rRatio).toInt().coerceIn(70, 100)

        val status = when {
            estimatedSpo2 < 85 -> VitalsStatus.SEVERE_HYPOXIA
            estimatedSpo2 < 90 -> VitalsStatus.HYPOXEMIA
            calculatedBpm > 100 -> VitalsStatus.TACHYCARDIA
            calculatedBpm < 50 -> VitalsStatus.BRADYCARDIA
            else -> VitalsStatus.NORMAL
        }

        val confidence = min(0.98f, 0.60f + (peakIntervals.size * 0.05f))

        val summary = when (status) {
            VitalsStatus.SEVERE_HYPOXIA -> "CRITICAL: Severe Hypoxia (SpO2 $estimatedSpo2%). High risk of loss of consciousness."
            VitalsStatus.HYPOXEMIA -> "WARNING: Low SpO2 ($estimatedSpo2%). Assess for respiratory depression or altitude shock."
            VitalsStatus.TACHYCARDIA -> "ELEVATED: Tachycardia ($calculatedBpm BPM). Elevated stress, hypovolemia, or shock response."
            VitalsStatus.BRADYCARDIA -> "ELEVATED: Bradycardia ($calculatedBpm BPM). Severe hypothermia risk."
            VitalsStatus.NORMAL -> "STABLE: Vitals nominal ($calculatedBpm BPM · SpO2 $estimatedSpo2%)."
            else -> "CALIBRATING"
        }

        return VitalsReading(
            timestampNs = timestampNs,
            heartRateBpm = calculatedBpm,
            spo2Percent = estimatedSpo2,
            confidence = confidence,
            status = status,
            diagnosticSummary = summary,
        )
    }
}
