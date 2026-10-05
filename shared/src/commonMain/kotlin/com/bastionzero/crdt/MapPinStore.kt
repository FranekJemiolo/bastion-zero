package com.bastionzero.crdt

import com.bastionzero.mesh.LamportClock

enum class PinKind { HAZARD, RESOURCE }

/** A crowd-sourced map pin. [id] is a UUID chosen by the author; coordinates are micro-degrees. */
data class MapPin(
    val id: String,
    val kind: PinKind,
    val label: String,
    val latE6: Int,
    val lonE6: Int,
)

/**
 * Offline map-pin replica composed of three convergent parts:
 *  - an [LwwElementSet] of pin ids (existence / deletion),
 *  - an LWW register per id holding the pin's latest content,
 *  - a grow-only set of confirming replica ids per pin (powers "Verified x3").
 *
 * Remote changes arrive through the `apply*` methods (from verified mesh packets);
 * whole-replica sync uses [merge]. Both advance the local Lamport clock.
 */
class MapPinStore(
    val replicaId: String,
    private val clock: LamportClock,
) {
    private val ids = LwwElementSet<String>()
    private val content = HashMap<String, Pair<Stamp, MapPin>>()
    private val confirmations = HashMap<String, MutableSet<String>>()

    // ---- local authoring -------------------------------------------------

    fun add(pin: MapPin): Stamp {
        val stamp = Stamp(clock.tick(), replicaId)
        applyAdd(pin, stamp)
        return stamp
    }

    fun remove(pinId: String): Stamp {
        val stamp = Stamp(clock.tick(), replicaId)
        applyRemove(pinId, stamp)
        return stamp
    }

    /** Local user confirms a pin they also observed. */
    fun confirm(pinId: String) = applyConfirm(pinId, replicaId)

    // ---- remote / replayed operations ------------------------------------

    fun applyAdd(pin: MapPin, stamp: Stamp) {
        clock.observe(stamp.lamport)
        ids.add(pin.id, stamp)
        val current = content[pin.id]
        if (current == null || current.first < stamp) content[pin.id] = stamp to pin
    }

    fun applyRemove(pinId: String, stamp: Stamp) {
        clock.observe(stamp.lamport)
        ids.remove(pinId, stamp)
    }

    fun applyConfirm(pinId: String, byReplica: String) {
        confirmations.getOrPut(pinId) { HashSet() }.add(byReplica)
    }

    // ---- queries ----------------------------------------------------------

    /** Visible pins, deterministic order (by id). */
    fun pins(): List<MapPin> =
        ids.elements().mapNotNull { content[it]?.second }.sortedBy { it.id }

    fun confirmationCount(pinId: String): Int = confirmations[pinId]?.size ?: 0

    // ---- convergence ------------------------------------------------------

    /** Join [other] into this replica. @return true if anything changed. */
    fun merge(other: MapPinStore): Boolean {
        var changed = ids.merge(other.ids)
        for ((id, entry) in other.content) {
            val current = content[id]
            if (current == null || current.first < entry.first) {
                content[id] = entry
                changed = true
            }
        }
        for ((id, replicas) in other.confirmations) {
            if (confirmations.getOrPut(id) { HashSet() }.addAll(replicas)) changed = true
        }
        other.maxLamport()?.let { clock.observe(it) }
        return changed
    }

    private fun maxLamport(): Long? =
        content.values.maxOfOrNull { it.first.lamport }
}
