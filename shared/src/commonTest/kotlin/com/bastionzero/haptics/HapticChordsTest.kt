package com.bastionzero.haptics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HapticChordsTest {
    @Test
    fun allPatternsAreWellFormed() {
        HapticChord.entries.forEach { chord ->
            val p = chord.pattern
            assertEquals(p.timingsMs.size, p.amplitudes.size, chord.name)
            p.amplitudes.forEachIndexed { i, a ->
                assertTrue(a in 0..255, "${chord.name}[$i] amplitude $a")
                if (i % 2 == 0) assertEquals(0, a, "${chord.name}[$i] must be an OFF slot")
                else assertTrue(a > 0, "${chord.name}[$i] must be an ON slot")
            }
            assertTrue(p.timingsMs.all { it >= 0 })
            assertTrue(p.timingsMs.sum() < 5_000, "${chord.name} should be short")
        }
    }

    @Test
    fun rattlesnakeEscalates() {
        val p = HapticChord.HAZARD_APPROACHING.pattern
        val ons = p.amplitudes.filterIndexed { i, _ -> i % 2 == 1 }
        val gaps = p.timingsMs.filterIndexed { i, _ -> i % 2 == 0 && i > 0 }
        assertEquals(ons.sorted(), ons)
        assertEquals(gaps.sortedDescending(), gaps)
        assertTrue(ons.last() > ons.first())
    }

    @Test
    fun medicalSosIsTwoThuds() {
        assertEquals(2, HapticChord.MEDICAL_SOS.pattern.amplitudes.count { it == 255 })
    }
}
