package com.bastionzero.acoustic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AcousticNeuralClassifierTest {

    private val classifier = AcousticNeuralClassifier()

    @Test
    fun testDroneRotorAcousticClassification() {
        // Rotor blade energy concentrated in low frequencies (40 - 200 Hz -> bins 0, 1)
        val melInput = FloatArray(16) { 0.05f }
        melInput[0] = 3.2f
        melInput[1] = 2.8f

        val result = classifier.classify(melInput)
        assertEquals(NeuralAcousticClass.DRONE_ROTOR_BLADE, result.predictedClass)
        assertTrue(result.confidence > 0.60f, "Should confidently classify drone rotor")
    }

    @Test
    fun testGunshotImpulseClassification() {
        // Gunshot shockwave energy concentrated across mid-high frequencies (bins 6-11)
        val melInput = FloatArray(16) { 0.1f }
        for (i in 6..11) {
            melInput[i] = 3.5f
        }

        val result = classifier.classify(melInput)
        assertEquals(NeuralAcousticClass.GUNSHOT_SUPERSONIC, result.predictedClass)
        assertTrue(result.confidence > 0.60f, "Should confidently classify gunshot impulse")
    }

    @Test
    fun testSurvivorScreamClassification() {
        // Human distress formant energy concentrated around 1-3 kHz (bins 4, 5, 6)
        val melInput = FloatArray(16) { 0.1f }
        melInput[4] = 2.9f
        melInput[5] = 3.4f
        melInput[6] = 2.6f

        val result = classifier.classify(melInput)
        assertEquals(NeuralAcousticClass.SURVIVOR_DISTRESS_SCREAM, result.predictedClass)
        assertTrue(result.confidence > 0.50f, "Should classify survivor scream")
    }
}
