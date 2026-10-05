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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bastionzero.crdt.PinKind
import com.bastionzero.mesh.MeshRouter
import com.bastionzero.proto.SurvivalPacket

@Composable
fun MeshMapScreen(mesh: MeshRouter, modifier: Modifier = Modifier) {
    val peerCount by mesh.peerCount.collectAsState()
    val pins by mesh.pins.collectAsState()
    val packets by mesh.recentPackets.collectAsState()

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Top status card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BastionColors.Outline)
                .background(BastionColors.Ember)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("MeshLink Ad-Hoc Network", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (peerCount > 0) "$peerCount active peer(s) in range" else "Scanning for nearby survival nodes...",
                    color = if (peerCount > 0) BastionColors.Red else BastionColors.DimRed,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("◎ LIVE", color = BastionColors.Red, style = MaterialTheme.typography.labelMedium)
        }

        // Emergency action triggers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ElevatedButton(
                onClick = { mesh.broadcastSos(SurvivalPacket.PacketType.SOS_MEDICAL, note = "CRITICAL MEDICAL DISTRESS") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = BastionColors.Red,
                    contentColor = BastionColors.Black,
                ),
            ) {
                Text("SOS MEDICAL")
            }
            OutlinedButton(
                onClick = { mesh.broadcastSos(SurvivalPacket.PacketType.SOS_RESCUE, note = "RESCUE EXTRACTION NEEDED") },
                modifier = Modifier.weight(1f),
            ) {
                Text("SOS RESCUE")
            }
        }

        // Quick Pin dropping
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { mesh.dropPin(PinKind.HAZARD, "Hazard: Structural Hazard", 52_229_700, 21_012_200) },
                modifier = Modifier.weight(1f),
            ) {
                Text("+ Hazard Pin")
            }
            OutlinedButton(
                onClick = { mesh.dropPin(PinKind.RESOURCE, "Resource: Clean Water", 52_230_500, 21_013_100) },
                modifier = Modifier.weight(1f),
            ) {
                Text("+ Resource Pin")
            }
        }

        // Synchronized CRDT Map Pins
        Text("CRDT Map Pins (${pins.size})", style = MaterialTheme.typography.titleSmall)
        if (pins.isEmpty()) {
            Text("No pins dropped. Tap '+ Hazard Pin' to drop and flood-broadcast.", color = BastionColors.DimRed)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(pins, key = { it.id }) { pin ->
                    val confirmations = mesh.confirmationCount(pin.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BastionColors.Outline)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    if (pin.kind == PinKind.HAZARD) "⚠ HAZARD" else "✓ RESOURCE",
                                    color = BastionColors.Red,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                if (confirmations > 0) {
                                    Text(
                                        "Verified x$confirmations",
                                        color = BastionColors.DimRed,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                            Text(pin.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Lat: ${pin.latE6 / 1e6} | Lon: ${pin.lonE6 / 1e6}",
                                color = BastionColors.DimRed,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(onClick = { mesh.confirmPin(pin.id) }) {
                                Text("+1")
                            }
                            OutlinedButton(onClick = { mesh.removePin(pin.id) }) {
                                Text("✕")
                            }
                        }
                    }
                }
            }
        }

        // Live Mesh Packet Log
        Text("Recent Mesh Packets (${packets.size})", style = MaterialTheme.typography.titleSmall)
        LazyColumn(
            modifier = Modifier.weight(0.7f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(packets) { p ->
                val typeName = p.type.name
                val senderShort = p.senderId.hex().take(8)
                Text(
                    "[$typeName] L:${p.lamport} TTL:${p.ttl} From: $senderShort...",
                    color = if (typeName.startsWith("SOS")) BastionColors.Red else BastionColors.DimRed,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
