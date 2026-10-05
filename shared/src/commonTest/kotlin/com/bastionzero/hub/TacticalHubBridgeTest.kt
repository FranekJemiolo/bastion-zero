package com.bastionzero.hub

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TacticalHubBridgeTest {

    @Test
    fun testLoRaPacketFramingAndUnframing() {
        val bridge = TacticalHubBridge()

        val samplePayload = "BASTION_SOS_PAYLOAD_LAT52_LON21".encodeToByteArray()
        val frame = bridge.frameLoRaPacket(samplePayload)

        assertTrue(frame.size >= 4 + samplePayload.size)
        assertEquals(0xBA.toByte(), frame[0])
        assertEquals(0x70.toByte(), frame[1])

        val recovered = bridge.unframeLoRaPacket(frame)
        assertNotNull(recovered)
        assertEquals(samplePayload.decodeToString(), recovered.decodeToString())
    }

    @Test
    fun testCorruptedLoRaPacketIsRejected() {
        val bridge = TacticalHubBridge()

        val samplePayload = "INTEGRITY_CHECK".encodeToByteArray()
        val frame = bridge.frameLoRaPacket(samplePayload)

        // Corrupt a byte in payload
        frame[5] = (frame[5].toInt() xor 0xFF).toByte()

        val recovered = bridge.unframeLoRaPacket(frame)
        assertNull(recovered)
    }

    @Test
    fun testGeigerRadiationHazardTracking() {
        val bridge = TacticalHubBridge()

        // Normal background radiation 0.15 uSv/h
        val normal = bridge.processRadiationSample(
            doseRateMicroSvPerHour = 0.15f,
            timestampNs = 1_000_000_000L,
        )
        assertEquals(RadiationHazardLevel.BACKGROUND_NORMAL, normal.hazardLevel)
        assertEquals(false, normal.isExclusionZoneTriggered)

        // Acute radiation hotspot 120 uSv/h
        val acute = bridge.processRadiationSample(
            doseRateMicroSvPerHour = 120.0f,
            timestampNs = 2_000_000_000L,
        )
        assertEquals(RadiationHazardLevel.ACUTE_EXCLUSION_ZONE, acute.hazardLevel)
        assertEquals(true, acute.isExclusionZoneTriggered)
        assertTrue(acute.safeStayTimeHoursRemaining < 2500f)
    }
}
