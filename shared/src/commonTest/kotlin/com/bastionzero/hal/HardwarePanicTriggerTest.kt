package com.bastionzero.hal

import com.bastionzero.haptics.HapticChord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HardwarePanicTriggerTest {

    @Test
    fun testFiveRapidClicksTriggersPanic() {
        val trigger = HardwarePanicTrigger(requiredClickCount = 5, clickWindowMs = 2000L)

        assertFalse(trigger.registerButtonPress(100L))
        assertFalse(trigger.registerButtonPress(300L))
        assertFalse(trigger.registerButtonPress(500L))
        assertFalse(trigger.registerButtonPress(700L))

        // 5th click within window triggers panic
        val fired = trigger.registerButtonPress(900L)
        assertTrue(fired, "5 clicks within window must fire panic")
    }

    @Test
    fun testSlowClicksDoNotTriggerPanic() {
        val trigger = HardwarePanicTrigger(requiredClickCount = 5, clickWindowMs = 1000L)

        assertFalse(trigger.registerButtonPress(100L))
        assertFalse(trigger.registerButtonPress(1500L)) // 1400ms delta, resets
        assertFalse(trigger.registerButtonPress(3000L))
        assertFalse(trigger.registerButtonPress(4500L))
        assertFalse(trigger.registerButtonPress(6000L))
    }

    @Test
    fun testDisarmedTriggerDoesNotFire() {
        val trigger = HardwarePanicTrigger(requiredClickCount = 3, clickWindowMs = 1000L)
        trigger.setArmed(false)

        assertFalse(trigger.registerButtonPress(100L))
        assertFalse(trigger.registerButtonPress(200L))
        assertFalse(trigger.registerButtonPress(300L))
    }
}
