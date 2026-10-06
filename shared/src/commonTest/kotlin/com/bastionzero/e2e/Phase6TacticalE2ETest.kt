package com.bastionzero.e2e

import com.bastionzero.acoustic.AcousticThreatType
import com.bastionzero.acoustic.AcousticTriangulationEngine
import com.bastionzero.hal.SarGhostTransponder
import com.bastionzero.nav.CelestialCompassEngine
import com.bastionzero.optical.SpatialPoint3D
import com.bastionzero.optical.WaterClarityCategory
import com.bastionzero.optical.WaterTurbidityAnalyzer
import com.bastionzero.optical.ZeroLightSpatialMapper
import com.bastionzero.tactical.PerimeterDefenseCoordinator
import com.bastionzero.tactical.PerimeterSensorEvent
import com.bastionzero.tactical.PerimeterThreatLevel
import com.bastionzero.tactical.TripwireNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Phase6TacticalE2ETest {

    @Test
    fun testScenario1_SubterraneanDarkCaveEscapeAndWaterTriage() {
        val mapper = ZeroLightSpatialMapper()
        val turbidityAnalyzer = WaterTurbidityAnalyzer()

        // 1. Synthesize cave point cloud: flat floor with boulder obstacle and pitfall
        val cavePoints = mutableListOf<SpatialPoint3D>()
        for (x in -5..5) {
            for (y in 0..15) {
                cavePoints.add(SpatialPoint3D(x * 0.3f, y * 0.4f, 0.0f))
            }
        }

        // Add boulder obstacle directly in forward path (X ~ 0, Y ~ 2.0m, Z ~ 0.8m)
        for (z in 3..10) {
            cavePoints.add(SpatialPoint3D(-0.2f, 2.0f, z * 0.1f))
            cavePoints.add(SpatialPoint3D(0.2f, 2.0f, z * 0.1f))
            cavePoints.add(SpatialPoint3D(0.0f, 2.1f, z * 0.1f))
        }

        // Add pitfall ledge drop-off at Y = 5.0m
        cavePoints.add(SpatialPoint3D(0.0f, 5.0f, -1.0f))
        cavePoints.add(SpatialPoint3D(0.3f, 5.2f, -1.2f))

        val topology = mapper.mapTopology(cavePoints)

        assertTrue(topology.obstacles.isNotEmpty(), "Boulder obstacle must be segmented")
        assertTrue(topology.corridor.forwardClearanceMeters < 3.0f, "Boulder constricts forward path")
        assertTrue(topology.corridor.hasDropoffPitfall, "Cave pitfall must trigger alarm")
        assertTrue(topology.wireframeLines.isNotEmpty(), "Wireframe lines must be generated for OLED render")

        // 2. Evaluate scavenged water sample from cave stream
        // Transmitted 920 lux out of 1000 lux (slight silt)
        val waterReport = turbidityAnalyzer.analyze(luxTransmitted = 920f, luxReference = 1000f, pathLengthCm = 5.0f)

        assertEquals(WaterClarityCategory.SLIGHTLY_TURBID, waterReport.category)
        assertFalse(waterReport.isUvSterilizerEffective, "UV alone is unsafe with particulate haze")
        assertEquals(2, waterReport.requiredBoilTimeMinutes)
        assertTrue(waterReport.fieldActionNotes.contains("Hollow Fiber filter"))
    }

    @Test
    fun testScenario2_HostileDroneAcousticVectorAndCelestialUnjammableNav() {
        val acousticEngine = AcousticTriangulationEngine(sampleRateHz = 48000)
        val celestialEngine = CelestialCompassEngine()

        // 1. Acoustic rotor threat detection
        val mic0 = FloatArray(600) { 0.4f }
        val mic1 = FloatArray(600) { 0.4f }
        val acousticResult = acousticEngine.triangulate(
            channelBuffers = listOf(mic0, mic1),
            ambientTempCelsius = 12.0f,
            dominantFrequencyHz = 42.0f, // 42 Hz drone rotor blade pass frequency
            riseTimeMs = 20.0f
        )

        assertEquals(AcousticThreatType.ROTOR_BLADE_FREQUENCY, acousticResult.threatType)

        // 2. Infallible Celestial Navigation during GNSS jamming & magnetic distortion
        // Epoch at solar noon in Bieszczady Mountains (49.2° N, 22.5° E)
        val epochMillis = 1773994800000L
        val navFix = celestialEngine.solveHeadingWithShadow(
            epochMillis = epochMillis,
            latitudeDeg = 49.2,
            longitudeDeg = 22.5,
            shadowRelativeBearingDeg = 0.0f,
            measuredMagneticHeadingDeg = 240.0f // Magnetometer heavily skewed by metal rebar
        )

        assertTrue(navFix.ephemeris.isSunAboveHorizon)
        assertTrue(navFix.magneticAnomalyDetected, "Magnetic compass distortion must be detected")
        assertTrue(navFix.magneticDeviationDeg > 30f)
        assertTrue(navFix.guidanceNote.contains("CRITICAL: Magnetic compass deviates"))
    }

    @Test
    fun testScenario3_StrandedMountainRescueAndCampPerimeterDefense() {
        val transponder = SarGhostTransponder(burstCooldownMillis = 30_000L)
        val perimeterCoordinator = PerimeterDefenseCoordinator(alertCorrelationWindowMs = 20_000L)

        // 1. Mountain survivor SAR ghost transponder burst
        transponder.setArmed(true)
        val burstPacket = transponder.triggerBurst(
            currentTimeMillis = 5000L,
            batteryPercent = 45,
            batteryTempCelsius = 22.0f,
            lat = 49.25,
            lon = 22.55,
            altMeters = 1150f,
            traumaCode = "PATIENT_IMPACT_12G_FEMUR_FRACTURE"
        )

        assertNotNull(burstPacket)
        assertEquals(1L, burstPacket.burstId)
        assertEquals(23.0f, burstPacket.estimatedRfPowerDbm)
        assertEquals("PATIENT_IMPACT_12G_FEMUR_FRACTURE", burstPacket.traumaTriageCode)

        // 2. Base camp perimeter security network
        perimeterCoordinator.registerNode(TripwireNode("post_alpha", 80f, 30f, 5000L, isArmed = true))
        perimeterCoordinator.registerNode(TripwireNode("post_bravo", 90f, 150f, 5000L, isArmed = true))

        // First event: Post Alpha detects a gunshot
        perimeterCoordinator.ingestEvent(
            PerimeterSensorEvent.AcousticSpike(
                nodeId = "post_alpha",
                timestampMillis = 5500L,
                threatType = AcousticThreatType.GUNSHOT_SUPERSONIC,
                peakDecibels = 108f,
                soundAzimuthDeg = 32f
            )
        )

        val breachReport = perimeterCoordinator.evaluatePerimeter(6000L)
        assertEquals(PerimeterThreatLevel.BREACH_RED, breachReport.threatLevel)
        assertTrue(breachReport.threatDescription.contains("ACOUSTIC BREACH"))
        assertEquals(32f, breachReport.primaryThreatSectorDeg)
    }
}
