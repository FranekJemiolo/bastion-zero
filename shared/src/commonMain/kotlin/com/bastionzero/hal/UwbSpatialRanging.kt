package com.bastionzero.hal

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class UwbRangingVector(
    val peerId: String,
    val distanceMeters: Float,
    val azimuthDegrees: Float, // Horizontal angle [-180, 180]
    val elevationDegrees: Float, // Vertical angle [-90, 90]
    val timestampNs: Long,
    val lineOfSight: Boolean,
)

interface UwbRangingSession {
    val rangingVectors: SharedFlow<UwbRangingVector>
    fun startRanging(targetPeerId: String)
    fun stopRanging()
}

/**
 * Common Ultra-Wideband (UWB) Spatial Ranging Controller.
 * Enables centimeter-level 3D vectoring for locating trapped or buried survivors in rubble/avalanches.
 */
class UwbSpatialController : UwbRangingSession {
    private val _rangingVectors = MutableSharedFlow<UwbRangingVector>(extraBufferCapacity = 32)
    override val rangingVectors: SharedFlow<UwbRangingVector> = _rangingVectors.asSharedFlow()

    private var activePeerId: String? = null

    override fun startRanging(targetPeerId: String) {
        activePeerId = targetPeerId
    }

    override fun stopRanging() {
        activePeerId = null
    }

    fun emitRangingVector(vector: UwbRangingVector) {
        if (vector.peerId == activePeerId || activePeerId == null) {
            _rangingVectors.tryEmit(vector)
        }
    }
}
