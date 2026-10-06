package com.bastionzero.hal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UsbSerialHostDriverTest {

    @Test
    fun testDeviceInfoProperties() {
        val info = UsbSerialDeviceInfo(
            vendorId = 0x10C4,
            productId = 0xEA60,
            deviceName = "CP2102 USB to UART Bridge",
            chipset = UsbChipsetType.CP210X,
            serialNumber = "0001"
        )

        assertEquals(0x10C4, info.vendorId)
        assertEquals(0xEA60, info.productId)
        assertEquals(UsbChipsetType.CP210X, info.chipset)
        assertEquals("0001", info.serialNumber)
    }

    @Test
    fun testChipsetIdentification() {
        val ftdi = UsbChipsetType.FTDI
        val cp210x = UsbChipsetType.CP210X
        val ch34x = UsbChipsetType.CH34X
        val cdc = UsbChipsetType.CDC_ACM

        assertTrue(ftdi != cp210x)
        assertEquals("CH34X", ch34x.name)
        assertEquals("CDC_ACM", cdc.name)
    }
}
