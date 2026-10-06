package com.bastionzero.airgap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AirgapBundleSyncTest {

    @Test
    fun testBundleEncodingAndSequentialIngestion() {
        val sync = AirgapBundleSync()
        val receiver = AirgapBundleReceiver(sync)

        val samplePayload = "BASTION_AIRGAP_PAYLOAD_MAP_PINS_LAT_52.123_LON_21.456_HAZARD_MINEFIELD".encodeToByteArray()
        val frames = sync.encodeBundle("bundle-001", samplePayload, maxChunkBytes = 16)

        assertTrue(frames.size > 1)

        var finalStatus: IngestStatus? = null
        for (frame in frames) {
            finalStatus = receiver.ingestFrame(frame)
        }

        assertNotNull(finalStatus)
        assertTrue(finalStatus.isComplete)
        assertEquals(frames.size, finalStatus.chunksReceived)
        assertNotNull(finalStatus.assembledData)
        assertEquals(samplePayload.decodeToString(), finalStatus.assembledData!!.decodeToString())
    }

    @Test
    fun testOutOfOrderFrameIngestionReassemblesAccurately() {
        val sync = AirgapBundleSync()
        val receiver = AirgapBundleReceiver(sync)

        val samplePayload = "EMERGENCY_FIELD_HOSPITAL_COORDINATES_SUPPLY_WATER_ANTIBIOTICS".encodeToByteArray()
        val frames = sync.encodeBundle("bundle-002", samplePayload, maxChunkBytes = 12)

        // Scramble frames order (simulating camera capturing animated QR frames at random offsets)
        val scrambled = frames.reversed()

        var status: IngestStatus? = null
        for (frame in scrambled) {
            status = receiver.ingestFrame(frame)
        }

        assertNotNull(status)
        assertTrue(status.isComplete)
        assertNotNull(status.assembledData)
        assertEquals(samplePayload.decodeToString(), status.assembledData!!.decodeToString())
    }

    @Test
    fun testCorruptedFrameIsRejected() {
        val sync = AirgapBundleSync()
        val receiver = AirgapBundleReceiver(sync)

        val samplePayload = "SECURE_DATA_PACKET".encodeToByteArray()
        val frames = sync.encodeBundle("bundle-003", samplePayload, maxChunkBytes = 10)

        // Corrupt frame CRC
        val corruptedFrame = frames[0].substringBeforeLast(":") + ":99999"

        val status = receiver.ingestFrame(corruptedFrame)
        assertNull(status)
    }
}
