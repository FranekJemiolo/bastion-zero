package com.bastionzero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bastionzero.net.TransportTier

@Composable
fun TacticalFieldHud(
    batteryPercent: Int = 85,
    solarWatts: Float = 4.2f,
    activeTransport: TransportTier = TransportTier.BLE_MESH,
    isExclusionZoneActive: Boolean = false,
    onBroadcastSos: () -> Unit = {},
    onTriggerAirgapSync: () -> Unit = {},
) {
    var isGloveLocked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Top Tactical Status Bar
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "BASTION HUD [GLOVE]",
                    color = Color(0xFFFF1744),
                    fontSize = 18.sp,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (isGloveLocked) "🔒 LOCKED" else "🔓 ACTIVE",
                    color = if (isGloveLocked) Color.Yellow else Color(0xFF00E676),
                    fontSize = 14.sp,
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A0000), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF4A0000), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("BATTERY", color = Color.Gray, fontSize = 10.sp)
                    Text("$batteryPercent%", color = Color.White, fontSize = 18.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SOLAR IN", color = Color.Gray, fontSize = 10.sp)
                    Text("${solarWatts}W", color = Color(0xFFFFD600), fontSize = 18.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("LINK TIER", color = Color.Gray, fontSize = 10.sp)
                    Text(activeTransport.name.take(7), color = Color(0xFF00E5FF), fontSize = 16.sp)
                }
            }

            if (isExclusionZoneActive) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFB71C1C), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "⚠️ EXCLUSION ZONE TRIGGERED",
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        // Center Glove Lock Controls
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Oversized SOS Button
            Button(
                onClick = { if (!isGloveLocked) onBroadcastSos() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGloveLocked) Color(0xFF4A0000) else Color(0xFFD50000)
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = if (isGloveLocked) "SOS LOCKED" else "BROADCAST SOS",
                    color = Color.White,
                    fontSize = 20.sp,
                )
            }

            // Air-gap QR Sync Button
            Button(
                onClick = { if (!isGloveLocked) onTriggerAirgapSync() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGloveLocked) Color(0xFF222222) else Color(0xFF2E7D32)
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "OPTICAL QR SYNC",
                    color = Color.White,
                    fontSize = 16.sp,
                )
            }
        }

        // Bottom Glove Lock Toggle
        Button(
            onClick = { isGloveLocked = !isGloveLocked },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isGloveLocked) Color(0xFF37474F) else Color(0xFF212121)
            ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = if (isGloveLocked) "TAP TO UNLOCK TOUCH SCREEN" else "TAP TO ENGAGE RAIN/GLOVE LOCK",
                color = Color.White,
                fontSize = 13.sp,
            )
        }
    }
}
