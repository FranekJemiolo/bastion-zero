package com.bastionzero.net

data class SlottedRelayDecision(
    val shouldRelay: Boolean,
    val slottedDelayMs: Long,
    val overheardDuplicateCount: Int,
    val reason: String,
)

/**
 * Slotted Rebroadcast Suppression Engine.
 * Mitigates broadcast collision storms in dense survivor clusters by adding pseudo-random
 * slotted backoff intervals and suppressing re-transmissions if duplicates are overheard.
 */
class SlottedRebroadcastSuppression(
    private val baseDelayMs: Long = 100L,
    private val slotWidthMs: Long = 50L,
    private val slotCount: Int = 10,
    private val duplicateSuppressionThreshold: Int = 2,
) {

    private val overheardPacketCounts = HashMap<Long, Int>()

    /**
     * Compute slotted backoff delay for a packet before relaying.
     */
    fun computeSlottedDelay(packetId: Long, localNodeId: Long): Long {
        val hash = (packetId xor localNodeId).hashCode() and 0x7FFFFFFF
        val slot = hash % slotCount
        return baseDelayMs + (slot * slotWidthMs)
    }

    /**
     * Record an overheard identical packet from a neighbor node.
     */
    fun recordOverheardPacket(packetId: Long) {
        val count = (overheardPacketCounts[packetId] ?: 0) + 1
        overheardPacketCounts[packetId] = count
    }

    /**
     * Evaluate whether this node should proceed with relaying or suppress the transmission.
     */
    fun evaluateRelayDecision(packetId: Long, localNodeId: Long): SlottedRelayDecision {
        val duplicates = overheardPacketCounts[packetId] ?: 0
        val delay = computeSlottedDelay(packetId, localNodeId)

        return if (duplicates >= duplicateSuppressionThreshold) {
            SlottedRelayDecision(
                shouldRelay = false,
                slottedDelayMs = delay,
                overheardDuplicateCount = duplicates,
                reason = "SUPPRESSED: Overheard $duplicates duplicates from neighboring nodes"
            )
        } else {
            SlottedRelayDecision(
                shouldRelay = true,
                slottedDelayMs = delay,
                overheardDuplicateCount = duplicates,
                reason = "FORWARD: Slot clear, proceeding with broadcast after ${delay}ms"
            )
        }
    }

    fun clearHistory() {
        overheardPacketCounts.clear()
    }
}
