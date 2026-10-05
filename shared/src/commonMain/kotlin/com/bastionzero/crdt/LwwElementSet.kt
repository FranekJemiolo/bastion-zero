package com.bastionzero.crdt

/**
 * Total order for CRDT writes: Lamport counter first, then replica id as a
 * deterministic tie-breaker so every replica picks the same winner.
 */
data class Stamp(val lamport: Long, val replicaId: String) : Comparable<Stamp> {
    override fun compareTo(other: Stamp): Int {
        val c = lamport.compareTo(other.lamport)
        return if (c != 0) c else replicaId.compareTo(other.replicaId)
    }
}

/**
 * LWW-Element-Set (Shapiro et al.): two maps element -> latest stamp, one for adds
 * and one for removes. An element is present iff its add stamp is >= its remove
 * stamp (add-biased). [merge] is a per-element max, so it is commutative,
 * associative and idempotent, which is what lets pins converge with no server.
 *
 * Not thread-safe: confine to the mesh dispatcher.
 */
class LwwElementSet<E> {
    private val adds = HashMap<E, Stamp>()
    private val removes = HashMap<E, Stamp>()

    /** @return true if the state changed. */
    fun add(element: E, stamp: Stamp): Boolean = raise(adds, element, stamp)

    /** @return true if the state changed. */
    fun remove(element: E, stamp: Stamp): Boolean = raise(removes, element, stamp)

    fun contains(element: E): Boolean {
        val a = adds[element] ?: return false
        val r = removes[element] ?: return true
        return a >= r
    }

    fun elements(): Set<E> = adds.keys.filterTo(HashSet()) { contains(it) }

    fun addStamp(element: E): Stamp? = adds[element]

    /** Join with [other] in place. @return true if anything changed. */
    fun merge(other: LwwElementSet<E>): Boolean {
        var changed = false
        for ((e, s) in other.adds) changed = raise(adds, e, s) || changed
        for ((e, s) in other.removes) changed = raise(removes, e, s) || changed
        return changed
    }

    fun copy(): LwwElementSet<E> = LwwElementSet<E>().also { it.merge(this) }

    private fun raise(map: MutableMap<E, Stamp>, element: E, stamp: Stamp): Boolean {
        val current = map[element]
        if (current != null && current >= stamp) return false
        map[element] = stamp
        return true
    }
}
