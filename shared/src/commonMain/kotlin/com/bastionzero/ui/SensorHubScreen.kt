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
import com.bastionzero.power.PowerGovernor
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
    modifier: Modifier = Modifier,
) {
    val inputs by power.inputs.collectAsState()
    val policy by power.policy.collectAsState()

    val drState by deadReckoning.state.collectAsState()
    val tiltStatus by tiltMonitor.status.collectAsState()
    val dmsState by deadMansSwitch.state.collectAsState()
    val gnssReport by gnssSpoofing.integrityReport.collectAsState()
    val currentSpl by acousticEdgeAI.currentSpl.collectAsState()

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
            Text("Steps: ${drState.stepCount} · Distance: ${"%.1f".format(drState.totalDistanceMeters)} m", color = BastionColors.Red)
            Text("Heading: ${"%.1f".format(drState.headingDegrees)}° · Altitude: ${"%.1f".format(drState.altitudeMeters)} m")
            Text("DR Position: ${"%.5f".format(drState.lat)}, ${"%.5f".format(drState.lon)}", color = BastionColors.DimRed)
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
            Text("Tilt Deviation: ${"%.3f".format(tiltStatus.temperatureCompensatedDeltaDeg)}° (Raw: ${"%.3f".format(tiltStatus.currentTiltDeg)}°)")
            Text("Thermistor: ${"%.1f".format(tiltStatus.currentTempCelsius)}°C (Ref: ${"%.1f".format(tiltStatus.baselineTempCelsius)}°C)", color = BastionColors.DimRed)
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
            Text("Live SPL: ${"%.1f".format(currentSpl)} dB SPL (Gate: > 70 dB)", color = if (currentSpl >= 70f) BastionColors.Red else BastionColors.DimRed)
            val threat = latestThreat
            if (threat != null) {
                Text("Detected: ${threat.signature} (${(threat.confidence * 100).toInt()}%)", color = BastionColors.Red)
                Text("Dominant Frequency: ${"%.0f".format(threat.dominantFrequencyHz)} Hz", color = BastionColors.DimRed)
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
            Text("AGC Level: ${"%.1f".format(gnssReport.agcLevelDb)} dB · Baseline: ${"%.1f".format(gnssReport.baselineAgcDb)} dB")
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
