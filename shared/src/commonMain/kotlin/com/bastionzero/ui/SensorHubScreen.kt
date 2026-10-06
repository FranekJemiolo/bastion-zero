package com.bastionzero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bastionzero.AppEnvironment
import com.bastionzero.acoustic.AcousticNeuralClassifier
import com.bastionzero.acoustic.NeuralClassificationResult
import com.bastionzero.airgap.AirgapApkBeacon
import com.bastionzero.airgap.ApkDistributionSession
import com.bastionzero.hal.AcousticThreatEvent
import com.bastionzero.hal.GnssRawStream
import com.bastionzero.hal.MotionSample
import com.bastionzero.hal.ThreatSignature
import com.bastionzero.hal.TiltSeverity
import com.bastionzero.hal.UsbSerialConnection
import com.bastionzero.haptics.HapticChord
import com.bastionzero.haptics.HapticPlayer
import com.bastionzero.hub.RadiationHazardLevel
import com.bastionzero.hub.SdrSignalTriangulation
import com.bastionzero.hub.SolarInsolationOptimizer
import com.bastionzero.hub.TacticalHubBridge
import com.bastionzero.medical.OpticalVitalsMonitor
import com.bastionzero.medical.OpticalVitalsSample
import com.bastionzero.net.MeshtasticMeshPacket
import com.bastionzero.net.MeshtasticProtocolBridge
import com.bastionzero.net.NtnSatelliteMeshBridge
import com.bastionzero.net.SlottedRebroadcastSuppression
import com.bastionzero.net.SlottedRelayDecision
import com.bastionzero.power.PowerGovernor
import com.bastionzero.rag.EdgeRagSemanticRouter
import com.bastionzero.rag.SemanticTriageResponse
import com.bastionzero.thermal.SurfaceMaterial
import com.bastionzero.thermal.ThermalImagingEngine
import com.bastionzero.thermal.ThermalReading
import com.bastionzero.trauma.KinematicTraumaLogger
import com.bastionzero.trauma.TraumaSeverity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun SensorHubScreen(
    env: AppEnvironment,
    modifier: Modifier = Modifier,
) {
    SensorHubContent(
        power = env.power,
        haptics = env.haptics,
        deadReckoning = env.deadReckoning,
        tiltMonitor = env.tiltMonitor,
        deadMansSwitch = env.deadMansSwitch,
        acousticEdgeAI = env.acousticEdgeAI,
        gnssSpoofing = env.gnssSpoofing,
        traumaLogger = env.traumaLogger,
        thermalEngine = env.thermalEngine,
        tacticalHub = env.tacticalHub,
        vitalsMonitor = env.vitalsMonitor,
        solarOptimizer = env.solarOptimizer,
        sdrTriangulation = env.sdrTriangulation,
        satelliteBridge = env.satelliteBridge,
        neuralClassifier = env.neuralClassifier,
        semanticRouter = env.semanticRouter,
        apkBeacon = env.apkBeacon,
        meshtasticBridge = env.meshtasticBridge,
        slottedSuppression = env.slottedSuppression,
        usbSerialDriver = env.usbSerialHostDriver,
        gnssRawIngestor = env.gnssRawIngestor,
        modifier = modifier,
    )
}

/** Backward compatible overload */
@Composable
fun SensorHubScreen(
    power: PowerGovernor,
    haptics: HapticPlayer,
    modifier: Modifier = Modifier,
) {
    SensorHubContent(
        power = power,
        haptics = haptics,
        modifier = modifier,
    )
}

@Composable
fun SensorHubContent(
    power: PowerGovernor,
    haptics: HapticPlayer,
    deadReckoning: com.bastionzero.hal.InertialDeadReckoning = remember { com.bastionzero.hal.InertialDeadReckoning() },
    tiltMonitor: com.bastionzero.hal.StructuralTiltMonitor = remember { com.bastionzero.hal.StructuralTiltMonitor() },
    deadMansSwitch: com.bastionzero.hal.DeadMansSwitch = remember { com.bastionzero.hal.DeadMansSwitch() },
    acousticEdgeAI: com.bastionzero.hal.AcousticEdgeAI = remember { com.bastionzero.hal.AcousticEdgeAI() },
    gnssSpoofing: com.bastionzero.hal.GnssSpoofingDetector = remember { com.bastionzero.hal.GnssSpoofingDetector() },
    traumaLogger: com.bastionzero.trauma.KinematicTraumaLogger = remember { com.bastionzero.trauma.KinematicTraumaLogger() },
    thermalEngine: com.bastionzero.thermal.ThermalImagingEngine = remember { com.bastionzero.thermal.ThermalImagingEngine() },
    tacticalHub: com.bastionzero.hub.TacticalHubBridge = remember { com.bastionzero.hub.TacticalHubBridge() },
    vitalsMonitor: com.bastionzero.medical.OpticalVitalsMonitor = remember { com.bastionzero.medical.OpticalVitalsMonitor() },
    solarOptimizer: com.bastionzero.hub.SolarInsolationOptimizer = remember { com.bastionzero.hub.SolarInsolationOptimizer() },
    sdrTriangulation: com.bastionzero.hub.SdrSignalTriangulation = remember { com.bastionzero.hub.SdrSignalTriangulation() },
    satelliteBridge: com.bastionzero.net.NtnSatelliteMeshBridge = remember { com.bastionzero.net.NtnSatelliteMeshBridge() },
    neuralClassifier: AcousticNeuralClassifier = remember { AcousticNeuralClassifier() },
    semanticRouter: EdgeRagSemanticRouter = remember { EdgeRagSemanticRouter() },
    apkBeacon: AirgapApkBeacon = remember { AirgapApkBeacon() },
    meshtasticBridge: MeshtasticProtocolBridge = remember { MeshtasticProtocolBridge() },
    slottedSuppression: SlottedRebroadcastSuppression = remember { SlottedRebroadcastSuppression() },
    usbSerialDriver: UsbSerialConnection? = null,
    gnssRawIngestor: GnssRawStream? = null,
    modifier: Modifier = Modifier,
) {
    val inputs by power.inputs.collectAsState()
    val policy by power.policy.collectAsState()

    val drState by deadReckoning.state.collectAsState()
    val tiltStatus by tiltMonitor.status.collectAsState()
    val dmsState by deadMansSwitch.state.collectAsState()
    val gnssReport by gnssSpoofing.integrityReport.collectAsState()
    val currentSpl by acousticEdgeAI.currentSpl.collectAsState()
    val traumaState by traumaLogger.state.collectAsState()
    val hubState by tacticalHub.state.collectAsState()
    val vitalsState by vitalsMonitor.state.collectAsState()
    val satelliteState by satelliteBridge.state.collectAsState()

    var selectedThermalMaterial by remember { mutableStateOf(SurfaceMaterial.HUMAN_SKIN) }
    var currentThermalReading by remember {
        mutableStateOf(thermalEngine.calculateTrueTemperature(36.5f, SurfaceMaterial.HUMAN_SKIN))
    }

    var solarCoords by remember {
        mutableStateOf(solarOptimizer.calculateSolarPosition(52.2f, 172, 12.0f))
    }
    var powerRouting by remember {
        mutableStateOf(solarOptimizer.triagePower(21.0f))
    }
    var sdrTarget by remember { mutableStateOf<com.bastionzero.hub.TriangulationTarget?>(null) }

    val isUsbConnected by (usbSerialDriver?.isConnected ?: remember { MutableStateFlow(false) }).collectAsState()
    val usbDevice by (usbSerialDriver?.connectedDevice ?: remember { MutableStateFlow(null) }).collectAsState()
    val isGnssListening by (gnssRawIngestor?.isListening ?: remember { MutableStateFlow(false) }).collectAsState()

    var neuralResult by remember { mutableStateOf<NeuralClassificationResult?>(null) }
    var triageInput by remember { mutableStateOf("Massive bright red blood spurting from upper thigh") }
    var triageResult by remember { mutableStateOf<SemanticTriageResponse?>(null) }
    var apkSession by remember { mutableStateOf<ApkDistributionSession?>(null) }
    var meshtasticSamplePacket by remember { mutableStateOf<MeshtasticMeshPacket?>(null) }
    var slottedDecision by remember { mutableStateOf<SlottedRelayDecision?>(null) }

    var latestThreat by remember { mutableStateOf<AcousticThreatEvent?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(acousticEdgeAI) {
        acousticEdgeAI.threatEvents.collectLatest { event ->
            latestThreat = event
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("SENSOR HUB & EDGE HAL", style = MaterialTheme.typography.headlineMedium, color = BastionColors.Red)

        // 1. PowerOS Resource Governor
        CardBox(title = "POWEROS RESOURCE GOVERNOR") {
            Text("Tier: ${policy.tier}", style = MaterialTheme.typography.titleMedium, color = BastionColors.Red)
            Text("Battery ${inputs.batteryPercent}%${if (inputs.isCharging) " (charging)" else ""}")
            Text("Motion: ${if (inputs.isMoving) "MOVING" else "STATIONARY"}")
            Text("UI: ${policy.uiRefreshHz} Hz · Brightness: ${(policy.screenBrightness * 100).toInt()}%", color = BastionColors.DimRed)
            Text("GPS polling: ${policy.gpsIntervalMs / 1000}s · Sensors: ${policy.sensorIntervalMs / 1000}s", color = BastionColors.DimRed)
            Text("Mesh duty: ${policy.meshScanDutyPercent}% · WakeLock: ${if (policy.holdWakeLock) "HELD" else "RELEASED"}", color = BastionColors.DimRed)
        }

        // 2. Inertial Dead Reckoning
        CardBox(title = "INERTIAL DEAD RECKONING (PDR)") {
            Text("Steps: ${drState.stepCount} · Distance: ${drState.totalDistanceMeters.formatDecimals(1)} m", color = BastionColors.Red)
            Text("Heading: ${drState.headingDegrees.formatDecimals(1)}° · Altitude: ${drState.altitudeMeters.formatDecimals(1)} m")
            Text("DR Position: ${drState.lat.formatDecimals(5)}, ${drState.lon.formatDecimals(5)}", color = BastionColors.DimRed)
            Text("Breadcrumb Count: ${drState.breadcrumbs.size}", color = BastionColors.DimRed)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        // Simulate a forward footstep
                        val now = drState.lastUpdateTimestampNs + 300_000_000L
                        deadReckoning.updateMotion(
                            MotionSample(
                                timestampNs = now,
                                ax = 0f, ay = 0f, az = 12.0f,
                                gx = 0f, gy = 0f, gz = 0f,
                                mx = 20f, my = 5f, mz = -40f,
                            )
                        )
                        deadReckoning.updateMotion(
                            MotionSample(
                                timestampNs = now + 50_000_000L,
                                ax = 0f, ay = 0f, az = 9.8f,
                                gx = 0f, gy = 0f, gz = 0f,
                                mx = 20f, my = 5f, mz = -40f,
                            )
                        )
                    }
                ) { Text("SIMULATE STEP", color = BastionColors.Red) }

                OutlinedButton(
                    onClick = { deadReckoning.resetAnchor(52.2297, 21.0122, 110f, 0L) }
                ) { Text("RESET GPS ANCHOR", color = BastionColors.DimRed) }
            }
        }

        // 3. Structural Tilt Monitor
        CardBox(title = "STRUCTURAL TILT MONITOR") {
            val statusColor = when (tiltStatus.severity) {
                TiltSeverity.CRITICAL -> BastionColors.Red
                TiltSeverity.WARNING -> BastionColors.Ember
                TiltSeverity.NORMAL -> BastionColors.DimRed
            }
            Text("Status: ${tiltStatus.severity}", color = statusColor, style = MaterialTheme.typography.titleMedium)
            Text("Calibrated: ${if (tiltStatus.isCalibrated) "YES" else "AWAITING ZERO"}")
            Text("Tilt Deviation: ${tiltStatus.temperatureCompensatedDeltaDeg.formatDecimals(3)}° (Raw: ${tiltStatus.currentTiltDeg.formatDecimals(3)}°)")
            Text("Thermistor: ${tiltStatus.currentTempCelsius.formatDecimals(1)}°C (Ref: ${tiltStatus.baselineTempCelsius.formatDecimals(1)}°C)", color = BastionColors.DimRed)
            if (tiltStatus.isAlarmTriggered) {
                Text("⚠️ WARNING: FOUNDATION SHIFT EXCEEDS 0.5° THRESHOLD", color = BastionColors.Red)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        tiltMonitor.startCalibration()
                        for (i in 0 until 40) {
                            tiltMonitor.processSample(0f, 0f, 9.80665f, 21.5f)
                        }
                    }
                ) { Text("ZERO BASELINE", color = BastionColors.Red) }

                OutlinedButton(
                    onClick = {
                        // Simulate a 0.6 degree foundation tilt
                        tiltMonitor.processSample(0.11f, 0f, 9.80f, 21.5f)
                    }
                ) { Text("SIMULATE SHIFT", color = BastionColors.DimRed) }
            }
        }

        // 4. Dead Man's Switch (5% Battery)
        CardBox(title = "DEAD MAN'S SWITCH (5% VOLTAGE GATE)") {
            Text("Status: ${dmsState.status}", color = if (dmsState.isBeaconTransmitting) BastionColors.Red else BastionColors.DimRed)
            Text("Pre-allocated RAM: 50 KB · Payload: ${dmsState.payloadSizeBytes} bytes")
            Text("BLE Beacon Locked: ${if (dmsState.isBeaconTransmitting) "TRANSMITTING" else "STANDBY"}")
            Text("HALT Procedure: ${if (dmsState.haltExecuted) "EXECUTED" else "READY"}")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { deadMansSwitch.triggerEmergencySequence(1_000_000_000L, batteryPercent = 5) }
                ) { Text("TEST 5% TRIGGER", color = BastionColors.Red) }

                OutlinedButton(
                    onClick = { deadMansSwitch.arm() }
                ) { Text("RE-ARM", color = BastionColors.DimRed) }
            }
        }

        // 5. Acoustic Edge Overwatch
        CardBox(title = "ACOUSTIC OVERWATCH (70 dB DSP GATE)") {
            Text("Live SPL: ${currentSpl.formatDecimals(1)} dB SPL (Gate: > 70 dB)", color = if (currentSpl >= 70f) BastionColors.Red else BastionColors.DimRed)
            val threat = latestThreat
            if (threat != null) {
                Text("Detected: ${threat.signature} (${(threat.confidence * 100).toInt()}%)", color = BastionColors.Red)
                Text("Dominant Frequency: ${threat.dominantFrequencyHz.formatDecimals(0)} Hz", color = BastionColors.DimRed)
            } else {
                Text("Acoustic Signature: QUIET / AMBIENT MONITORING", color = BastionColors.DimRed)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        // Simulate an 85dB drone rotor harmonic burst (1000 Hz)
                        val sampleRate = 16000
                        val samples = FloatArray(sampleRate * 2) { i ->
                            (0.35 * kotlin.math.sin(2.0 * kotlin.math.PI * 1000.0 * i / sampleRate)).toFloat()
                        }
                        acousticEdgeAI.processAudioChunk(samples, timestampNs = 1_000_000_000L)
                    }
                ) { Text("SIMULATE DRONE BURST", color = BastionColors.Red) }
            }
        }

        // 6. GNSS Spoofing Defense
        CardBox(title = "GNSS EW & ANTI-SPOOFING DEFENSE") {
            Text("Integrity: ${if (gnssReport.isSpoofed) "SPOOFED (FALLBACK ACTIVE)" else "AUTHENTIC"}",
                color = if (gnssReport.isSpoofed) BastionColors.Red else BastionColors.DimRed)
            Text("AGC Level: ${gnssReport.agcLevelDb.formatDecimals(1)} dB · Baseline: ${gnssReport.baselineAgcDb.formatDecimals(1)} dB")
            Text("Clock Drift: ${gnssReport.clockDriftNanosPerSec} ns/s · Satellites: ${gnssReport.satelliteCount}")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        // Simulate high-power spoofer AGC spike
                        gnssSpoofing.processGnssMeasurement(
                            agcLevelDb = 25.0f,
                            clockDriftNanosPerSec = 150_000L,
                            satelliteCount = 12,
                            timestampNs = 1_000_000_000L,
                        )
                    }
                ) { Text("SIMULATE SPOOFER SPIKE", color = BastionColors.Red) }

                OutlinedButton(
                    onClick = { gnssSpoofing.resetBaseline() }
                ) { Text("RESET GNSS", color = BastionColors.DimRed) }
            }
        }

        // 7. Tactical Haptic Chords
        CardBox(title = "STEALTH HAPTIC CHORDS (PWM)") {
            HapticChord.entries.forEach { chord ->
                OutlinedButton(
                    onClick = { haptics.play(chord) },
                    modifier = Modifier.padding(vertical = 2.dp),
                ) { Text(chord.name.replace('_', ' '), color = BastionColors.Red) }
            }
            Text("Hardware PWM Waveform for zero-light tactile signaling.", color = BastionColors.DimRed)
        }

        // 8. Kinematic Trauma Black-Box
        CardBox(title = "KINEMATIC TRAUMA BLACK-BOX (HIGH-G & FALL)") {
            val severityColor = when (traumaState.activeSeverity) {
                TraumaSeverity.CATASTROPHIC, TraumaSeverity.SEVERE_TRAUMA -> BastionColors.Red
                TraumaSeverity.MODERATE_IMPACT -> BastionColors.Ember
                TraumaSeverity.NORMAL -> BastionColors.DimRed
            }
            Text("Status: ${traumaState.activeSeverity}", color = severityColor, style = MaterialTheme.typography.titleMedium)
            Text("Live Acceleration: ${traumaState.currentAccelerationG.formatDecimals(1)} G · Peak: ${traumaState.peakRecordedG.formatDecimals(1)} G")
            Text("Free-Fall State: ${if (traumaState.isInFreeFall) "FALLING (< 0.25G)" else "GROUNDED / NORMAL"}")
            Text("RAM Buffer: ${traumaState.circularBufferCount} telemetry frames held in memory")

            val event = traumaState.lastTraumaEvent
            if (event != null) {
                Text("Lock-Screen Triage Alert:", color = BastionColors.Red, style = MaterialTheme.typography.titleSmall)
                Text(event.lockScreenTriageAlert, color = BastionColors.Red)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        // Simulate 1.0s freefall followed by 14.5G impact
                        val t0 = 1_000_000_000L
                        traumaLogger.processSample(t0, 0.05f, 0.05f, 0.05f)
                        traumaLogger.processSample(t0 + 1_000_000_000L, 2f, 2f, 14.5f, gxDegPerSec = 360f)
                    }
                ) { Text("SIMULATE 14G FALL", color = BastionColors.Red) }

                OutlinedButton(
                    onClick = { traumaLogger.reset() }
                ) { Text("RESET BLACK-BOX", color = BastionColors.DimRed) }
            }
        }

        // 9. LWIR Thermal & Emissivity Engine
        CardBox(title = "LWIR THERMAL & EMISSIVITY CORRECTION") {
            Text("Target Material: ${selectedThermalMaterial.displayName} (ε = ${selectedThermalMaterial.emissivity})")
            Text("Apparent Temp: ${currentThermalReading.apparentTempCelsius.formatDecimals(1)}°C · Corrected: ${currentThermalReading.correctedTempCelsius.formatDecimals(1)}°C",
                color = if (currentThermalReading.isScaldHazard) BastionColors.Red else BastionColors.DimRed)

            val warning = currentThermalReading.safetyWarning
            if (warning != null) {
                Text("⚠️ $warning", color = BastionColors.Red)
            }
            val action = currentThermalReading.recommendedFieldAction
            if (action != null) {
                Text("Action: $action", color = BastionColors.Ember)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = {
                        selectedThermalMaterial = SurfaceMaterial.HUMAN_SKIN
                        currentThermalReading = thermalEngine.calculateTrueTemperature(36.5f, SurfaceMaterial.HUMAN_SKIN)
                    }
                ) { Text("SKIN", color = BastionColors.Red) }

                OutlinedButton(
                    onClick = {
                        selectedThermalMaterial = SurfaceMaterial.POLISHED_METAL
                        currentThermalReading = thermalEngine.calculateTrueTemperature(22.0f, SurfaceMaterial.POLISHED_METAL)
                    }
                ) { Text("SHINY METAL", color = BastionColors.Ember) }

                OutlinedButton(
                    onClick = {
                        selectedThermalMaterial = SurfaceMaterial.MYLAR_SPACE_BLANKET
                        currentThermalReading = thermalEngine.calculateTrueTemperature(5.0f, SurfaceMaterial.MYLAR_SPACE_BLANKET)
                    }
                ) { Text("MYLAR", color = BastionColors.Red) }

                OutlinedButton(
                    onClick = {
                        selectedThermalMaterial = SurfaceMaterial.TARGET_PATCH_TAPE_SOOT
                        currentThermalReading = thermalEngine.calculateTrueTemperature(85.0f, SurfaceMaterial.TARGET_PATCH_TAPE_SOOT)
                    }
                ) { Text("PATCH", color = BastionColors.DimRed) }
            }
        }

        // 10. Tactical Hub & LoRa Bridge
        CardBox(title = "TACTICAL HUB (LORA MESH & CBRN DOSIMETER)") {
            Text("LoRa Link: ${if (hubState.isLoRaConnected) "ACTIVE (${hubState.loraFrequencyMhz} MHz)" else "DISCONNECTED"}",
                color = if (hubState.isLoRaConnected) BastionColors.Red else BastionColors.DimRed)
            Text("LoRa Packets: TX ${hubState.packetsTransmittedLoRa} · RX ${hubState.packetsReceivedLoRa}")

            val rad = hubState.lastRadiationTelemetry
            if (rad != null) {
                val radColor = if (rad.isExclusionZoneTriggered) BastionColors.Red else BastionColors.DimRed
                Text("Radiation Rate: ${rad.currentDoseRateMicroSvPerHour.formatDecimals(2)} μSv/h (${rad.hazardLevel})", color = radColor)
                Text("Accumulated Dose: ${rad.accumulatedDoseMicroSv.formatDecimals(1)} μSv · Safe Stay Time: ${rad.safeStayTimeHoursRemaining.formatDecimals(1)} hrs")
                if (rad.isExclusionZoneTriggered) {
                    Text("☢️ ACUTE RADIATION EXCLUSION ZONE TRIGGERED", color = BastionColors.Red)
                }
            } else {
                Text("Dosimeter Status: SENSOR STANDBY", color = BastionColors.DimRed)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        tacticalHub.setLoRaConnected(true, 915.0f)
                        val samplePacket = "BASTION_MESH_PING_SOS".encodeToByteArray()
                        val frame = tacticalHub.frameLoRaPacket(samplePacket)
                        tacticalHub.unframeLoRaPacket(frame)
                    }
                ) { Text("TEST LORA FRAME", color = BastionColors.Red) }

                OutlinedButton(
                    onClick = {
                        tacticalHub.processRadiationSample(125.0f, 1_000_000_000L)
                    }
                ) { Text("SIMULATE 125 μSv/h", color = BastionColors.Red) }

                OutlinedButton(
                    onClick = { tacticalHub.resetDose() }
                ) { Text("RESET DOSE", color = BastionColors.DimRed) }
            }
        }

        // 11. Optical Vitals & Photoplethysmography
        CardBox(title = "OPTICAL VITALS & PPG (CAMERA + FLASH)") {
            val reading = vitalsState.currentReading
            if (reading != null) {
                Text("Pulse Rate: ${reading.heartRateBpm} BPM · SpO2: ${reading.spo2Percent}%",
                    color = if (reading.status == com.bastionzero.medical.VitalsStatus.NORMAL) BastionColors.Red else BastionColors.Ember,
                    style = MaterialTheme.typography.titleMedium)
                Text("Status: ${reading.status} (${(reading.confidence * 100).toInt()}% confidence)")
                Text(reading.diagnosticSummary, color = BastionColors.DimRed)
            } else {
                Text("Vitals Monitor: ${if (vitalsState.isMonitoring) "COLLECTING OPTICAL PULSE..." else "STANDBY"}", color = BastionColors.DimRed)
            }
            Text("Processed Frames: ${vitalsState.samplesProcessed} · Signal Quality: ${vitalsState.signalQualityPercent}%", color = BastionColors.DimRed)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        vitalsMonitor.start()
                        // Feed simulated 75 BPM pulse cycle
                        val cycleNs = 800_000_000L
                        var t = 1_000_000_000L
                        for (i in 0..120) {
                            val prog = (i * 33_333_333.0 / cycleNs) * 2.0 * kotlin.math.PI
                            vitalsMonitor.processFrame(
                                OpticalVitalsSample(
                                    timestampNs = t,
                                    redIntensity = (0.7 + 0.15 * kotlin.math.sin(prog)).toFloat(),
                                    infraredOrGreenIntensity = (0.8 + 0.10 * kotlin.math.sin(prog)).toFloat(),
                                )
                            )
                            t += 33_333_333L
                        }
                    }
                ) { Text("SIMULATE 75 BPM", color = BastionColors.Red) }

                OutlinedButton(
                    onClick = { vitalsMonitor.reset() }
                ) { Text("RESET PPG", color = BastionColors.DimRed) }
            }
        }

        // 12. AR Solar Insolation & Triage Power
        CardBox(title = "AR SOLAR INSOLATION & POWER TRIAGE") {
            Text("Solar Elevation: ${solarCoords.elevationDegrees.formatDecimals(1)}° · Azimuth: ${solarCoords.azimuthDegrees.formatDecimals(1)}°",
                color = if (solarCoords.isDaylight) BastionColors.Red else BastionColors.DimRed)
            Text("Daylight: ${if (solarCoords.isDaylight) "ACTIVE DIRECT INSOLATION" else "NIGHT / HORIZON BELOW 0°"}")
            Text("Solar Input: ${powerRouting.incomingSolarWatts.formatDecimals(1)}W · Optimal Tilt: ${solarOptimizer.calculateOptimalTiltDegrees(52.2f, 172).formatDecimals(1)}°", color = BastionColors.DimRed)
            Text("Routing: ${powerRouting.primaryAllocatedWatts.formatDecimals(1)}W to ${powerRouting.primaryTargetDevice} (${powerRouting.primaryPercent}%) · ${powerRouting.secondaryAllocatedWatts.formatDecimals(1)}W to ${powerRouting.secondaryTargetDevice} (${powerRouting.secondaryPercent}%)", color = BastionColors.Ember)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        solarCoords = solarOptimizer.calculateSolarPosition(52.2f, 172, 12.0f)
                        powerRouting = solarOptimizer.triagePower(21.0f)
                    }
                ) { Text("NOON 21W", color = BastionColors.Red) }

                OutlinedButton(
                    onClick = {
                        solarCoords = solarOptimizer.calculateSolarPosition(52.2f, 172, 17.5f)
                        powerRouting = solarOptimizer.triagePower(8.5f)
                    }
                ) { Text("DUSK 8.5W", color = BastionColors.DimRed) }
            }
        }

        // 13. SDR RF Triangulation
        CardBox(title = "SDR RF SIGNAL TRIANGULATION (406 MHz / LORa)") {
            val target = sdrTarget
            if (target != null) {
                Text("Bearing: ${target.peakAzimuthDeg.formatDecimals(1)}° · Peak RSSI: ${target.peakRssiDbm.formatDecimals(1)} dBm",
                    color = BastionColors.Red, style = MaterialTheme.typography.titleMedium)
                Text("Target: ${target.targetType} (${(target.confidence * 100).toInt()}% confidence)")
                Text("Beamwidth: ±${target.beamwidthDeg.formatDecimals(0)}° directional lobe", color = BastionColors.DimRed)
            } else {
                Text("RF Emitter Bearing: SWEEP COMPASS TO TRIANGULATE", color = BastionColors.DimRed)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        sdrTriangulation.clear()
                        for (az in 0 until 360 step 15) {
                            val rssi = if (az in 105..135) -45.0f else -95.0f
                            sdrTriangulation.recordSample(az.toFloat(), rssi, 406.025f)
                        }
                        sdrTarget = sdrTriangulation.computeBearing()
                    }
                ) { Text("SWEEP 406 MHz BEACON", color = BastionColors.Red) }

                OutlinedButton(
                    onClick = {
                        sdrTriangulation.clear()
                        sdrTarget = null
                    }
                ) { Text("CLEAR SDR", color = BastionColors.DimRed) }
            }
        }

        // 14. NTN Direct-to-Cell Satellite Bridge
        CardBox(title = "NTN SATELLITE MESH UPLINK GATEWAY") {
            Text("Constellation: ${satelliteState.constellationName}")
            Text("Lock Status: ${satelliteState.status}",
                color = if (satelliteState.status == com.bastionzero.net.SatelliteLockStatus.UPLINK_SUCCESS) BastionColors.Red else BastionColors.DimRed)
            Text("Queued Mesh SOS Packets: ${satelliteState.queuedMeshSosPacketsCount} · Uplinked: ${satelliteState.packetsUplinkedTotal}", color = BastionColors.DimRed)
            Text("Satellite Position: Az ${satelliteState.currentSatelliteAzimuthDeg.formatDecimals(0)}° · El ${satelliteState.currentSatelliteElevationDeg.formatDecimals(0)}°", color = BastionColors.DimRed)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        satelliteBridge.enqueueMeshSosPacket("SOS_VICTIM_GPS_52_21".encodeToByteArray())
                        satelliteBridge.onSatelliteLockChanged(hasLock = true, elevationDeg = 38.0f, azimuthDeg = 160.0f)
                    }
                ) { Text("TRIGGER SATELLITE UPLINK", color = BastionColors.Red) }
            }
        }

        // 15. Physical USB-C OTG Host Driver & Peripherals
        CardBox(title = "USB-C OTG SERIAL HOST DRIVER & BUS") {
            Text("Hardware Link: ${if (isUsbConnected) "CONNECTED" else "NOT CONNECTED"}",
                color = if (isUsbConnected) BastionColors.Red else BastionColors.DimRed)
            if (usbDevice != null) {
                Text("Device: ${usbDevice!!.deviceName}")
                Text("Chipset: ${usbDevice!!.chipset} (VID: 0x${usbDevice!!.vendorId.toString(16).uppercase()} / PID: 0x${usbDevice!!.productId.toString(16).uppercase()})")
            } else {
                Text("Detected Hardware: NONE / VIRTUAL SIMULATOR", color = BastionColors.DimRed)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        usbSerialDriver?.open(115200)
                    }
                ) { Text("OPEN SERIAL 115200", color = BastionColors.Red) }
                OutlinedButton(
                    onClick = {
                        usbSerialDriver?.close()
                    }
                ) { Text("CLOSE BUS", color = BastionColors.DimRed) }
            }
        }

        // 16. Raw GNSS Measurement Ingestion & EW Defense
        CardBox(title = "RAW GNSS MEASUREMENT EW INGESTION") {
            Text("Stream Callback: ${if (isGnssListening) "INGESTING SATELLITE SIGNALS" else "IDLE"}",
                color = if (isGnssListening) BastionColors.Red else BastionColors.DimRed)
            Text("EW Spoof Status: ${if (gnssReport.isSpoofed) "SPOOF ATTACK DETECTED -> IMU FALLBACK" else "NOMINAL EPHEMERIS"}",
                color = if (gnssReport.isSpoofed) BastionColors.Red else BastionColors.DimRed)
            Text("Clock Drift: ${gnssReport.clockDriftNanosPerSec} ns/s · AGC Metric: ${gnssReport.agcLevelDb.formatDecimals(1)} dB", color = BastionColors.DimRed)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        gnssRawIngestor?.startListening()
                        for (i in 0..15) {
                            gnssSpoofing.processGnssMeasurement(-10.0f, 180L, 14, i * 1_000_000_000L)
                        }
                    }
                ) { Text("INGEST NOMINAL GNSS", color = BastionColors.Red) }
                OutlinedButton(
                    onClick = {
                        gnssSpoofing.processGnssMeasurement(16.0f, 600_000L, 14, 20_000_000_000L)
                    }
                ) { Text("SIMULATE +25dB EW SPOOF", color = BastionColors.Red) }
            }
        }

        // 17. Neural Mel-Spectrogram Acoustic Threat Classifier
        CardBox(title = "NEURAL ACOUSTIC MEL-SPECTROGRAM CLASSIFIER") {
            if (neuralResult != null) {
                Text("Detected Class: ${neuralResult!!.predictedClass.name}", color = BastionColors.Red, style = MaterialTheme.typography.titleMedium)
                Text("Confidence: ${(neuralResult!!.confidence * 100).toInt()}% · Dominant Mel Band: ${neuralResult!!.dominantMelBandHz.toInt()} Hz")
            } else {
                Text("Status: READY · 16 MEL FREQUENCY BINS", color = BastionColors.DimRed)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val melInput = FloatArray(16) { 0.1f }
                        for (i in 6..11) melInput[i] = 3.5f
                        neuralResult = neuralClassifier.classify(melInput)
                    }
                ) { Text("CLASSIFY GUNSHOT", color = BastionColors.Red) }
                OutlinedButton(
                    onClick = {
                        val melInput = FloatArray(16) { 0.05f }
                        melInput[0] = 3.2f; melInput[1] = 2.8f
                        neuralResult = neuralClassifier.classify(melInput)
                    }
                ) { Text("CLASSIFY DRONE", color = BastionColors.Red) }
            }
        }

        // 18. Edge RAG Natural-Language Field Triage (TCCC)
        CardBox(title = "SEMANTIC NATURAL-LANGUAGE FIELD TRIAGE (TCCC)") {
            Text("Distress Query: \"$triageInput\"", color = BastionColors.DimRed)
            if (triageResult != null) {
                Text("Urgency: ${triageResult!!.urgencyLevel.name}", color = BastionColors.Red, style = MaterialTheme.typography.titleMedium)
                Text("Headline: ${triageResult!!.triageHeadline}")
                triageResult!!.immediateActions.firstOrNull()?.let { act ->
                    Text("Intervention: ${act.title}", color = BastionColors.Red)
                    Text("Procedure: ${act.instruction}")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        triageInput = "Massive bright red blood spurting from upper thigh"
                        triageResult = semanticRouter.routePanickedQuery(triageInput)
                    }
                ) { Text("ARTERIAL BLEED", color = BastionColors.Red) }
                OutlinedButton(
                    onClick = {
                        triageInput = "Sucking hole in chest with severe gasping"
                        triageResult = semanticRouter.routePanickedQuery(triageInput)
                    }
                ) { Text("CHEST HOLE", color = BastionColors.Red) }
            }
        }

        // 19. Meshtastic Protocol Interop & Slotted Suppression
        CardBox(title = "MESHTASTIC PROTOCOL INTEROP & SLOTTED SUPPRESSION") {
            if (meshtasticSamplePacket != null) {
                Text("Meshtastic Port: ${meshtasticSamplePacket!!.portNum} (TEXT_APP)")
                Text("Hop Limit: ${meshtasticSamplePacket!!.hopLimit} · Payload: ${meshtasticSamplePacket!!.payload.decodeToString()}")
            } else {
                Text("Status: READY FOR CIVILIAN MESHTASTIC TRANSCODING", color = BastionColors.DimRed)
            }
            if (slottedDecision != null) {
                Text("Slotted Relay Delay: ${slottedDecision!!.slottedDelayMs} ms", color = BastionColors.Red)
                Text("Decision: ${slottedDecision!!.reason}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        meshtasticSamplePacket = meshtasticBridge.encodeSurvivalDistressToMeshtastic(
                            fromNodeId = 0xABCD1234L,
                            packetId = 1001L,
                            distressMessage = "SOS: 2 VICTIMS AT RIDGE CREST",
                        )
                        val delay = slottedSuppression.computeSlottedDelay(1001L, 0xABCD1234L)
                        slottedSuppression.recordOverheardPacket(1001L)
                        val decision = slottedSuppression.evaluateRelayDecision(1001L, 0xABCD1234L)
                        slottedDecision = decision
                    }
                ) { Text("TRANSCODE & SLOTTED DELAY", color = BastionColors.Red) }
            }
        }

        // 20. Air-Gapped Direct APK Wi-Fi Direct Distribution
        CardBox(title = "AIR-GAPPED DIRECT APK BEACON DISTRIBUTION") {
            if (apkSession != null && apkSession!!.isHosting) {
                Text("Beacon: ACTIVE (Wi-Fi Hotspot / Direct)", color = BastionColors.Red)
                Text("SSID: ${apkSession!!.ssid} · Passkey: ${apkSession!!.passkey}")
                Text("Captive URL: ${apkSession!!.captivePortalUrl}", color = BastionColors.DimRed)
                Text("Completed Downloads: ${apkSession!!.completedPeerDownloads}", color = BastionColors.Red)
            } else {
                Text("Beacon: INACTIVE (Standby)", color = BastionColors.DimRed)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        apkSession = apkBeacon.startHostingBeacon("NODE01")
                    }
                ) { Text("START APK BEACON", color = BastionColors.Red) }
                OutlinedButton(
                    onClick = {
                        apkBeacon.recordDownloadCompleted()
                        apkSession = apkSession?.copy(completedPeerDownloads = (apkSession?.completedPeerDownloads ?: 0) + 1)
                    }
                ) { Text("SIMULATE PEER DL", color = BastionColors.DimRed) }
                OutlinedButton(
                    onClick = {
                        apkBeacon.stopHosting()
                        apkSession = null
                    }
                ) { Text("STOP", color = BastionColors.DimRed) }
            }
        }
    }
}

@Composable
private fun CardBox(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BastionColors.Ember, RoundedCornerShape(4.dp))
            .background(BastionColors.Black, RoundedCornerShape(4.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = BastionColors.Red)
        content()
    }
}

private fun Float.formatDecimals(decimals: Int): String {
    val factor = when (decimals) {
        0 -> 1.0
        1 -> 10.0
        2 -> 100.0
        3 -> 1000.0
        4 -> 10000.0
        5 -> 100000.0
        else -> 10.0
    }
    val rounded = kotlin.math.round(this * factor) / factor
    if (decimals == 0) return rounded.toLong().toString()
    val str = rounded.toString()
    val parts = str.split('.')
    if (parts.size == 1) return "$str." + "0".repeat(decimals)
    val dec = parts[1].padEnd(decimals, '0').take(decimals)
    return "${parts[0]}.$dec"
}

private fun Double.formatDecimals(decimals: Int): String {
    val factor = when (decimals) {
        0 -> 1.0
        1 -> 10.0
        2 -> 100.0
        3 -> 1000.0
        4 -> 10000.0
        5 -> 100000.0
        else -> 10.0
    }
    val rounded = kotlin.math.round(this * factor) / factor
    if (decimals == 0) return rounded.toLong().toString()
    val str = rounded.toString()
    val parts = str.split('.')
    if (parts.size == 1) return "$str." + "0".repeat(decimals)
    val dec = parts[1].padEnd(decimals, '0').take(decimals)
    return "${parts[0]}.$dec"
}
