package com.bastionzero.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MeshMapScreen(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Mesh Map", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        Text("No peers in range.")
        Text(
            "Offline map tiles and BLE MeshLink arrive in Phase 3. Pins you author will be " +
                "signed, flood-routed and merged with a conflict-free set.",
            color = BastionColors.DimRed,
        )
    }
}
