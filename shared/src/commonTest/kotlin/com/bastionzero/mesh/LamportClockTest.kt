package com.bastionzero.mesh

import kotlin.test.Test
import kotlin.test.assertEquals

class LamportClockTest {
    @Test
    fun tickIsMonotonic() {
        val c = LamportClock()
        assertEquals(1, c.tick())
        assertEquals(2, c.tick())
    }

    @Test
    fun observeJumpsPastRemote() {
        val c = LamportClock(3)
        assertEquals(11, c.observe(10))
        assertEquals(12, c.observe(2)) // older remote never moves the clock back
    }
}
