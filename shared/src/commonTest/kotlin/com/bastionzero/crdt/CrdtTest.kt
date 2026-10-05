package com.bastionzero.crdt

import com.bastionzero.mesh.LamportClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LwwElementSetTest {
    private fun s(l: Long, r: String = "a") = Stamp(l, r)

    @Test
    fun laterRemoveBeatsEarlierAdd() {
        val set = LwwElementSet<String>()
        set.add("x", s(1)); set.remove("x", s(2))
        assertFalse(set.contains("x"))
    }

    @Test
    fun laterAddBeatsEarlierRemove() {
        val set = LwwElementSet<String>()
        set.remove("x", s(1)); set.add("x", s(2))
        assertTrue(set.contains("x"))
    }

    @Test
    fun replicaIdBreaksLamportTies() {
        assertTrue(Stamp(5, "b") > Stamp(5, "a"))
    }

    @Test
    fun mergeIsCommutativeAssociativeIdempotent() {
        val a = LwwElementSet<String>().apply { add("p", s(1, "a")); remove("q", s(4, "a")) }
        val b = LwwElementSet<String>().apply { add("q", s(3, "b")); add("p", s(2, "b")) }
        val c = LwwElementSet<String>().apply { remove("p", s(5, "c")); add("r", s(1, "c")) }

        fun join(vararg order: LwwElementSet<String>): Set<String> {
            val acc = LwwElementSet<String>()
            order.forEach { acc.merge(it) }
            return acc.elements()
        }

        assertEquals(join(a, b, c), join(c, b, a))
        assertEquals(join(a, b, c), join(b, a, c, c, a))
        // ab then c == a then bc
        val ab = a.copy().also { it.merge(b) }
        val bc = b.copy().also { it.merge(c) }
        assertEquals(ab.copy().also { it.merge(c) }.elements(), a.copy().also { it.merge(bc) }.elements())
    }
}

class MapPinStoreTest {
    private fun pin(id: String, label: String) = MapPin(id, PinKind.RESOURCE, label, 52_000_000, 21_000_000)

    private fun replica(id: String) = MapPinStore(id, LamportClock())

    /** The pharmacy scenario from the design doc: A says stocked, B later says looted. */
    @Test
    fun laterEditWinsAfterSync() {
        val a = replica("A"); val b = replica("B")
        a.add(pin("pharmacy", "Safe / Stocked"))
        b.merge(a)
        b.add(pin("pharmacy", "Empty / Hostile")) // B's clock already passed A's
        a.merge(b)
        assertEquals("Empty / Hostile", a.pins().single().label)
        assertEquals(a.pins(), b.pins())
    }

    @Test
    fun removalPropagatesAndConverges() {
        val a = replica("A"); val b = replica("B")
        a.add(pin("bridge", "Intact"))
        b.merge(a)
        b.remove("bridge")
        a.merge(b)
        assertTrue(a.pins().isEmpty())
        assertTrue(b.pins().isEmpty())
    }

    @Test
    fun concurrentEditsConvergeRegardlessOfMergeOrder() {
        val a = replica("A"); val b = replica("B")
        a.add(pin("w", "from-A")); b.add(pin("w", "from-B")) // same lamport, different replicas
        val ab = replica("X").also { it.merge(a); it.merge(b) }
        val ba = replica("Y").also { it.merge(b); it.merge(a) }
        assertEquals(ab.pins(), ba.pins())
        assertEquals("from-B", ab.pins().single().label) // "B" > "A" tie-break
    }

    @Test
    fun confirmationsAreAGrowOnlySetOfDistinctReplicas() {
        val a = replica("A"); val b = replica("B"); val c = replica("C")
        a.add(pin("bridge", "Destroyed"))
        b.merge(a); c.merge(a)
        b.confirm("bridge"); c.confirm("bridge"); c.confirm("bridge")
        a.merge(b); a.merge(c); a.merge(c)
        assertEquals(2, a.confirmationCount("bridge"))
    }
}
