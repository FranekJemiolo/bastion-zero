package com.bastionzero.tactical

import com.bastionzero.acoustic.AcousticThreatType
import kotlin.math.max

/**
 * Perimeter defense threat posture.
 */
enum class PerimeterThreatLevel {
    SECURE_GREEN,      // All perimeter nodes reporting nominal background telemetry
    CAUTION_YELLOW,    // Single sensor event or low-confidence anomaly detected
    BREACH_RED         // Multi-sensor correlation or verified high-priority threat detected
}

/**
 * Deployed tripwire node descriptor.
 */
data class TripwireNode(
    val nodeId: String,
    val distanceMeters: Float,
    val bearingDegrees: Float,
    val lastHeartbeatMillis: Long,
    val isArmed: Boolean
)

/**
 * Normalized perimeter sensor event.
 */
sealed class PerimeterSensorEvent {
    abstract val nodeId: String
    abstract val timestampMillis: Long

    data class AcousticSpike(
        override val nodeId: String,
        override val timestampMillis: Long,
        val threatType: AcousticThreatType,
        val peakDecibels: Float,
        val soundAzimuthDeg: Float
    ) : PerimeterSensorEvent()

    data class SeismicTilt(
        override val nodeId: String,
        override val timestampMillis: Long,
        val tiltDeltaDeg: Float
    ) : PerimeterSensorEvent()

    data class RadiationSpike(
        override val nodeId: String,
        override val timestampMillis: Long,
        val doseRateUsVh: Double
    ) : PerimeterSensorEvent()
}

/**
 * Perimeter security status report.
 */
data class PerimeterStatusReport(
    val threatLevel: PerimeterThreatLevel,
    val activeNodeCount: Int,
    val activeAlertCount: Int,
    val primaryThreatSectorDeg: Float?,
    val threatDescription: String,
    val tacticalGuidance: String
)

/**
 * Distributed Tactical Perimeter & Tripwire Coordinator.
 *
 * Coordinates multiple deployed Bastion Zero nodes into a distributed
 * early-warning tripwire network, correlating acoustic anomalies,
 * structural seismic tilts, and radiological boundary breaches into
 * a synchronized perimeter posture.
 */
class PerimeterDefenseCoordinator(
    private val alertCorrelationWindowMs: Long = 60_000L // 60-second correlation window
) {

    private val registeredNodes = mutableMapOf<String, TripwireNode>()
    private val recentEvents = mutableListOf<PerimeterSensorEvent>()

    /**
     * Registers or updates a perimeter tripwire node.
     */
    fun registerNode(node: TripwireNode) {
        registeredNodes[node.nodeId] = node
    }

    /**
     * Ingests a sensor event from a perimeter node.
     */
    fun ingestEvent(event: PerimeterSensorEvent) {
        recentEvents.add(event)
    }

    /**
     * Evaluates current perimeter posture and prunes expired events.
     */
    fun evaluatePerimeter(currentTimeMillis: Long): PerimeterStatusReport {
        // Prune events outside correlation window
        recentEvents.removeAll { (currentTimeMillis - it.timestampMillis) > alertCorrelationWindowMs }

        val activeNodes = registeredNodes.values.filter {
            (currentTimeMillis - it.lastHeartbeatMillis) <= (alertCorrelationWindowMs * 2) && it.isArmed
        }

        if (recentEvents.isEmpty()) {
            return PerimeterStatusReport(
                threatLevel = PerimeterThreatLevel.SECURE_GREEN,
                activeNodeCount = activeNodes.size,
                activeAlertCount = 0,
                primaryThreatSectorDeg = null,
                threatDescription = "Perimeter clear. All listening nodes report normal baseline telemetry.",
                tacticalGuidance = "Maintain silent watch. Conserve battery."
            )
        }

        // Check for acute critical threats
        var hasGunshot = false
        var hasSevereRadiation = false
        var hasStructuralFailure = false
        var primarySector: Float? = null

        val alertingNodeIds = mutableSetOf<String>()

        for (event in recentEvents) {
            alertingNodeIds.add(event.nodeId)
            val node = registeredNodes[event.nodeId]
            if (node != null && primarySector == null) {
                primarySector = node.bearingDegrees
            }

            when (event) {
                is PerimeterSensorEvent.AcousticSpike -> {
                    if (event.threatType == AcousticThreatType.GUNSHOT_SUPERSONIC) {
                        hasGunshot = true
                        primarySector = event.soundAzimuthDeg
                    }
                }
                is PerimeterSensorEvent.RadiationSpike -> {
                    if (event.doseRateUsVh > 25.0) {
                        hasSevereRadiation = true
                    }
                }
                is PerimeterSensorEvent.SeismicTilt -> {
                    if (event.tiltDeltaDeg > 1.5f) {
                        hasStructuralFailure = true
                    }
                }
            }
        }

        val isBreach = hasGunshot || hasSevereRadiation || hasStructuralFailure || alertingNodeIds.size >= 2

        val level = if (isBreach) {
            PerimeterThreatLevel.BREACH_RED
        } else {
            PerimeterThreatLevel.CAUTION_YELLOW
        }

        val desc = when {
            hasGunshot -> "ACOUSTIC BREACH: Supersonic gunshot detected in sector ${primarySector?.toInt() ?: 0}°!"
            hasSevereRadiation -> "CBRN BREACH: Hazardous radiation spike exceeding 25 uSv/h!"
            hasStructuralFailure -> "SEISMIC BREACH: Severe foundational shift (>1.5°) detected!"
            alertingNodeIds.size >= 2 -> "COORDINATED BREACH: Multiple perimeter nodes (${alertingNodeIds.size}) reporting simultaneous alerts!"
            else -> "PERIMETER CAUTION: Anomaly reported by node ${alertingNodeIds.firstOrNull() ?: "UNKNOWN"}."
        }

        val guidance = if (level == PerimeterThreatLevel.BREACH_RED) {
            "RED ALERT: Assume defensive positions. Evacuate compromised sector immediately."
        } else {
            "YELLOW ALERT: Sensor anomaly detected. Direct visual or acoustic surveillance toward sector."
        }

        return PerimeterStatusReport(
            threatLevel = level,
            activeNodeCount = activeNodes.size,
            activeAlertCount = recentEvents.size,
            primaryThreatSectorDeg = primarySector,
            threatDescription = desc,
            tacticalGuidance = guidance
        )
    }

    /**
     * Clears all alerts and resets state.
     */
    fun clearAlerts() {
        recentEvents.clear()
    }
}
