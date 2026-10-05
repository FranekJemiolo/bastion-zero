package com.bastionzero.acoustic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UltrasonicModemTest {

    @Test
    fun testModulateProducesValidPcmWaveform() {
        val modem = UltrasonicModem()

        val payload = byteArrayOf(0x01, 0x02, 0x03)
        val pcmSamples = modem.modulate(payload)

        // Expected bits = preamble (8) + length (8) + 3 payload bytes (24) + checksum (8) = 48 bits
        // samplesPerBit = 44100 / 50 = 882 samples
        // total samples = 48 * 882 = 42336
        val expectedSamples = 48 * (modem.config.sampleRateHz / modem.config.baudRate)
        assertEquals(expectedSamples, pcmSamples.size)

        // All samples should be normalized between -1.0 and 1.0
        for (sample in pcmSamples) {
            assertTrue(sample in -1.0f..1.0f)
        }
    }

    @Test
    fun testGoertzelToneEnergyDiscrimination() {
        val modem = UltrasonicModem()
        val sampleRate = modem.config.sampleRateHz
        val markFreq = modem.config.markFrequencyHz // 19500 Hz
        val spaceFreq = modem.config.spaceFrequencyHz // 18500 Hz

        // Synthesize 1000 samples of pure mark tone (19500 Hz)
        val n = 1000
        val markBuffer = FloatArray(n) { i ->
            (kotlin.math.sin(2.0 * kotlin.math.PI * markFreq * i / sampleRate)).toFloat()
        }

        val energyAtMark = modem.calculateToneEnergy(markBuffer, 0, n, markFreq)
        val energyAtSpace = modem.calculateToneEnergy(markBuffer, 0, n, spaceFreq)

        // Mark energy should be substantially higher than Space energy
        assertTrue(energyAtMark > energyAtSpace * 10.0)
    }
}
