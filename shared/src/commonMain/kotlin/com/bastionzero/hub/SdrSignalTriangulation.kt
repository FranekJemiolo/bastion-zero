package com.bastionzero.hub

import kotlin.math.round

data class SignalAzimuthReading(
    val compassAzimuthDeg: Float,
    val signalStrengthDbm: Float,
    val frequencyMhz: Float,
)

data class TriangulationTarget(
    val peakAzimuthDeg: Float,
    val peakRssiDbm: Float,
    val beamwidthDeg: Float,
    val confidence: Float,
    val targetType: String,
)

/**
 * AR Signal Triangulation & RF Threat Vectoring Engine.
 * Ingests raw signal strength samples from a USB-C connected SDR (Software-Defined Radio)
 * dongle as the user sweeps their phone in a circle, locating emergency beacons or drones.
 */
class SdrSignalTriangulation(
    private val minSectorSamples: Int = 12,
) {
    private val readings = ArrayList<SignalAzimuthReading>()

    fun recordSample(azimuthDeg: Float, rssiDbm: Float, frequencyMhz: Float = 433.92f) {
        val normalizedAzimuth = ((azimuthDeg % 360f) + 360f) % 360f
        readings.add(SignalAzimuthReading(normalizedAzimuth, rssiDbm, frequencyMhz))
        if (readings.size > 200) {
            readings.removeAt(0)
        }
    }

    fun clear() {
        readings.clear()
    }

    /**
     * Compute the estimated bearing and directional lobe of the RF emitter.
     */
    fun computeBearing(): TriangulationTarget? {
        if (readings.size < minSectorSamples) return null

        // Group into 15-degree azimuth buckets
        val bucketCount = 24
        val bucketSums = FloatArray(bucketCount)
        val bucketCounts = IntArray(bucketCount)

        for (r in readings) {
            val idx = (r.compassAzimuthDeg / 15f).toInt().coerceIn(0, bucketCount - 1)
            bucketSums[idx] += r.signalStrengthDbm
            bucketCounts[idx]++
        }

        var bestIdx = -1
        var bestAvgRssi = -999.0f

        for (i in 0 until bucketCount) {
            if (bucketCounts[i] > 0) {
                val avg = bucketSums[i] / bucketCounts[i]
                if (avg > bestAvgRssi) {
                    bestAvgRssi = avg
                    bestIdx = i
                }
            }
        }

        if (bestIdx == -1) return null

        val peakAzimuth = (bestIdx * 15f) + 7.5f

        // Estimate signal contrast (peak vs mean background)
        val allMean = readings.map { it.signalStrengthDbm }.average().toFloat()
        val contrastDb = bestAvgRssi - allMean
        val confidence = (contrastDb / 15f).coerceIn(0.1f, 0.99f)

        val freq = readings.firstOrNull()?.frequencyMhz ?: 0f
        val targetType = when {
            freq in 406.0f..406.1f -> "COSPAS-SARSAT Emergency Beacon"
            freq in 121.5f..123.0f -> "Civilian Aircraft SAR Emergency Locator (ELT)"
            freq in 868.0f..928.0f -> "ISM LoRa Node / Drone Video Link"
            else -> "RF Threat / Beacon Emitter"
        }

        return TriangulationTarget(
            peakAzimuthDeg = peakAzimuth,
            peakRssiDbm = bestAvgRssi,
            beamwidthDeg = 30.0f,
            confidence = confidence,
            targetType = targetType,
        )
    }
}
