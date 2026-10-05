package com.bastionzero.e2e

import com.bastionzero.acoustic.UltrasonicModem
import com.bastionzero.crdt.MapPinStore
import com.bastionzero.crdt.PinType
import com.bastionzero.crypto.Ed25519
import com.bastionzero.hub.RadiationHazardLevel
import com.bastionzero.hub.TacticalHubBridge
import com.bastionzero.medical.OpticalVitalsMonitor
import com.bastionzero.medical.OpticalVitalsSample
import com.bastionzero.medical.WoundPhotogrammetry
import com.bastionzero.medical.WoundType
import com.bastionzero.mesh.LamportClock
import com.bastionzero.mesh.PacketValidator
import com.bastionzero.proto.PacketType
import com.bastionzero.proto.SurvivalPacket
import com.bastionzero.testing.FakeEd25519
import com.bastionzero.thermal.SurfaceMaterial
import com.bastionzero.thermal.ThermalImagingEngine
import com.bastionzero.trauma.KinematicTraumaLogger
import com.bastionzero.trauma.TraumaSeverity
import okio.ByteString.Companion.toByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DisasterScenarioE2ETest {

    @Test
    fun testFullDisasterTriageAndMeshToLoRaRelayE2E() {
        val ed25519: Ed25519 = FakeEd25519()

        // 1. Node A (Victim): Setup sensors and keys
        val keyPairA = ed25519.generateKeyPair()
        val traumaLoggerA = KinematicTraumaLogger()
        val vitalsMonitorA = OpticalVitalsMonitor()
        vitalsMonitorA.start()
        val clockA = LamportClock()

        // Simulate Ravine Fall: 1.0s of near zero-G (< 0.2G) followed by 14G impact
        val t0 = 1_000_000_000L
        traumaLoggerA.processSample(t0, 0.05f, 0.05f, 0.05f)
        val impactEvent = traumaLoggerA.processSample(
            t0 + 1_000_000_000L,
            axG = 2.0f, ayG = 2.0f, azG = 14.0f,
            gxDegPerSec = 360f,
        )

        assertNotNull(impactEvent)
        assertEquals(TraumaSeverity.SEVERE_TRAUMA, impactEvent.severity)
        assertTrue(impactEvent.estimatedDropHeightMeters in 4.5f..5.5f)

        // Simulate shock vitals: Tachycardia (125 BPM)
        val cycleNs = 480_000_000L // ~125 BPM
        var sampleTime = t0 + 1_000_000_000L
        for (i in 0..120) {
            val progress = (i * 33_333_333.0 / cycleNs) * 2.0 * kotlin.math.PI
            vitalsMonitorA.processFrame(
                OpticalVitalsSample(
                    timestampNs = sampleTime,
                    redIntensity = (0.7 + 0.15 * kotlin.math.sin(progress)).toFloat(),
                    infraredOrGreenIntensity = (0.8 + 0.10 * kotlin.math.sin(progress)).toFloat(),
                )
            )
            sampleTime += 33_333_333L
        }

        val vitals = vitalsMonitorA.state.value.currentReading
        assertNotNull(vitals)
        assertTrue(vitals.heartRateBpm > 100)

        // 2. Package into SurvivalPacket Protobuf
        val triagePayloadString = "${impactEvent.lockScreenTriageAlert} | Vitals: ${vitals.heartRateBpm} BPM SpO2:${vitals.spo2Percent}%"
        val payloadBytes = triagePayloadString.encodeToByteArray()

        val unsignedPacket = SurvivalPacket(
            packet_id = "pkt-trauma-001",
            logical_clock = clockA.tick(),
            sender_id = keyPairA.publicKeyHex,
            type = PacketType.SOS_MEDICAL,
            lat_micro = 49200000,
            lon_micro = 22400000,
            altitude_meters = 1150,
            ttl = 3,
            payload = payloadBytes.toByteString(),
            signature = ByteArray(0).toByteString(),
        )

        // Sign packet
        val canonicalBytes = unsignedPacket.copy(signature = ByteArray(0).toByteString()).encode()
        val signature = ed25519.sign(canonicalBytes, keyPairA.privateKey)
        val signedPacketA = unsignedPacket.copy(signature = signature.toByteString())

        // 3. Relay through Node B
        val validatorB = PacketValidator(ed25519)
        assertTrue(validatorB.validate(signedPacketA))

        // Node B decrements TTL and forwards
        val forwardedPacketB = signedPacketA.copy(ttl = signedPacketA.ttl - 1)
        assertEquals(2, forwardedPacketB.ttl)

        // 4. Relay through Node C
        val validatorC = PacketValidator(ed25519)
        assertTrue(validatorC.validate(forwardedPacketB))
        val forwardedPacketC = forwardedPacketB.copy(ttl = forwardedPacketB.ttl - 1)
        assertEquals(1, forwardedPacketC.ttl)

        // 5. Node D (Tactical Hub Base Station): Ingest and bridge over LoRa
        val validatorD = PacketValidator(ed25519)
        assertTrue(validatorD.validate(forwardedPacketC))

        val hubBridgeD = TacticalHubBridge()
        hubBridgeD.setLoRaConnected(true, 915.0f)

        // Frame over LoRa PHY (0xBA70 sync word + CRC16)
        val loraFrame = hubBridgeD.frameLoRaPacket(forwardedPacketC.encode())
        assertTrue(loraFrame.size > 4)

        // Base station LoRa receiver un-frames packet
        val recoveredBytes = hubBridgeD.unframeLoRaPacket(loraFrame)
        assertNotNull(recoveredBytes)

        val recoveredPacket = SurvivalPacket.ADAPTER.decode(recoveredBytes)
        assertEquals("pkt-trauma-001", recoveredPacket.packet_id)
        assertEquals(PacketType.SOS_MEDICAL, recoveredPacket.type)

        val recoveredText = recoveredPacket.payload.toByteArray().decodeToString()
        assertTrue(recoveredText.contains("SEVERE TRAUMA IMPACT ALERT"))
        assertTrue(recoveredText.contains("BB-SEVERE_TRAUMA"))
    }

    @Test
    fun testRadiationExclusionAndCrdtSyncE2E() {
        val hubBridge = TacticalHubBridge()

        // 1. Ingest normal reading
        val normal = hubBridge.processRadiationSample(0.12f, 1_000_000_000L)
        assertEquals(RadiationHazardLevel.BACKGROUND_NORMAL, normal.hazardLevel)

        // 2. Walk into acute contamination: 150 uSv/h
        val acute = hubBridge.processRadiationSample(150.0f, 2_000_000_000L)
        assertEquals(RadiationHazardLevel.ACUTE_EXCLUSION_ZONE, acute.hazardLevel)
        assertTrue(acute.isExclusionZoneTriggered)

        // 3. Create Hazard Pin in CRDT MapPinStore
        val pinStoreA = MapPinStore()
        pinStoreA.upsertPin(
            pinId = "hazard-rad-zone-1",
            lat = 49.321,
            lon = 22.456,
            type = PinType.HAZARD,
            title = "CRITICAL RADIATION EXCLUSION ZONE (150 uSv/h)",
            author = "Node_Alpha",
        )

        // 4. Synchronize with Peer PinStore B
        val pinStoreB = MapPinStore()
        val allPinsA = pinStoreA.getAllPins()
        assertEquals(1, allPinsA.size)

        for (pin in allPinsA) {
            pinStoreB.upsertPin(
                pinId = pin.id,
                lat = pin.lat,
                lon = pin.lon,
                type = pin.type,
                title = pin.title,
                author = pin.author,
            )
        }

        val allPinsB = pinStoreB.getAllPins()
        assertEquals(1, allPinsB.size)
        assertEquals("CRITICAL RADIATION EXCLUSION ZONE (150 uSv/h)", allPinsB[0].title)
    }

    @Test
    fun testThermalScaldPreventionAndWoundResuscitationE2E() {
        val thermalEngine = ThermalImagingEngine()
        val photogrammetry = WoundPhotogrammetry()

        // 1. Measuring shiny metal door handle in burning building:
        // Apparent reading is cool 24°C due to mirror reflection
        val reading = thermalEngine.calculateTrueTemperature(
            apparentTempCelsius = 24.0f,
            material = SurfaceMaterial.POLISHED_METAL,
        )

        assertTrue(reading.isScaldHazard)
        assertTrue(reading.safetyWarning!!.contains("BURN HAZARD WARNING"))

        // 2. Survivor applies soot patch (emissivity 0.95) to measure true temperature
        val patchReading = thermalEngine.calculateTrueTemperature(
            apparentTempCelsius = 95.0f,
            material = SurfaceMaterial.TARGET_PATCH_TAPE_SOOT,
        )
        assertTrue(patchReading.correctedTempCelsius in 93.0f..97.0f)
        assertTrue(patchReading.isScaldHazard) // 95°C is dangerous

        // 3. Victim sustained burn injury on arm (800 cm^2)
        val assessment = photogrammetry.assessWound(
            segmentedPixelCount = 20000,
            mmPerPixel = 2.0f, // 20000 * 4 = 80,000 mm^2 = 800 cm^2
            patientWeightKg = 80.0f,
            woundType = WoundType.FULL_THICKNESS_BURN,
        )

        assertEquals(800.0f, assessment.surfaceAreaSquareCm)
        assertTrue(assessment.estimatedTbsaPercent in 4.0f..5.5f)
        assertNotNull(assessment.fluidProtocol)
        assertTrue(assessment.fluidProtocol!!.total24HrFluidMl > 1000)
    }

    @Test
    fun testAirGappedUltrasonicModemFallbackE2E() {
        val modem = UltrasonicModem()

        val secretSosBytes = "BASTION_SOS_CAVE_TRAPPED".encodeToByteArray()
        val pcm = modem.modulate(secretSosBytes)

        assertTrue(pcm.isNotEmpty())
        // Verify audio energy at mark frequency (19.5 kHz) vs random out-of-band frequency (5 kHz)
        val markEnergy = modem.calculateToneEnergy(pcm, 0, 1000, 19500.0)
        val outOfBandEnergy = modem.calculateToneEnergy(pcm, 0, 1000, 5000.0)

        assertTrue(markEnergy > outOfBandEnergy * 20.0)
    }
}
