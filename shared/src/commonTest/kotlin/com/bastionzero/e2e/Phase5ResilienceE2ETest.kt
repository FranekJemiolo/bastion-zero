package com.bastionzero.e2e

import com.bastionzero.airgap.AirgapBundleReceiver
import com.bastionzero.airgap.AirgapBundleSync
import com.bastionzero.crdt.MapPin
import com.bastionzero.crdt.MapPinStore
import com.bastionzero.crdt.PinKind
import com.bastionzero.crypto.SecureEnclaveKeyManager
import com.bastionzero.mesh.LamportClock
import com.bastionzero.net.TransportTier
import com.bastionzero.net.UnifiedMeshRouter
import com.bastionzero.power.AutonomousPowerGovernor
import com.bastionzero.power.PowerBudgetMode
import com.bastionzero.proto.SurvivalPacket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Phase5ResilienceE2ETest {

    @Test
    fun testMultiTransportCascadeFailoverAndSosPreemptionE2E() {
        val router = UnifiedMeshRouter()

        // 1. Nominal BLE proximity communication
        val normalPacket = SurvivalPacket(
            packetId = 5001L,
            type = SurvivalPacket.PacketType.CHAT,
        )
        val decision1 = router.routePacket(normalPacket)
        assertEquals(listOf(TransportTier.BLE_MESH), decision1.selectedTransports)

        // 2. Cascade 1: BLE goes out of range -> Failover to LoRa Tactical
        router.updateLink(TransportTier.BLE_MESH, isAvailable = false)
        router.updateLink(TransportTier.LORA_TACTICAL, isAvailable = true, rssiDbm = -95)

        val pinPacket = SurvivalPacket(
            packetId = 5002L,
            type = SurvivalPacket.PacketType.PIN_ADD,
        )
        val decision2 = router.routePacket(pinPacket)
        assertEquals(listOf(TransportTier.LORA_TACTICAL), decision2.selectedTransports)

        // 3. Cascade 2: Severe RF electronic jamming -> LoRa down -> Fallback to Ultrasonic AFSK
        router.updateLink(TransportTier.LORA_TACTICAL, isAvailable = false)
        router.updateLink(TransportTier.ULTRASONIC_AFSK, isAvailable = true)

        val tacticalMsg = SurvivalPacket(
            packetId = 5003L,
            type = SurvivalPacket.PacketType.TEXT,
        )
        val decision3 = router.routePacket(tacticalMsg)
        assertEquals(listOf(TransportTier.ULTRASONIC_AFSK), decision3.selectedTransports)

        // 4. Emergency Preemption: Life-threatening trauma SOS
        // NTN Satellite terminal acquires constellation link
        router.updateLink(TransportTier.NTN_SATELLITE, isAvailable = true)

        val sosPacket = SurvivalPacket(
            packetId = 9999L,
            type = SurvivalPacket.PacketType.SOS_MEDICAL,
        )
        val sosDecision = router.routePacket(sosPacket)
        assertTrue(sosDecision.isEmergencyBroadcast)
        // Concurrently broadcasts on both active links (Ultrasonic + NTN Satellite)
        assertEquals(2, sosDecision.selectedTransports.size)
        assertTrue(sosDecision.selectedTransports.contains(TransportTier.ULTRASONIC_AFSK))
        assertTrue(sosDecision.selectedTransports.contains(TransportTier.NTN_SATELLITE))
        assertTrue(sosDecision.reason.contains("PREEMPTION"))
    }

    @Test
    fun testSolarHarvestEnduranceCouplingE2E() {
        val governor = AutonomousPowerGovernor(batteryCapacityMilliAmpHours = 5000)

        // 1. Survivor in dark sub-zero cave: Battery depleted to 14%, 0W solar
        val criticalState = governor.updateTelemetry(
            batteryPercent = 14,
            batteryTempCelsius = 5.0f,
            solarHarvestWatts = 0.0f,
        )

        assertEquals(PowerBudgetMode.SURVIVAL_CRITICAL, criticalState.mode)
        assertEquals(10, criticalState.throttleProfile.cameraFps)
        assertEquals(20, criticalState.throttleProfile.imuSamplingHz)
        assertEquals(2, criticalState.throttleProfile.bleScanDutyCyclePercent)
        assertEquals(10, criticalState.throttleProfile.oledBrightnessPercent)
        assertTrue(criticalState.throttleProfile.backgroundProcessingPaused)

        // 2. Survivor climbs to mountain ridge and connects 10W folding solar panel
        // Real-time solar harvest peaks at 4.2W
        val solarHarvestState = governor.updateTelemetry(
            batteryPercent = 55,
            batteryTempCelsius = 22.0f,
            solarHarvestWatts = 4.2f,
        )

        assertEquals(PowerBudgetMode.HARVEST_SURPLUS, solarHarvestState.mode)
        assertEquals(30, solarHarvestState.throttleProfile.cameraFps)
        assertEquals(100, solarHarvestState.throttleProfile.imuSamplingHz)
        assertEquals(50, solarHarvestState.throttleProfile.bleScanDutyCyclePercent)
        assertEquals(false, solarHarvestState.throttleProfile.backgroundProcessingPaused)
        assertTrue(solarHarvestState.estimatedRuntimeHours > 40.0f)
    }

    @Test
    fun testAirGappedOpticalQrSyncRoundtripE2E() {
        // Node Alpha: Create local hazard pins under strict RF silence (OPSEC)
        val clockA = LamportClock()
        val pinStoreA = MapPinStore("Node_Alpha", clockA)
        pinStoreA.add(
            MapPin(
                id = "pin-ammo-dump-01",
                kind = PinKind.RESOURCE,
                label = "CACHED MEDICAL SUPPLIES & WATER",
                latE6 = 50123456,
                lonE6 = 21987654,
            )
        )
        pinStoreA.add(
            MapPin(
                id = "pin-minefield-02",
                kind = PinKind.HAZARD,
                label = "UNEXPLODED ORDNANCE DANGER AREA",
                latE6 = 50133456,
                lonE6 = 21997654,
            )
        )

        val alphaPins = pinStoreA.pins()
        assertEquals(2, alphaPins.size)

        // Serialize pins to text payload
        val bundlePayload = alphaPins.joinToString(";") { "${it.id}|${it.kind.name}|${it.label}|${it.latE6}|${it.lonE6}" }
            .encodeToByteArray()

        // Seal with Enclave before airgap transfer
        val enclaveA = SecureEnclaveKeyManager()
        val sealedBundle = enclaveA.sealLocalData(bundlePayload)

        // Slice into optical QR stream
        val sync = AirgapBundleSync()
        val frames = sync.encodeBundle("bundle-tactical-01", sealedBundle, maxChunkBytes = 24)
        assertTrue(frames.size >= 2)

        // Node Bravo: Ingests frames out-of-order via camera
        val receiverB = AirgapBundleReceiver(sync)
        val scrambledFrames = frames.shuffled()

        var status: com.bastionzero.airgap.IngestStatus? = null
        for (f in scrambledFrames) {
            status = receiverB.ingestFrame(f)
        }

        assertNotNull(status)
        assertTrue(status.isComplete)
        assertNotNull(status.assembledData)

        // Unseal bundle using Node Alpha's key
        val unsealedPayload = enclaveA.unsealLocalData(status.assembledData!!)
        assertNotNull(unsealedPayload)

        // Parse and import pins into Node Bravo's MapPinStore
        val clockB = LamportClock()
        val pinStoreB = MapPinStore("Node_Bravo", clockB)

        val recoveredString = unsealedPayload.decodeToString()
        val pinTokens = recoveredString.split(";")
        for (token in pinTokens) {
            val parts = token.split("|")
            pinStoreB.add(
                MapPin(
                    id = parts[0],
                    kind = PinKind.valueOf(parts[1]),
                    label = parts[2],
                    latE6 = parts[3].toInt(),
                    lonE6 = parts[4].toInt(),
                )
            )
        }

        val bravoPins = pinStoreB.pins()
        assertEquals(2, bravoPins.size)
        assertEquals("CACHED MEDICAL SUPPLIES & WATER", bravoPins[0].label)
        assertEquals("UNEXPLODED ORDNANCE DANGER AREA", bravoPins[1].label)
    }
}
