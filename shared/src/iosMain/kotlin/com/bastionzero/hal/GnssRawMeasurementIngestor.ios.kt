package com.bastionzero.hal

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS actual implementation of [GnssRawMeasurementIngestor].
 * Graceful fallback adhering to Apple CoreLocation API limitations (no raw AGC or clock bias access).
 */
actual class GnssRawMeasurementIngestor : GnssRawStream {

    private val _epochs = MutableSharedFlow<RawGnssEpoch>(extraBufferCapacity = 32)
    actual override val epochs: SharedFlow<RawGnssEpoch> = _epochs.asSharedFlow()

    private val _isListening = MutableStateFlow(false)
    actual override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var attachedDetector: GnssSpoofingDetector? = null

    actual override fun startListening() {
        _isListening.value = true
    }

    actual override fun stopListening() {
        _isListening.value = false
    }

    actual override fun attachToSpoofingDetector(detector: GnssSpoofingDetector) {
        attachedDetector = detector
    }
}
