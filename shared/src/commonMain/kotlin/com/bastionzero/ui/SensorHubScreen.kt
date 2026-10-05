package com.bastionzero.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bastionzero.haptics.HapticChord
import com.bastionzero.haptics.HapticPlayer
import com.bastionzero.power.PowerGovernor

@Composable
fun SensorHubScreen(power: PowerGovernor, haptics: HapticPlayer, modifier: Modifier = Modifier) {
    val inputs by power.inputs.collectAsState()
    val policy by power.policy.collectAsState()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Sensor Hub", style = MaterialTheme.typography.headlineMedium)

        Text("PowerOS — ${policy.tier}", style = MaterialTheme.typography.titleMedium)
        Text("Battery ${inputs.batteryPercent}%${if (inputs.isCharging) " (charging)" else ""}")
        Text("Motion: ${if (inputs.isMoving) "moving" else "still"}")
        Text("UI refresh ${policy.uiRefreshHz} Hz · brightness ${(policy.screenBrightness * 100).toInt()}%",
            color = BastionColors.DimRed)
        Text("GPS every ${policy.gpsIntervalMs / 1000}s · sensors every ${policy.sensorIntervalMs / 1000}s",
            color = BastionColors.DimRed)
        Text("Mesh scan duty ${policy.meshScanDutyPercent}% · wakelock ${if (policy.holdWakeLock) "held" else "released"}",
            color = BastionColors.DimRed)

        Text("Haptic chords", style = MaterialTheme.typography.titleMedium)
        HapticChord.entries.forEach { chord ->
            OutlinedButton(onClick = { haptics.play(chord) }) { Text(chord.name.replace('_', ' ')) }
        }
        Text("Chords play on Android only for now.", color = BastionColors.DimRed)
    }
}
