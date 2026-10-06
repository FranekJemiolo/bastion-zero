package com.bastionzero.tactical

import com.bastionzero.acoustic.AcousticThreatType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerimeterDefenseCoordinatorTest {

    private val coordinator = PerimeterDefenseCoordinator(alertCorrelationWindowMs = 30_000L)

    @Test
    fun testNominalPerimeterState() {
        coordinator.registerNode(
            TripwireNode("node_north", 50f, 0f, 1000L, isArmed = true)
        )
        coordinator.registerNode(
            TripwireNode("node_south", 50f, 180f, 1000L, isArmed = true)
        )

        val report = coordinator.evaluatePerimeter(1500L)
        assertEquals(PerimeterThreatLevel.SECURE_GREEN, report.threatLevel)
        assertEquals(2, report.activeNodeCount)
        assertEquals(0, report.activeAlertCount)
    }

    @Test
    fun testSingleSensorAlertTriggersCaution() {
        coordinator.registerNode(
            TripwireNode("node_east", 30f, 90f, 1000L, isArmed = true)
        )

        coordinator.ingestEvent(
            PerimeterSensorEvent.AcousticSpike(
                nodeId = "node_east",
                timestampMillis = 1500L,
                threatType = AcousticThreatType.ROTOR_BLADE_FREQUENCY,
                peakDecibels = 78f,
                soundAzimuthDeg = 90f
            )
        )

        val report = coordinator.evaluatePerimeter(2000L)
        assertEquals(PerimeterThreatLevel.CAUTION_YELLOW, report.threatLevel)
        assertEquals(1, report.activeAlertCount)
        assertTrue(report.threatDescription.contains("PERIMETER CAUTION"))
    }

    @Test
    fun testGunshotImpulseTriggersInstantRedBreach() {
        coordinator.registerNode(
            TripwireNode("node_west", 40f, 270f, 1000L, isArmed = true)
        )

        coordinator.ingestEvent(
            PerimeterSensorEvent.AcousticSpike(
                nodeId = "node_west",
                timestampMillis = 1200L,
                threatType = AcousticThreatType.GUNSHOT_SUPERSONIC,
                peakDecibels = 110f,
                soundAzimuthDeg = 275f
            )
        )

        val report = coordinator.evaluatePerimeter(1500L)
        assertEquals(PerimeterThreatLevel.BREACH_RED, report.threatLevel)
        assertTrue(report.threatDescription.contains("ACOUSTIC BREACH"))
        assertEquals(275f, report.primaryThreatSectorDeg)
    }

    @Test
    fun testMultiNodeCorrelatedAlertTriggersRedBreach() {
        coordinator.registerNode(
            TripwireNode("node_alpha", 25f, 45f, 1000L, isArmed = true)
        )
        coordinator.registerNode(
            TripwireNode("node_bravo", 35f, 135f, 1000L, isArmed = true)
        )

        // Two distinct nodes report anomalies within the 30s window
        coordinator.ingestEvent(
            PerimeterSensorEvent.SeismicTilt("node_alpha", 1200L, 0.8f)
        )
        coordinator.ingestEvent(
            PerimeterSensorEvent.RadiationSpike("node_bravo", 1400L, 8.5)
        )

        val report = coordinator.evaluatePerimeter(1600L)
        assertEquals(PerimeterThreatLevel.BREACH_RED, report.threatLevel)
        assertTrue(report.threatDescription.contains("COORDINATED BREACH"))
    }
}
