package com.bastionzero.trauma

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KinematicTraumaLoggerTest {

    @Test
    fun testNormalMovementDoesNotTriggerTrauma() {
        val logger = KinematicTraumaLogger()

        // 1G normal gravity
        val event = logger.processSample(
            timestampNs = 1_000_000_000L,
            axG = 0f, ayG = 0f, azG = 1.0f,
        )

        assertNull(event)
        assertEquals(TraumaSeverity.NORMAL, logger.state.value.activeSeverity)
        assertEquals(1.0f, logger.state.value.currentAccelerationG)
    }

    @Test
    fun testHighGImpactTriggersTraumaAlert() {
        val logger = KinematicTraumaLogger()

        // 14G impact
        val event = logger.processSample(
            timestampNs = 1_500_000_000L,
            axG = 2f, ayG = 2f, azG = 14f,
            gxDegPerSec = 240f, gyDegPerSec = 0f, gzDegPerSec = 0f,
        )

        assertNotNull(event)
        assertTrue(event.peakAccelerationG >= 14.0f)
        assertEquals(TraumaSeverity.SEVERE_TRAUMA, event.severity)
        assertTrue(event.lockScreenTriageAlert.contains("SEVERE TRAUMA IMPACT ALERT"))
        assertTrue(event.lockScreenTriageAlert.contains("BB-SEVERE_TRAUMA"))
    }

    @Test
    fun testFreeFallFollowedByImpactCalculatesDropHeight() {
        val logger = KinematicTraumaLogger()

        // 1 second of near-zero G free fall (e.g. falling down a ravine)
        val startNs = 1_000_000_000L
        for (i in 0..10) {
            val t = startNs + (i * 100_000_000L) // 0 to 1.0s
            val event = logger.processSample(
                timestampNs = t,
                axG = 0.05f, ayG = 0.05f, azG = 0.05f,
            )
            assertNull(event)
            assertTrue(logger.state.value.isInFreeFall)
        }

        // Impact at 1.0 second: 12G
        val impactNs = startNs + 1_000_000_000L
        val impactEvent = logger.processSample(
            timestampNs = impactNs,
            axG = 0f, ayG = 0f, azG = 12.0f,
        )

        assertNotNull(impactEvent)
        assertEquals(TraumaSeverity.SEVERE_TRAUMA, impactEvent.severity)
        // h = 0.5 * 9.80665 * 1.0^2 ≈ 4.9 meters
        assertTrue(impactEvent.estimatedDropHeightMeters in 4.5f..5.5f)
        assertTrue(impactEvent.lockScreenTriageAlert.contains("Vertical drop:"))
    }
}
