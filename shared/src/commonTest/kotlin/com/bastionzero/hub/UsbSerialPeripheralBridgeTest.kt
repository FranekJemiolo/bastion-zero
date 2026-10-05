package com.bastionzero.hub

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsbSerialPeripheralBridgeTest {

    @Test
    fun testAttachAndFramingDelimiterParsing() {
        val bridge = UsbSerialPeripheralBridge()
        bridge.onDeviceAttached(UsbDeviceType.GEIGER_COUNTER, 115200)

        assertTrue(bridge.status.value.isConnected)
        assertEquals(UsbDeviceType.GEIGER_COUNTER, bridge.status.value.deviceType)

        // Stream arrives in chunks: "CPM=142\n" split across two packets
        val chunk1 = "CPM=14".encodeToByteArray()
        val packets1 = bridge.ingestRawBytes(chunk1)
        assertEquals(0, packets1.size)

        val chunk2 = "2\n".encodeToByteArray()
        val packets2 = bridge.ingestRawBytes(chunk2)
        assertEquals(1, packets2.size)
        assertEquals("CPM=142\n", packets2[0].decodeToString())

        assertEquals(1, bridge.status.value.packetsFramedTotal)
        assertEquals(chunk1.size + chunk2.size.toLong(), bridge.status.value.bytesReceivedTotal)
    }
}
