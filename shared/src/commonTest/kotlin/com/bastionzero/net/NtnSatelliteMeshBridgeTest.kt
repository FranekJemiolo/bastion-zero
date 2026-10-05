package com.bastionzero.net

import kotlin.test.Test
import kotlin.test.assertEquals

class NtnSatelliteMeshBridgeTest {

    @Test
    fun testQueueingAndOpportunisticUplink() {
        val bridge = NtnSatelliteMeshBridge()

        // Enqueue 3 SOS packets from local BLE mesh
        bridge.enqueueMeshSosPacket("SOS_PACKET_A".encodeToByteArray())
        bridge.enqueueMeshSosPacket("SOS_PACKET_B".encodeToByteArray())
        bridge.enqueueMeshSosPacket("SOS_PACKET_C".encodeToByteArray())

        assertEquals(3, bridge.state.value.queuedMeshSosPacketsCount)
        assertEquals(SatelliteLockStatus.NO_SATELLITE_COVERAGE, bridge.state.value.status)

        // Low satellite elevation (12° < 20° threshold) -> cannot transmit
        val transmittedLow = bridge.onSatelliteLockChanged(hasLock = true, elevationDeg = 12.0f)
        assertEquals(0, transmittedLow)
        assertEquals(SatelliteLockStatus.SEARCHING_ORBITAL_EPHEMERIS, bridge.state.value.status)

        // High satellite elevation (40° > 20° threshold) -> opportunistic burst uplink
        val transmitted = bridge.onSatelliteLockChanged(hasLock = true, elevationDeg = 40.0f)
        assertEquals(3, transmitted)
        assertEquals(SatelliteLockStatus.UPLINK_SUCCESS, bridge.state.value.status)
        assertEquals(0, bridge.state.value.queuedMeshSosPacketsCount)
        assertEquals(3, bridge.state.value.packetsUplinkedTotal)
    }
}
