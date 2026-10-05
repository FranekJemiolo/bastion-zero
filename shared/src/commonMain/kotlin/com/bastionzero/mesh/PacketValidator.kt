package com.bastionzero.mesh

import com.bastionzero.crypto.Ed25519
import com.bastionzero.proto.SurvivalPacket

enum class Verdict {
    ACCEPT,

    /** Wrong field sizes or impossible values. Dropped before any crypto work. */
    MALFORMED,

    /** Exact signature already seen (loop or replay). */
    DUPLICATE,

    /** Lamport counter too far behind the sender's high-water mark (old replay). */
    STALE,

    BAD_SIGNATURE,
}

/**
 * Anti-replay state: a rolling cache of the last [cacheCapacity] signatures plus a
 * per-sender sliding window over Lamport counters (the IPsec/DTLS technique).
 *
 * The window, not only the signature cache, is what stops replays once the cache
 * has rotated. Late-but-fresh packets that arrive out of order over a different
 * flood path are still accepted as long as they fall inside [windowSize].
 *
 * Reads ([peek]) never mutate; only [commit] does, and it must be called only after
 * the signature verified, otherwise forged packets could advance a victim's window.
 *
 * Not thread-safe: confine to the mesh dispatcher.
 */
class ReplayGuard(
    private val windowSize: Int = 64,
    private val cacheCapacity: Int = 500,
    private val maxSenders: Int = 1024,
) {
    init {
        require(windowSize in 1..64) { "windowSize must fit in a 64-bit bitmap" }
    }

    private class Window(var highest: Long, var bitmap: Long)

    private val seenSignatures = LinkedHashSet<String>()
    private val windows = LinkedHashMap<String, Window>()

    fun peek(senderKey: String, lamport: Long, signatureKey: String): Verdict {
        if (signatureKey in seenSignatures) return Verdict.DUPLICATE
        val w = windows[senderKey] ?: return Verdict.ACCEPT
        if (lamport > w.highest) return Verdict.ACCEPT
        val diff = w.highest - lamport
        if (diff >= windowSize) return Verdict.STALE
        if (((w.bitmap ushr diff.toInt()) and 1L) == 1L) return Verdict.DUPLICATE
        return Verdict.ACCEPT
    }

    fun commit(senderKey: String, lamport: Long, signatureKey: String) {
        seenSignatures.add(signatureKey)
        while (seenSignatures.size > cacheCapacity) {
            seenSignatures.remove(seenSignatures.first())
        }

        val w = windows[senderKey]
        if (w == null) {
            windows[senderKey] = Window(lamport, 1L)
            while (windows.size > maxSenders) windows.remove(windows.keys.first())
            return
        }
        if (lamport > w.highest) {
            val shift = lamport - w.highest
            w.bitmap = if (shift >= 64) 0L else w.bitmap shl shift.toInt()
            w.bitmap = w.bitmap or 1L
            w.highest = lamport
        } else {
            w.bitmap = w.bitmap or (1L shl (w.highest - lamport).toInt())
        }
    }
}

/**
 * Inbound pipeline, cheapest check first so malicious floods cost the least battery:
 * structure -> duplicate/stale (hash lookups) -> Ed25519 verify -> commit + clock merge.
 */
class PacketValidator(
    private val ed: Ed25519,
    private val guard: ReplayGuard,
    private val clock: LamportClock,
) {
    fun validate(packet: SurvivalPacket): Verdict {
        if (packet.senderId.size != Ed25519.PUBLIC_KEY_SIZE ||
            packet.signature.size != Ed25519.SIGNATURE_SIZE ||
            packet.lamport <= 0L
        ) return Verdict.MALFORMED

        val senderKey = packet.senderId.hex()
        val sigKey = packet.signature.hex()

        val pre = guard.peek(senderKey, packet.lamport, sigKey)
        if (pre != Verdict.ACCEPT) return pre

        if (!packet.verifySignature(ed)) return Verdict.BAD_SIGNATURE

        guard.commit(senderKey, packet.lamport, sigKey)
        clock.observe(packet.lamport)
        return Verdict.ACCEPT
    }
}
