package com.bastionzero.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BluetoothChannelSoundingTest {

    @Test
    fun testPhaseSlopeAndRttDistanceEstimation() {
        val sounding = BluetoothChannelSounding()

        // 10 meters distance:
        // RTT time of flight = 2 * 10m / c ≈ 66.71 nanoseconds
        val rttNanos = (2.0 * 10.0 / BluetoothChannelSounding.SPEED_OF_LIGHT_M_PER_S) * 1e9

        // Multi-channel steps from 2.402 GHz to 2.440 GHz
        val steps = ArrayList<ChannelSoundingStep>()
        val startFreq = 2.402e9
        val targetDist = 10.0

        for (i in 0..10) {
            val freq = startFreq + (i * 2.0e6) // 2 MHz channel spacing
            // delta_phi = (4 * pi * d / c) * freq
            val phase = (4.0 * kotlin.math.PI * targetDist / BluetoothChannelSounding.SPEED_OF_LIGHT_M_PER_S) * (freq - startFreq)
            steps.add(
                ChannelSoundingStep(
                    channelFrequencyHz = freq,
                    phaseRadians = phase,
                    rttFlightTimeNanos = rttNanos,
                )
            )
        }

        val result = sounding.estimateDistance(steps)
        // Distance should be estimated near 10 meters (+/- 1.5m)
        assertTrue(result.estimatedDistanceMeters in 8.5f..11.5f, "Expected near 10m, got ${result.estimatedDistanceMeters}")
        assertTrue(result.isPhaseAmbiguityResolved)
        assertEquals("BT6_PBR_RTT_FUSED", result.methodUsed)
    }
}
