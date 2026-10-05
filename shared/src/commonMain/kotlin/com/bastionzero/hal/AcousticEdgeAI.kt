package com.bastionzero.hal

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThreatSignature {
    AMBIENT_NOISE,
    DRONE_ROTOR,
    BALLISTIC_GUNSHOT,
    SAR_SIREN_SOS,
    VEHICLE_HEAVY,
}

data class AcousticThreatEvent(
    val signature: ThreatSignature,
    val confidence: Float,
    val decibelSpl: Float,
    val dominantFrequencyHz: Float,
    val timestampNs: Long,
)

data class AcousticSpectrogram(
    val frameCount: Int,
    val binCount: Int,
    val sampleRateHz: Int,
    // [frameCount][binCount] matrix of log-magnitude spectral energy
    val powerMatrix: Array<FloatArray>,
)

/**
 * Pure Kotlin Cooley-Tukey Radix-2 Fast Fourier Transform.
 */
object FastFourierTransform {
    /**
     * In-place radix-2 Cooley-Tukey FFT. [real] and [imag] must have size that is a power of 2.
     */
    fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        require(n and (n - 1) == 0) { "FFT size must be a power of 2" }

        // Bit reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]; real[i] = real[j]; real[j] = tempR
                val tempI = imag[i]; imag[i] = imag[j]; imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        // Cooley-Tukey butterfly computations
        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val angle = -2.0 * PI / len
            val wStepR = cos(angle).toFloat()
            val wStepI = sin(angle).toFloat()

            var i = 0
            while (i < n) {
                var wR = 1.0f
                var wI = 0.0f
                for (k in 0 until halfLen) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[i + k + halfLen] * wR - imag[i + k + halfLen] * wI
                    val vI = real[i + k + halfLen] * wI + imag[i + k + halfLen] * wR

                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + halfLen] = uR - vR
                    imag[i + k + halfLen] = uI - vI

                    val nextWR = wR * wStepR - wI * wStepI
                    val nextWI = wR * wStepI + wI * wStepR
                    wR = nextWR
                    wI = nextWI
                }
                i += len
            }
            len = len shl 1
        }
    }
}

/**
 * Acoustic Edge-AI Overwatch Processor.
 *
 * Implements:
 * 1. Hardware Decibel Threshold Gating (> 70 dB SPL trigger).
 * 2. 2-second circular audio buffer (16 kHz mono = 32,000 samples).
 * 3. Short-Time Fourier Transform (STFT) Spectrogram computation with Hann windowing.
 * 4. Acoustic threat classifier for drone rotors, gunshots, and emergency sirens.
 */
class AcousticEdgeAI(
    val sampleRateHz: Int = 16000,
    val decibelThresholdDb: Float = 70.0f,
    val fftSize: Int = 512,
    val hopSize: Int = 256,
) {
    companion object {
        const val BUFFER_DURATION_SECONDS = 2.0
    }

    private val totalBufferSamples = (sampleRateHz * BUFFER_DURATION_SECONDS).toInt()
    private val audioBuffer = FloatArray(totalBufferSamples)
    private var writeIndex = 0
    private var sampleCountTotal = 0L

    private val _threatEvents = MutableSharedFlow<AcousticThreatEvent>(extraBufferCapacity = 16)
    val threatEvents: SharedFlow<AcousticThreatEvent> = _threatEvents.asSharedFlow()

    private val _currentSpl = MutableStateFlow(30.0f)
    val currentSpl: StateFlow<Float> = _currentSpl.asStateFlow()

    private val hannWindow = FloatArray(fftSize) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))).toFloat()
    }

    /**
     * Ingests incoming PCM audio chunk (normalized floats in [-1.0, 1.0]).
     */
    fun processAudioChunk(samples: FloatArray, timestampNs: Long = 0L) {
        if (samples.isEmpty()) return

        // 1. Calculate RMS and instantaneous dB SPL
        var sumSquares = 0.0
        for (s in samples) {
            sumSquares += (s * s).toDouble()
        }
        val rms = sqrt(sumSquares / samples.size).toFloat()
        // Calibrated SPL approximation (RMS 1.0 = ~100 dB SPL, 0.0001 = ~20 dB)
        val splDb = if (rms > 1e-5f) (20.0f * log10(rms) + 100.0f).coerceIn(20f, 130f) else 20.0f
        _currentSpl.value = splDb

        // Write into circular buffer
        for (s in samples) {
            audioBuffer[writeIndex] = s
            writeIndex = (writeIndex + 1) % totalBufferSamples
            sampleCountTotal++
        }

        // 2. Hardware Decibel Threshold Gating
        // If sound amplitude is below threshold, skip CPU-intensive FFT and inference to conserve battery!
        if (splDb >= decibelThresholdDb && sampleCountTotal >= totalBufferSamples) {
            val spectrogram = computeSpectrogram()
            val event = classifySpectrogram(spectrogram, splDb, timestampNs)
            if (event.signature != ThreatSignature.AMBIENT_NOISE) {
                _threatEvents.tryEmit(event)
            }
        }
    }

    /**
     * Extracts linear audio from circular buffer and generates STFT Spectrogram.
     */
    fun computeSpectrogram(): AcousticSpectrogram {
        val linearAudio = FloatArray(totalBufferSamples)
        for (i in 0 until totalBufferSamples) {
            linearAudio[i] = audioBuffer[(writeIndex + i) % totalBufferSamples]
        }

        val frameCount = (totalBufferSamples - fftSize) / hopSize + 1
        val binCount = fftSize / 2 + 1
        val matrix = Array(frameCount) { FloatArray(binCount) }

        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)

        for (f in 0 until frameCount) {
            val offset = f * hopSize
            for (i in 0 until fftSize) {
                real[i] = linearAudio[offset + i] * hannWindow[i]
                imag[i] = 0f
            }

            FastFourierTransform.fft(real, imag)

            for (k in 0 until binCount) {
                val magnitude = sqrt((real[k] * real[k] + imag[k] * imag[k]).toDouble()).toFloat()
                // Log-magnitude
                matrix[f][k] = if (magnitude > 1e-4f) log10(magnitude + 1e-4f) + 4.0f else 0.0f
            }
        }

        return AcousticSpectrogram(frameCount, binCount, sampleRateHz, matrix)
    }

    /**
     * Edge heuristic classifier analyzing spectral energy distributions and harmonics.
     */
    fun classifySpectrogram(
        spec: AcousticSpectrogram,
        splDb: Float,
        timestampNs: Long,
    ): AcousticThreatEvent {
        val binFreqRes = spec.sampleRateHz.toFloat() / fftSize // ~31.25 Hz per bin

        // Calculate average energy spectrum across all frames
        val avgSpectrum = FloatArray(spec.binCount)
        var maxPeakEnergy = 0f
        var maxPeakBin = 0

        for (k in 0 until spec.binCount) {
            var sum = 0f
            for (f in 0 until spec.frameCount) {
                sum += spec.powerMatrix[f][k]
            }
            avgSpectrum[k] = sum / spec.frameCount
            if (avgSpectrum[k] > maxPeakEnergy) {
                maxPeakEnergy = avgSpectrum[k]
                maxPeakBin = k
            }
        }

        val dominantFreqHz = maxPeakBin * binFreqRes

        // Check for impulse transient (ballistic / gunshot)
        var maxFrameEnergy = 0f
        var minFrameEnergy = Float.MAX_VALUE
        for (f in 0 until spec.frameCount) {
            var frameEnergy = 0f
            for (k in 0 until spec.binCount) {
                frameEnergy += spec.powerMatrix[f][k]
            }
            if (frameEnergy > maxFrameEnergy) maxFrameEnergy = frameEnergy
            if (frameEnergy < minFrameEnergy) minFrameEnergy = frameEnergy
        }
        val crestFactor = if (minFrameEnergy > 0f) maxFrameEnergy / minFrameEnergy else 1f

        // Classification heuristics:
        return when {
            // 1. Ballistic / Gunshot: Extremely high crest factor (> 8x energy spike) with broadband energy
            crestFactor > 8.0f && splDb >= 85.0f -> {
                AcousticThreatEvent(
                    signature = ThreatSignature.BALLISTIC_GUNSHOT,
                    confidence = 0.92f,
                    decibelSpl = splDb,
                    dominantFrequencyHz = dominantFreqHz,
                    timestampNs = timestampNs,
                )
            }
            // 2. Drone Rotor: Dominant harmonic peak in typical blade-pass range (400 Hz - 2400 Hz)
            dominantFreqHz in 400.0f..2400.0f && maxPeakEnergy > 2.0f -> {
                AcousticThreatEvent(
                    signature = ThreatSignature.DRONE_ROTOR,
                    confidence = 0.88f,
                    decibelSpl = splDb,
                    dominantFrequencyHz = dominantFreqHz,
                    timestampNs = timestampNs,
                )
            }
            // 3. SAR Siren: Periodic sweep in 900 Hz - 3200 Hz
            dominantFreqHz in 900.0f..3200.0f && splDb >= 75.0f -> {
                AcousticThreatEvent(
                    signature = ThreatSignature.SAR_SIREN_SOS,
                    confidence = 0.85f,
                    decibelSpl = splDb,
                    dominantFrequencyHz = dominantFreqHz,
                    timestampNs = timestampNs,
                )
            }
            // 4. Heavy Vehicle: Dominant low rumble (< 250 Hz)
            dominantFreqHz in 40.0f..250.0f -> {
                AcousticThreatEvent(
                    signature = ThreatSignature.VEHICLE_HEAVY,
                    confidence = 0.80f,
                    decibelSpl = splDb,
                    dominantFrequencyHz = dominantFreqHz,
                    timestampNs = timestampNs,
                )
            }
            else -> {
                AcousticThreatEvent(
                    signature = ThreatSignature.AMBIENT_NOISE,
                    confidence = 0.60f,
                    decibelSpl = splDb,
                    dominantFrequencyHz = dominantFreqHz,
                    timestampNs = timestampNs,
                )
            }
        }
    }
}
