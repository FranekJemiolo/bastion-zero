package com.bastionzero.acoustic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AcousticTriangulationEngineTest {

    private val engine = AcousticTriangulationEngine(sampleRateHz = 48000)

    @Test
    fun testTemperatureDependentSoundSpeed() {
        val speed0C = engine.computeSoundSpeed(0.0f)
        val speed20C = engine.computeSoundSpeed(20.0f)
        val speed40C = engine.computeSoundSpeed(40.0f)

        assertEquals(331.3f, speed0C, 0.5f)
        assertTrue(speed20C > 340f && speed20C < 345f, "Speed at 20C should be ~343 m/s")
        assertTrue(speed40C > speed20C, "Speed should increase with temperature")
    }

    @Test
    fun testGunshotImpulseClassificationAndBearing() {
        // Create 3 identical impulse buffers with a slight delay
        val bufferLen = 1000
        val mic0 = FloatArray(bufferLen)
        val mic1 = FloatArray(bufferLen)
        val mic2 = FloatArray(bufferLen)

        // Impulse arrives at mic0 at sample 200, mic1 at sample 210, mic2 at sample 215
        mic0[200] = 0.95f
        mic0[201] = -0.7f
        mic1[210] = 0.90f
        mic1[211] = -0.65f
        mic2[215] = 0.88f
        mic2[216] = -0.60f

        val result = engine.triangulate(
            channelBuffers = listOf(mic0, mic1, mic2),
            ambientTempCelsius = 20.0f,
            dominantFrequencyHz = 2200f,
            riseTimeMs = 0.8f
        )

        assertEquals(AcousticThreatType.GUNSHOT_SUPERSONIC, result.threatType)
        assertTrue(result.confidence > 0.5f, "Confidence should be high for clean impulses")
        assertTrue(result.isReliableFix)
        assertTrue(result.azimuthDegrees in 0.0f..360.0f)
    }

    @Test
    fun testRotorBladeClassification() {
        val mic0 = FloatArray(500) { 0.5f }
        val mic1 = FloatArray(500) { 0.5f }

        val result = engine.triangulate(
            channelBuffers = listOf(mic0, mic1),
            ambientTempCelsius = 15.0f,
            dominantFrequencyHz = 35.0f,
            riseTimeMs = 25.0f
        )

        assertEquals(AcousticThreatType.ROTOR_BLADE_FREQUENCY, result.threatType)
    }
}
