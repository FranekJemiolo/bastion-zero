package com.bastionzero.hal

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class RawGnssSatellite(
    val svid: Int,
    val constellationType: Int,
    val carrierFrequencyHz: Double,
    val cn0DbHz: Double,
    val agcDb: Double,
    val pseudorangeRateMps: Double,
)

data class RawGnssEpoch(
    val timestampNs: Long,
    val clockDriftNanosPerSec: Long,
    val satellites: List<RawGnssSatellite>,
    val averageAgcDb: Float,
)

interface GnssRawStream {
    val epochs: SharedFlow<RawGnssEpoch>
    val isListening: StateFlow<Boolean>

    fun startListening()
    fun stopListening()
    fun attachToSpoofingDetector(detector: GnssSpoofingDetector)
}

/**
 * Native Raw GNSS Measurement Ingestor.
 * Ingests low-level satellite carrier and AGC signals for Electronic Warfare & Anti-Spoofing defense.
 */
expect class GnssRawMeasurementIngestor() : GnssRawStream {
    override val epochs: SharedFlow<RawGnssEpoch>
    override val isListening: StateFlow<Boolean>

    override fun startListening()
    override fun stopListening()
    override fun attachToSpoofingDetector(detector: GnssSpoofingDetector)
}
