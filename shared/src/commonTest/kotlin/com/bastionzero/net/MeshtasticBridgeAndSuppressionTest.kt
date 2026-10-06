package com.bastionzero.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeshtasticBridgeAndSuppressionTest {

    private val bridge = MeshtasticProtocolBridge()
    private val suppression = SlottedRebroadcastSuppression(duplicateSuppressionThreshold = 2)

    @Test
    fun testEncodeAndDecodeMeshtasticTextDistress() {
        val fromNode = 0x1A2B3C4DL
        val packetId = 987654321L
        val text = "SOS: Stranded at ridge post, need medical evacuation"

        val packet = bridge.encodeSurvivalDistressToMeshtastic(fromNode, packetId, text, hopLimit = 3)

        assertEquals(0xFFFFFFFFL, packet.toNode)
        assertEquals(fromNode, packet.fromNode)
        assertEquals(MeshtasticProtocolBridge.PORT_TEXT_MESSAGE_APP, packet.portNum)

        val decoded = bridge.decodeMeshtasticPacket(packet)
        assertEquals("!1a2b3c4d", decoded.senderNodeHex)
        assertEquals(text, decoded.textMessage)
    }

    @Test
    fun testSlottedSuppressionWhenDuplicatesOverheard() {
        val packetId = 555123L
        val localNodeId = 0xAA11BB22L

        // Before any duplicates
        val decision1 = suppression.evaluateRelayDecision(packetId, localNodeId)
        assertTrue(decision1.shouldRelay)
        assertTrue(decision1.slottedDelayMs >= 100L)

        // Overhear 1st duplicate from neighbor
        suppression.recordOverheardPacket(packetId)
        val decision2 = suppression.evaluateRelayDecision(packetId, localNodeId)
        assertTrue(decision2.shouldRelay) // threshold is 2

        // Overhear 2nd duplicate
        suppression.recordOverheardPacket(packetId)
        val decision3 = suppression.evaluateRelayDecision(packetId, localNodeId)
        assertFalse(decision3.shouldRelay, "Must suppress broadcast after threshold duplicates overheard")
        assertTrue(decision3.reason.contains("SUPPRESSED"))
    }
}
