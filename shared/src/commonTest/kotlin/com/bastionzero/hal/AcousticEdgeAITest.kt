package com.bastionzero.hal

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AcousticEdgeAITest {

    @Test
    fun testCooleyTukeyFFTOnPureSineWave() {
        val n = 256
        val targetCycle = 16 // 16 full cycles inside 256 samples -> bin 16
        val real = FloatArray(n) { i ->
            sin(2.0 * PI * targetCycle * i / n).toFloat()
        }
        val imag = FloatArray(n)

        FastFourierTransform.fft(real, imag)

        // Find bin with maximum magnitude
        var maxMag = 0f
        var maxBin = 0
        for (k in 0 until n / 2) {
            val mag = kotlin.math.sqrt((real[k] * real[k] + imag[k] * imag[k]).toDouble()).toFloat()
            if (mag > maxMag) {
                maxMag = mag
                maxBin = k
            }
        }

        assertEquals(targetCycle, maxBin, "Dominant FFT bin should match sine wave cycle count")
    }

    @Test
    fun testDecibelGatingRejectsQuietAmbientNoise() {
        val ai = AcousticEdgeAI(
            sampleRateHz = 16000,
            decibelThresholdDb = 70.0f,
        )

        val quietSamples = FloatArray(32000) { 0.0002f } // ~35 dB SPL
        ai.processAudioChunk(quietSamples, timestampNs = 1_000_000_000L)

        assertTrue(ai.currentSpl.value < 70.0f, "Quiet audio should be below 70 dB threshold")
    }

    @Test
    fun testDroneRotorAcousticClassification() {
        val sampleRate = 16000
        val ai = AcousticEdgeAI(
            sampleRateHz = sampleRate,
            decibelThresholdDb = 70.0f,
            fftSize = 512,
            hopSize = 256,
        )

        // Generate 2 seconds of 1000 Hz harmonic drone rotor sound at ~80 dB SPL (amplitude 0.2f)
        val droneAudio = FloatArray(sampleRate * 2) { i ->
            (0.25f * sin(2.0 * PI * 1000.0 * i / sampleRate)).toFloat()
        }

        ai.processAudioChunk(droneAudio, timestampNs = 1_000_000_000L)

        assertTrue(ai.currentSpl.value >= 70.0f, "Audio should pass 70 dB hardware gate")
        val spectrogram = ai.computeSpectrogram()
        val event = ai.classifySpectrogram(spectrogram, ai.currentSpl.value, timestampNs = 1_000_000_000L)

        assertEquals(ThreatSignature.DRONE_ROTOR, event.signature)
        assertTrue(event.confidence >= 0.8f)
        assertEquals(1000.0f, event.dominantFrequencyHz, 50.0f)
    }

    @Test
    fun testBallisticGunshotAcousticClassification() {
        val sampleRate = 16000
        val ai = AcousticEdgeAI(sampleRateHz = sampleRate)

        val gunshotAudio = FloatArray(sampleRate * 2) { 0.001f }
        // Sudden explosive shockwave burst at sample 5000 for 100 samples
        for (i in 5000 until 5100) {
            gunshotAudio[i] = 0.95f
        }

        ai.processAudioChunk(gunshotAudio, timestampNs = 2_000_000_000L)

        val spectrogram = ai.computeSpectrogram()
        val event = ai.classifySpectrogram(spectrogram, splDb = 95.0f, timestampNs = 2_000_000_000L)

        assertEquals(ThreatSignature.BALLISTIC_GUNSHOT, event.signature)
        assertTrue(event.confidence > 0.85f)
    }
}
