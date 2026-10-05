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
import com.bastionzero.hal.AcousticThreatEvent
import com.bastionzero.hal.MotionSample
import com.bastionzero.hal.ThreatSignature
import com.bastionzero.hal.TiltSeverity
import com.bastionzero.haptics.HapticChord
import com.bastionzero.haptics.HapticPlayer
import com.bastionzero.hub.RadiationHazardLevel
import com.bastionzero.hub.TacticalHubBridge
import com.bastionzero.power.PowerGovernor
import com.bastionzero.thermal.SurfaceMaterial
import com.bastionzero.thermal.ThermalImagingEngine
import com.bastionzero.thermal.ThermalReading
import com.bastionzero.trauma.KinematicTraumaLogger
import com.bastionzero.trauma.TraumaSeverity
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

    var selectedThermalMaterial by remember { mutableStateOf(SurfaceMaterial.HUMAN_SKIN) }
    var currentThermalReading by remember {
        mutableStateOf(thermalEngine.calculateTrueTemperature(36.5f, SurfaceMaterial.HUMAN_SKIN))
    }

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
