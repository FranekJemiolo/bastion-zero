package com.bastionzero.medical

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpticalVitalsMonitorTest {

    @Test
    fun testPpgPeakDetectionAndVitalsCalculation() {
        val monitor = OpticalVitalsMonitor()
        monitor.start()

        // Simulate 75 BPM pulse wave: 60 / 75 = 0.8s = 800,000,000 ns per cycle
        // Feed 10 cycles of sinusoidal pulse samples at 30 fps (33,333,333 ns step)
        val cycleNs = 800_000_000L
        val stepNs = 33_333_333L
        var latestReading: VitalsReading? = null

        var currentNs = 1_000_000_000L
        for (cycle in 0..8) {
            val cycleStart = currentNs
            while (currentNs - cycleStart < cycleNs) {
                val progress = ((currentNs - cycleStart).toDouble() / cycleNs) * 2.0 * kotlin.math.PI
                val red = (0.7 + 0.15 * kotlin.math.sin(progress)).toFloat()
                val ir = (0.8 + 0.10 * kotlin.math.sin(progress)).toFloat()

                val reading = monitor.processFrame(
                    OpticalVitalsSample(
                        timestampNs = currentNs,
                        redIntensity = red,
                        infraredOrGreenIntensity = ir,
                    )
                )
                if (reading != null) {
                    latestReading = reading
                }
                currentNs += stepNs
            }
        }

        assertNotNull(latestReading)
        // Calculated BPM should be near 75 BPM (+/- 10 BPM)
        assertTrue(latestReading.heartRateBpm in 65..85, "Expected BPM near 75, got ${latestReading.heartRateBpm}")
        assertTrue(latestReading.spo2Percent in 88..100, "Expected nominal SpO2, got ${latestReading.spo2Percent}")
        assertTrue(latestReading.confidence > 0.6f)
    }
}
