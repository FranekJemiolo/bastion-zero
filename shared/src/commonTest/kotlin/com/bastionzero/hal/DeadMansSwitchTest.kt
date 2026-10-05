package com.bastionzero.hal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeadMansSwitchTest {

    @Test
    fun testPreallocatedBufferAndArmedStatus() {
        val dms = DeadMansSwitch()
        val initial = dms.state.value
        assertEquals(DeadMansSwitchStatus.STANDBY_ARMED, initial.status)
        assertEquals(100, initial.batteryPercent)
        assertFalse(initial.isBeaconTransmitting)
        assertFalse(initial.haltExecuted)
    }

    @Test
    fun testAutonomousTriggerAt5PercentBattery() {
        var haltInvoked = false
        val dms = DeadMansSwitch(
            onHaltRequested = { haltInvoked = true }
        )

        dms.updateTelemetryCache(
            lat = 50.0647,
            lon = 19.9450,
            altitudeMeters = 220f,
            vitals = VitalMetrics(heartRateBpm = 80, spO2Percent = 97, peakGForceImpact = 2.4f),
            imageGrayscale = ByteArray(1024) { 0x55.toByte() },
        )

        // At 6% battery: should NOT trigger
        dms.checkBatteryLevel(batteryPercent = 6, timestampNs = 1_000_000_000L)
        assertEquals(DeadMansSwitchStatus.STANDBY_ARMED, dms.state.value.status)
        assertFalse(haltInvoked)

        // At 5% battery: triggers emergency sequence
        dms.checkBatteryLevel(batteryPercent = 5, timestampNs = 1_001_000_000L)
        val triggered = dms.state.value
        assertEquals(DeadMansSwitchStatus.BEACON_LOCKED, triggered.status)
        assertTrue(triggered.isBeaconTransmitting)
        assertTrue(triggered.haltExecuted)
        assertTrue(haltInvoked)
        assertTrue(triggered.payloadSizeBytes > 1024)

        // Verify packed payload starts with magic header "B0Z0-SOS"
        val payload = dms.getPayloadBytes()
        val header = payload.decodeToString(0, 8)
        assertEquals("B0Z0-SOS", header)
    }

    @Test
    fun testDisarmPreventsTrigger() {
        var haltInvoked = false
        val dms = DeadMansSwitch(
            onHaltRequested = { haltInvoked = true }
        )
        dms.disarm()
        assertEquals(DeadMansSwitchStatus.DISARMED, dms.state.value.status)

        dms.checkBatteryLevel(batteryPercent = 3, timestampNs = 1_000_000L)
        assertEquals(DeadMansSwitchStatus.DISARMED, dms.state.value.status)
        assertFalse(haltInvoked)
    }
}
