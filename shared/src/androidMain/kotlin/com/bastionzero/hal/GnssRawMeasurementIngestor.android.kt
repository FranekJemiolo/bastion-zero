package com.bastionzero.hal

import android.content.Context
import android.location.GnssClock
import android.location.GnssMeasurement
import android.location.GnssMeasurementsEvent
import android.location.LocationManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Android actual implementation of [GnssRawMeasurementIngestor].
 * Hooks into [LocationManager.registerGnssMeasurementsCallback] to stream live AGC and clock bias.
 */
actual class GnssRawMeasurementIngestor(
    private val context: Context,
) : GnssRawStream {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _epochs = MutableSharedFlow<RawGnssEpoch>(extraBufferCapacity = 32)
    actual override val epochs: SharedFlow<RawGnssEpoch> = _epochs.asSharedFlow()

    private val _isListening = MutableStateFlow(false)
    actual override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val locationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private var attachedDetector: GnssSpoofingDetector? = null
    private var lastFullBiasNanos: Long = 0L
    private var lastBiasTimestampNanos: Long = 0L

    private val gnssCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        object : GnssMeasurementsEvent.Callback() {
            override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
                processEvent(event)
            }
        }
    } else {
        null
    }

    actual override fun startListening() {
        if (_isListening.value) return
        val manager = locationManager ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssCallback != null) {
                val registered = manager.registerGnssMeasurementsCallback(gnssCallback)
                if (registered) {
                    _isListening.value = true
                }
            }
        } catch (_: SecurityException) {
            // Missing ACCESS_FINE_LOCATION permission
        } catch (_: Throwable) {
            // Hardware doesn't support raw measurements
        }
    }

    actual override fun stopListening() {
        if (!_isListening.value) return
        val manager = locationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssCallback != null) {
            manager.unregisterGnssMeasurementsCallback(gnssCallback)
        }
        _isListening.value = false
    }

    actual override fun attachToSpoofingDetector(detector: GnssSpoofingDetector) {
        attachedDetector = detector
    }

    private fun processEvent(event: GnssMeasurementsEvent) {
        val clock: GnssClock = event.clock
        val timestampNs = clock.timeNanos

        // Calculate clock drift in nanos per second
        var clockDriftNanosPerSec = 0L
        if (clock.hasBiasNanos() && clock.hasFullBiasNanos()) {
            val currentFullBias = clock.fullBiasNanos
            if (lastBiasTimestampNanos > 0L) {
                val dtSec = (timestampNs - lastBiasTimestampNanos) / 1_000_000_000.0
                if (dtSec > 0.1) {
                    val dBias = currentFullBias - lastFullBiasNanos
                    clockDriftNanosPerSec = (dBias / dtSec).toLong()
                }
            }
            lastFullBiasNanos = currentFullBias
            lastBiasTimestampNanos = timestampNs
        }

        var totalAgc = 0.0
        var agcCount = 0
        val satList = ArrayList<RawGnssSatellite>()

        for (m in event.measurements) {
            val agc: Double = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && m.hasAutomaticGainControlLevelDb()) {
                m.automaticGainControlLevelDb
            } else {
                -10.0 // Default nominal AGC
            }

            if (agc > -90.0) {
                totalAgc += agc
                agcCount++
            }

            satList.add(
                RawGnssSatellite(
                    svid = m.svid,
                    constellationType = m.constellationType,
                    carrierFrequencyHz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && m.hasCarrierFrequencyHz()) m.carrierFrequencyHz.toDouble() else 1575420000.0,
                    cn0DbHz = m.cn0DbHz,
                    agcDb = agc,
                    pseudorangeRateMps = m.pseudorangeRateMetersPerSecond
                )
            )
        }

        val avgAgc = if (agcCount > 0) (totalAgc / agcCount).toFloat() else 0f
        val epoch = RawGnssEpoch(
            timestampNs = timestampNs,
            clockDriftNanosPerSec = clockDriftNanosPerSec,
            satellites = satList,
            averageAgcDb = avgAgc
        )

        scope.launch {
            _epochs.emit(epoch)
        }

        attachedDetector?.processGnssMeasurement(
            agcLevelDb = avgAgc,
            clockDriftNanosPerSec = clockDriftNanosPerSec,
            satelliteCount = satList.size,
            timestampNs = timestampNs
        )
    }
}
