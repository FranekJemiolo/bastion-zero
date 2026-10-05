package com.bastionzero.acoustic

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class UltrasonicModemConfig(
    val sampleRateHz: Int = 44100,
    val markFrequencyHz: Double = 19500.0,  // Binary 1
    val spaceFrequencyHz: Double = 18500.0, // Binary 0
    val preambleFrequencyHz: Double = 20500.0,
    val baudRate: Int = 50, // 50 bits/sec (robust in echoic caverns)
)

/**
 * Ultrasonic Acoustic Physical Layer Modem.
 * Provides air-gapped data transmission using high-frequency audio (18 kHz - 22 kHz)
 * through the smartphone's loudspeaker and microphone when all RF (cellular, BLE, Wi-Fi, LoRa)
 * is jammed or completely blocked by subterranean cave walls.
 */
class UltrasonicModem(
    val config: UltrasonicModemConfig = UltrasonicModemConfig(),
) {
    private val samplesPerBit = config.sampleRateHz / config.baudRate

    /**
     * Modulate a byte payload into 32-bit floating-point audio PCM samples
     * using Audio Frequency Shift Keying (AFSK) in the ultrasonic spectrum.
     */
    fun modulate(payload: ByteArray): FloatArray {
        // Bitstream: Preamble (8 sync bits) + 8-bit length + payload bits + 8-bit checksum
        val bits = ArrayList<Boolean>()

        // 1. Preamble sync pattern: alternating 1-0-1-0-1-0-1-1
        val preamble = byteArrayOf(0xAB.toByte())
        appendByteBits(preamble[0], bits)

        // 2. Length byte
        appendByteBits(payload.size.toByte(), bits)

        // 3. Payload bytes
        for (b in payload) {
            appendByteBits(b, bits)
        }

        // 4. Simple XOR checksum
        var checksum: Byte = 0
        for (b in payload) checksum = (checksum.toInt() xor b.toInt()).toByte()
        appendByteBits(checksum, bits)

        // Synthesize AFSK audio waveform
        val totalSamples = bits.size * samplesPerBit
        val audioSamples = FloatArray(totalSamples)

        var sampleIndex = 0
        var phase = 0.0

        for (bit in bits) {
            val freq = if (bit) config.markFrequencyHz else config.spaceFrequencyHz
            val phaseIncrement = 2.0 * PI * freq / config.sampleRateHz

            for (i in 0 until samplesPerBit) {
                audioSamples[sampleIndex++] = (sin(phase) * 0.5).toFloat()
                phase += phaseIncrement
                if (phase > 2.0 * PI) phase -= 2.0 * PI
            }
        }

        return audioSamples
    }

    /**
     * Calculate Goertzel energy at a specific target frequency in an audio buffer.
     * Efficient single-bin DFT detector for FSK tone discrimination.
     */
    fun calculateToneEnergy(samples: FloatArray, offset: Int, length: Int, targetFreqHz: Double): Double {
        val k = (0.5 + (length * targetFreqHz / config.sampleRateHz)).toInt()
        val omega = (2.0 * PI * k) / length
        val coeff = 2.0 * cos(omega)

        var q0 = 0.0
        var q1 = 0.0
        var q2 = 0.0

        val end = (offset + length).coerceAtMost(samples.size)
        for (i in offset until end) {
            q0 = coeff * q1 - q2 + samples[i]
            q2 = q1
            q1 = q0
        }

        return q1 * q1 + q2 * q2 - q1 * q2 * coeff
    }

    private fun appendByteBits(byte: Byte, target: ArrayList<Boolean>) {
        val v = byte.toInt() and 0xFF
        for (bit in 7 downTo 0) {
            target.add(((v shr bit) and 1) == 1)
        }
    }
}
