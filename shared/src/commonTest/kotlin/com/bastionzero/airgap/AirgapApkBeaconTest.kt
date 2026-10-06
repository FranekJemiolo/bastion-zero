package com.bastionzero.airgap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AirgapApkBeaconTest {

    private val beacon = AirgapApkBeacon()

    @Test
    fun testStartHostingAndQrPairingRoundtrip() {
        val session = beacon.startHostingBeacon("ALPHA")
        assertTrue(session.isHosting)
        assertEquals("BASTION_BEAM_ALPHA", session.ssid)
        assertEquals("SURVIVE_ALPHA", session.passkey)

        val qrPayload = beacon.formatQrPairingPayload(session)
        assertTrue(qrPayload.startsWith("BZAPK:"))

        val parsedSession = beacon.parseQrPairingPayload(qrPayload)
        assertNotNull(parsedSession)
        assertEquals(session.ssid, parsedSession.ssid)
        assertEquals(session.passkey, parsedSession.passkey)
        assertEquals(session.captivePortalUrl, parsedSession.captivePortalUrl)
        assertEquals(session.apkSha256Checksum, parsedSession.apkSha256Checksum)

        beacon.recordDownloadCompleted()
        beacon.stopHosting()
    }
}
