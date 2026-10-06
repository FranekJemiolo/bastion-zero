package com.bastionzero.net

import com.bastionzero.proto.SurvivalPacket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TransportTier {
    BLE_MESH,
    LORA_TACTICAL,
    ULTRASONIC_AFSK,
    NTN_SATELLITE,
}

data class TransportLinkStatus(
    val tier: TransportTier,
    val isAvailable: Boolean,
    val rssiDbm: Int = -80,
    val packetLossPercent: Int = 0,
    val latencyMs: Int = 50,
    val energyCostPerKbMilliJoules: Int = 10,
) {
    /**
     * Compute a quality score (0 to 100). Higher is better.
     * Penalizes packet loss, high latency, and high energy cost.
     */
    fun qualityScore(): Int {
        if (!isAvailable) return 0
        var score = 100
        score -= packetLossPercent.coerceIn(0, 50)
        score -= (latencyMs / 20).coerceIn(0, 30)
        score -= (energyCostPerKbMilliJoules / 5).coerceIn(0, 20)
        return score.coerceIn(1, 100)
    }
}

data class RoutingDecision(
    val selectedTransports: List<TransportTier>,
    val isEmergencyBroadcast: Boolean,
    val reason: String,
)

data class UnifiedRouterState(
    val linkStatuses: Map<TransportTier, TransportLinkStatus> = emptyMap(),
    val totalPacketsRouted: Long = 0,
    val emergencyPacketsPreempted: Long = 0,
    val lastRoutingDecision: RoutingDecision? = null,
)

/**
 * Unified multi-transport priority arbitrator.
 * Manages BLE, LoRa, Ultrasonic, and NTN Satellite uplinks under a single
 * resilience policy with automatic failover and SOS preemption.
 */
class UnifiedMeshRouter {

    private val links = mutableMapOf<TransportTier, TransportLinkStatus>()

    init {
        // Default initialized states
        links[TransportTier.BLE_MESH] = TransportLinkStatus(
            tier = TransportTier.BLE_MESH,
            isAvailable = true,
            rssiDbm = -70,
            packetLossPercent = 5,
            latencyMs = 30,
            energyCostPerKbMilliJoules = 5,
        )
        links[TransportTier.LORA_TACTICAL] = TransportLinkStatus(
            tier = TransportTier.LORA_TACTICAL,
            isAvailable = false,
            rssiDbm = -110,
            packetLossPercent = 10,
            latencyMs = 300,
            energyCostPerKbMilliJoules = 40,
        )
        links[TransportTier.ULTRASONIC_AFSK] = TransportLinkStatus(
            tier = TransportTier.ULTRASONIC_AFSK,
            isAvailable = false,
            rssiDbm = -40,
            packetLossPercent = 15,
            latencyMs = 500,
            energyCostPerKbMilliJoules = 25,
        )
        links[TransportTier.NTN_SATELLITE] = TransportLinkStatus(
            tier = TransportTier.NTN_SATELLITE,
            isAvailable = false,
            rssiDbm = -120,
            packetLossPercent = 2,
            latencyMs = 2500,
            energyCostPerKbMilliJoules = 150,
        )
    }

    private val _state = MutableStateFlow(
        UnifiedRouterState(linkStatuses = links.toMap())
    )
    val state: StateFlow<UnifiedRouterState> = _state.asStateFlow()

    fun updateLink(
        tier: TransportTier,
        isAvailable: Boolean,
        rssiDbm: Int? = null,
        packetLossPercent: Int? = null,
        latencyMs: Int? = null,
    ) {
        val current = links[tier] ?: TransportLinkStatus(tier, isAvailable)
        val updated = current.copy(
            isAvailable = isAvailable,
            rssiDbm = rssiDbm ?: current.rssiDbm,
            packetLossPercent = packetLossPercent ?: current.packetLossPercent,
            latencyMs = latencyMs ?: current.latencyMs,
        )
        links[tier] = updated
        _state.value = _state.value.copy(linkStatuses = links.toMap())
    }

    /**
     * Determine optimal physical transports for an outgoing [SurvivalPacket].
     * Emergency packets (SOS_MEDICAL, SOS_PANIC) trigger multi-transport
     * concurrent flood broadcast across all active physical interfaces.
     */
    fun routePacket(packet: SurvivalPacket): RoutingDecision {
        val isEmergency = packet.type == SurvivalPacket.PacketType.SOS_MEDICAL ||
                packet.type == SurvivalPacket.PacketType.SOS_PANIC

        val activeLinks = links.values.filter { it.isAvailable }

        val decision = if (isEmergency) {
            // Concurrent broadcast on all active physical links
            val chosen = if (activeLinks.isEmpty()) {
                listOf(TransportTier.BLE_MESH) // Attempt fallback
            } else {
                activeLinks.map { it.tier }
            }
            RoutingDecision(
                selectedTransports = chosen,
                isEmergencyBroadcast = true,
                reason = "PREEMPTION: Emergency SOS broadcast concurrent across all ${chosen.size} active links",
            )
        } else {
            // Standard packet: select single best quality link based on score
            val bestLink = activeLinks.maxByOrNull { it.qualityScore() }
            if (bestLink != null) {
                RoutingDecision(
                    selectedTransports = listOf(bestLink.tier),
                    isEmergencyBroadcast = false,
                    reason = "OPTIMAL_LINK: Selected ${bestLink.tier} (Quality score: ${bestLink.qualityScore()}/100)",
                )
            } else {
                // All links down: opportunistic queue on BLE
                RoutingDecision(
                    selectedTransports = listOf(TransportTier.BLE_MESH),
                    isEmergencyBroadcast = false,
                    reason = "DISCONNECTED: Queued for opportunistic BLE connection",
                )
            }
        }

        _state.value = _state.value.copy(
            totalPacketsRouted = _state.value.totalPacketsRouted + 1,
            emergencyPacketsPreempted = if (isEmergency) _state.value.emergencyPacketsPreempted + 1 else _state.value.emergencyPacketsPreempted,
            lastRoutingDecision = decision,
        )

        return decision
    }
}
