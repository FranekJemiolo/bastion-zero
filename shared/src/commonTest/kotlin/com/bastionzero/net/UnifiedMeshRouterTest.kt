package com.bastionzero.net

import com.bastionzero.proto.SurvivalPacket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnifiedMeshRouterTest {

    @Test
    fun testDefaultRouteSelectionPrefersAvailableBle() {
        val router = UnifiedMeshRouter()

        val samplePacket = SurvivalPacket(
            packetId = 1001L,
            type = SurvivalPacket.PacketType.CHAT,
        )

        val decision = router.routePacket(samplePacket)
        assertFalse(decision.isEmergencyBroadcast)
        assertEquals(listOf(TransportTier.BLE_MESH), decision.selectedTransports)
    }

    @Test
    fun testFailoverToLoRaWhenBleIsDown() {
        val router = UnifiedMeshRouter()

        // BLE drops connection
        router.updateLink(TransportTier.BLE_MESH, isAvailable = false)
        // LoRa radio connected
        router.updateLink(TransportTier.LORA_TACTICAL, isAvailable = true, rssiDbm = -95)

        val samplePacket = SurvivalPacket(
            packetId = 1002L,
            type = SurvivalPacket.PacketType.PIN_ADD,
        )

        val decision = router.routePacket(samplePacket)
        assertFalse(decision.isEmergencyBroadcast)
        assertEquals(listOf(TransportTier.LORA_TACTICAL), decision.selectedTransports)
        assertTrue(decision.reason.contains("OPTIMAL_LINK"))
    }

    @Test
    fun testEmergencySosPreemptsAndBroadcastsOnAllActiveTransports() {
        val router = UnifiedMeshRouter()

        // Enable BLE, LoRa, and NTN Satellite
        router.updateLink(TransportTier.BLE_MESH, isAvailable = true)
        router.updateLink(TransportTier.LORA_TACTICAL, isAvailable = true)
        router.updateLink(TransportTier.NTN_SATELLITE, isAvailable = true)

        val sosPacket = SurvivalPacket(
            packetId = 9999L,
            type = SurvivalPacket.PacketType.SOS_MEDICAL,
        )

        val decision = router.routePacket(sosPacket)
        assertTrue(decision.isEmergencyBroadcast)
        assertEquals(3, decision.selectedTransports.size)
        assertTrue(decision.selectedTransports.contains(TransportTier.BLE_MESH))
        assertTrue(decision.selectedTransports.contains(TransportTier.LORA_TACTICAL))
        assertTrue(decision.selectedTransports.contains(TransportTier.NTN_SATELLITE))
        assertTrue(decision.reason.contains("PREEMPTION"))
    }
}
