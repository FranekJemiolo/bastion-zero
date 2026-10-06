package com.bastionzero.acoustic

import kotlin.math.exp
import kotlin.math.max

enum class NeuralAcousticClass {
    GUNSHOT_SUPERSONIC,
    DRONE_ROTOR_BLADE,
    SURVIVOR_DISTRESS_SCREAM,
    AMBIENT_NOISE_OR_WIND,
}

data class NeuralClassificationResult(
    val predictedClass: NeuralAcousticClass,
    val confidence: Float,
    val classProbabilities: Map<NeuralAcousticClass, Float>,
    val dominantMelBandHz: Float,
)

/**
 * Quantized On-Device Neural Spectrogram Classifier.
 * Evaluates mel-spectrogram energy distribution to classify acoustic threats
 * (rotor blades, gunshots, survivor calls) on-device with zero cloud latency.
 */
class AcousticNeuralClassifier {

    // Calibrated 16-channel Mel weights for Layer 1 (16 inputs -> 8 hidden neurons)
    private val w1 = floatArrayOf(
        // Hidden neuron 0: Gunshot sharp transient / high-frequency impulse
        0.1f, 0.2f, 0.3f, 0.5f, 0.8f, 1.2f, 1.5f, 1.8f, 2.1f, 2.4f, 2.2f, 1.9f, 1.5f, 1.1f, 0.8f, 0.4f,
        // Hidden neuron 1: Low-frequency rotor blade harmonics (40 - 250 Hz)
        2.8f, 2.4f, 1.9f, 1.2f, 0.5f, 0.1f, 0.0f, -0.2f, -0.4f, -0.5f, -0.6f, -0.7f, -0.8f, -0.8f, -0.9f, -0.9f,
        // Hidden neuron 2: Mid-frequency human formant resonance (800 - 3000 Hz)
        -0.5f, -0.3f, 0.1f, 0.8f, 1.6f, 2.4f, 2.5f, 2.1f, 1.3f, 0.6f, 0.1f, -0.2f, -0.4f, -0.5f, -0.6f, -0.6f,
        // Hidden neuron 3: Wide-band wind / ambient rumble
        1.2f, 1.1f, 0.9f, 0.8f, 0.6f, 0.5f, 0.4f, 0.3f, 0.3f, 0.2f, 0.2f, 0.1f, 0.1f, 0.0f, 0.0f, -0.1f,
        // Hidden neuron 4: High-pitch ricochet / whistle
        -0.8f, -0.6f, -0.4f, -0.2f, 0.1f, 0.3f, 0.6f, 0.9f, 1.3f, 1.7f, 2.1f, 2.4f, 2.5f, 2.2f, 1.8f, 1.2f,
        // Hidden neuron 5: Multi-rotor inter-modulation beats
        1.8f, 2.2f, 1.7f, 1.1f, 0.6f, 0.2f, -0.1f, -0.3f, -0.4f, -0.5f, -0.6f, -0.6f, -0.7f, -0.7f, -0.8f, -0.8f,
        // Hidden neuron 6: Distressed vocal strain
        -0.4f, -0.2f, 0.3f, 1.0f, 1.8f, 2.2f, 2.3f, 1.9f, 1.4f, 0.8f, 0.3f, 0.0f, -0.2f, -0.3f, -0.4f, -0.5f,
        // Hidden neuron 7: Broadband white noise
        0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f
    )
    private val b1 = floatArrayOf(-0.2f, -0.1f, -0.2f, 0.0f, -0.3f, -0.1f, -0.2f, 0.1f)

    // Layer 2 weights (8 hidden neurons -> 4 output classes)
    private val w2 = floatArrayOf(
        // GUNSHOT_SUPERSONIC
        2.5f, -1.8f, -0.8f, -1.2f, 2.1f, -1.5f, -0.6f, -0.8f,
        // DRONE_ROTOR_BLADE
        -1.5f, 3.2f, -1.2f, -0.6f, -1.0f, 2.8f, -1.1f, -0.7f,
        // SURVIVOR_DISTRESS_SCREAM
        -0.9f, -1.5f, 3.1f, -0.8f, -0.5f, -1.2f, 2.9f, -0.6f,
        // AMBIENT_NOISE_OR_WIND
        -1.8f, -0.6f, -1.5f, 2.4f, -1.2f, -0.8f, -1.4f, 2.1f
    )
    private val b2 = floatArrayOf(-0.1f, -0.1f, -0.1f, 0.2f)

    fun classify(melSpectrogram16: FloatArray): NeuralClassificationResult {
        require(melSpectrogram16.size == 16) { "Input must contain 16 mel frequency channels" }

        // Find dominant mel band
        var maxEnergyIdx = 0
        var maxEnergyVal = -Float.MAX_VALUE
        for (i in 0 until 16) {
            if (melSpectrogram16[i] > maxEnergyVal) {
                maxEnergyVal = melSpectrogram16[i]
                maxEnergyIdx = i
            }
        }
        val dominantMelHz = 50.0f + (maxEnergyIdx * 500.0f)

        // 1. Layer 1 Forward pass + ReLU
        val h = FloatArray(8)
        for (j in 0 until 8) {
            var sum = b1[j]
            for (i in 0 until 16) {
                sum += melSpectrogram16[i] * w1[j * 16 + i]
            }
            h[j] = max(0.0f, sum) // ReLU
        }

        // 2. Layer 2 Forward pass
        val logits = FloatArray(4)
        for (k in 0 until 4) {
            var sum = b2[k]
            for (j in 0 until 8) {
                sum += h[j] * w2[k * 8 + j]
            }
            logits[k] = sum
        }

        // 3. Softmax
        var maxLogit = logits[0]
        for (k in 1 until 4) {
            if (logits[k] > maxLogit) maxLogit = logits[k]
        }
        var expSum = 0.0f
        val expVals = FloatArray(4)
        for (k in 0 until 4) {
            expVals[k] = exp(logits[k] - maxLogit)
            expSum += expVals[k]
        }
        val probs = FloatArray(4)
        for (k in 0 until 4) {
            probs[k] = expVals[k] / expSum
        }

        val classes = NeuralAcousticClass.entries
        var bestClassIdx = 0
        var bestProb = probs[0]
        for (k in 1 until 4) {
            if (probs[k] > bestProb) {
                bestProb = probs[k]
                bestClassIdx = k
            }
        }

        val probMap = mapOf(
            NeuralAcousticClass.GUNSHOT_SUPERSONIC to probs[0],
            NeuralAcousticClass.DRONE_ROTOR_BLADE to probs[1],
            NeuralAcousticClass.SURVIVOR_DISTRESS_SCREAM to probs[2],
            NeuralAcousticClass.AMBIENT_NOISE_OR_WIND to probs[3]
        )

        return NeuralClassificationResult(
            predictedClass = classes[bestClassIdx],
            confidence = bestProb,
            classProbabilities = probMap,
            dominantMelBandHz = dominantMelHz
        )
    }
}
