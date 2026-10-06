package com.bastionzero.e2e

import com.bastionzero.acoustic.AcousticNeuralClassifier
import com.bastionzero.acoustic.NeuralAcousticClass
import com.bastionzero.airgap.AirgapApkBeacon
import com.bastionzero.hal.GnssSpoofingDetector
import com.bastionzero.hal.HardwarePanicTrigger
import com.bastionzero.hal.RawGnssEpoch
import com.bastionzero.hal.RawGnssSatellite
import com.bastionzero.hal.UsbChipsetType
import com.bastionzero.hal.UsbSerialDeviceInfo
import com.bastionzero.nav.GeoPoint2D
import com.bastionzero.nav.MapSnappingEngine
import com.bastionzero.nav.OfflineVectorMapEngine
import com.bastionzero.net.MeshtasticProtocolBridge
import com.bastionzero.net.SlottedRebroadcastSuppression
import com.bastionzero.rag.EdgeRagSemanticRouter
import com.bastionzero.rag.TriageUrgency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Phase7OperationalE2ETest {

    @Test
    fun testScenario1_PhysicalHardwareAndEwSpoofingDefenseWithMapSnapping() {
        val spoofDetector = GnssSpoofingDetector(agcSpikeThresholdDb = 14.0f)
        val mapEngine = OfflineVectorMapEngine()
        val snappingEngine = MapSnappingEngine(snappingThresholdMeters = 30.0)

        // 1. Verify USB-OTG hardware peripheral identification
        val attachedHardware = UsbSerialDeviceInfo(
            vendorId = 0x1A86,
            productId = 0x7523,
            deviceName = "CH340 USB LoRa Transceiver (868MHz)",
            chipset = UsbChipsetType.CH34X,
            serialNumber = "LORA-NODE-01"
        )
        assertEquals(UsbChipsetType.CH34X, attachedHardware.chipset)

        // 2. Stream nominal GNSS baseline epochs
        for (i in 0..25) {
            spoofDetector.processGnssMeasurement(
                agcLevelDb = -12.0f,
                clockDriftNanosPerSec = 150L,
                satelliteCount = 14,
                timestampNs = i * 1_000_000_000L
            )
        }
        assertFalse(spoofDetector.integrityReport.value.isSpoofed)

        // 3. Electronic Warfare attack: Terrestrial high-power spoofer emits false ephemeris
        val spoofEpoch = RawGnssEpoch(
            timestampNs = 30_000_000_000L,
            clockDriftNanosPerSec = 350_000L,
            satellites = listOf(
                RawGnssSatellite(1, 1, 1575420000.0, 48.0, +18.0, 0.0)
            ),
            averageAgcDb = +18.0f // +30 dB jump over baseline
        )
        spoofDetector.processGnssMeasurement(
            agcLevelDb = spoofEpoch.averageAgcDb,
            clockDriftNanosPerSec = spoofEpoch.clockDriftNanosPerSec,
            satelliteCount = spoofEpoch.satellites.size,
            timestampNs = spoofEpoch.timestampNs
        )

        val report = spoofDetector.integrityReport.value
        assertTrue(report.isSpoofed, "Terrestrial GPS spoofer must be flagged")
        assertTrue(report.fallbackToDeadReckoning, "Must trigger immediate dead reckoning fallback")

        // 4. Dead reckoning inertial trajectory snaps to known offline trail
        val viewport = mapEngine.queryViewport(49.20, 49.25, 22.50, 22.60)
        val activeTrail = viewport.trails.first { it.trailId == "trail_ridge_alpha" }

        // Raw IMU coordinate with 12m lateral drift from ridge crest
        val driftedPdrCoord = GeoPoint2D(49.214, 22.52515)
        val snapResult = snappingEngine.snapToNearestTrail(driftedPdrCoord, listOf(activeTrail))

        assertTrue(snapResult.isSnapped, "Kalman drift must snap to offline contour trail")
        assertTrue(snapResult.distanceMeters < 20.0)
        assertEquals(activeTrail.name, snapResult.activeTrailName)
    }

    @Test
    fun testScenario2_DenseEncampmentBroadcastSuppressionAndMeshtasticInteroperability() {
        val suppression = SlottedRebroadcastSuppression(baseDelayMs = 80L, slotWidthMs = 40L)
        val meshtasticBridge = MeshtasticProtocolBridge()
        val panicTrigger = HardwarePanicTrigger(requiredClickCount = 5)

        // 1. Silent physical button panic trigger (5 rapid volume clicks)
        for (i in 0..3) {
            assertFalse(panicTrigger.registerButtonPress(100L * i))
        }
        val panicFired = panicTrigger.registerButtonPress(400L)
        assertTrue(panicFired, "5th hardware click must trigger silent emergency SOS")

        // 2. Dense mesh cluster receives emergency packet 991001
        val emergencyPacketId = 991001L
        val localNodeId = 0xFEEDBEEFL

        // First overhearing: proceeds with slotted delay
        val relayDecision1 = suppression.evaluateRelayDecision(emergencyPacketId, localNodeId)
        assertTrue(relayDecision1.shouldRelay)
        assertTrue(relayDecision1.slottedDelayMs in 80..480)

        // Neighbors rebroadcast packet -> 2 duplicates overheard
        suppression.recordOverheardPacket(emergencyPacketId)
        suppression.recordOverheardPacket(emergencyPacketId)

        val relayDecision2 = suppression.evaluateRelayDecision(emergencyPacketId, localNodeId)
        assertFalse(relayDecision2.shouldRelay, "Must suppress rebroadcast to avoid RF collision storm")

        // 3. Interoperability: Bridge Bastion Zero alert into civilian Meshtastic channel
        val meshtasticPacket = meshtasticBridge.encodeSurvivalDistressToMeshtastic(
            fromNodeId = localNodeId,
            packetId = emergencyPacketId,
            distressMessage = "BASTION_SOS: Femur Fracture Ridge Alpha"
        )
        assertEquals(0xFFFFFFFFL, meshtasticPacket.toNode)
        assertEquals(MeshtasticProtocolBridge.PORT_TEXT_MESSAGE_APP, meshtasticPacket.portNum)

        val decoded = meshtasticBridge.decodeMeshtasticPacket(meshtasticPacket)
        assertEquals("BASTION_SOS: Femur Fracture Ridge Alpha", decoded.textMessage)
        assertTrue(decoded.senderNodeHex.contains("feedbeef"))
    }

    @Test
    fun testScenario3_PanickedSemanticTriageNeuralAcousticDefenseAndAirgapApkBeam() {
        val semanticRouter = EdgeRagSemanticRouter()
        val neuralClassifier = AcousticNeuralClassifier()
        val apkBeacon = AirgapApkBeacon()

        // 1. Panicked natural language casualty triage
        val agitatedSurvivorVoiceQuery =
            "My friend fell down ravine, bone sticking out of thigh and spurting bright red blood fast!"
        val triageResponse = semanticRouter.routePanickedQuery(agitatedSurvivorVoiceQuery)

        assertEquals(TriageUrgency.P1_IMMEDIATE_LIFE_THREAT, triageResponse.urgencyLevel)
        assertTrue(triageResponse.detectedTraumaCategories.contains("MASSIVE_ARTERIAL_HEMORRHAGE"))
        assertTrue(triageResponse.detectedTraumaCategories.contains("COMPOUND_OPEN_FRACTURE"))
        assertEquals("SOS_MED_ARTERIAL_BLEED", triageResponse.emergencySurvivalPacketCode)
        assertEquals("APPLY TOURNIQUET IMMEDIATELY", triageResponse.immediateActions[0].title)

        // 2. Neural classification of incoming low-frequency drone rotor threat
        val rotorMelSpectrogram = FloatArray(16) { 0.05f }
        rotorMelSpectrogram[0] = 3.8f // 40 Hz blade pass
        rotorMelSpectrogram[1] = 2.9f // 90 Hz harmonic
        val neuralResult = neuralClassifier.classify(rotorMelSpectrogram)

        assertEquals(NeuralAcousticClass.DRONE_ROTOR_BLADE, neuralResult.predictedClass)
        assertTrue(neuralResult.confidence > 0.70f)

        // 3. Air-gapped APK distribution beacon to equip stranded survivor
        val apkSession = apkBeacon.startHostingBeacon("SQUAD_7")
        assertTrue(apkSession.isHosting)
        assertEquals("BASTION_BEAM_SQUAD_7", apkSession.ssid)

        val qrCodeString = apkBeacon.formatQrPairingPayload(apkSession)
        val peerSession = apkBeacon.parseQrPairingPayload(qrCodeString)
        assertNotNull(peerSession)
        assertEquals(apkSession.captivePortalUrl, peerSession.captivePortalUrl)
        assertEquals(apkSession.apkSha256Checksum, peerSession.apkSha256Checksum)

        apkBeacon.recordDownloadCompleted()
        apkBeacon.stopHosting()
    }
}
