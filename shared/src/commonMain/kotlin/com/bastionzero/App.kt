package com.bastionzero

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bastionzero.db.WikiRepository
import com.bastionzero.db.WikiSeed
import com.bastionzero.haptics.HapticPlayer
import com.bastionzero.mesh.MeshRouter
import com.bastionzero.power.PowerGovernor
import com.bastionzero.ui.BastionColors
import com.bastionzero.ui.BastionTheme
import com.bastionzero.ui.MeshMapScreen
import com.bastionzero.ui.SensorHubScreen
import com.bastionzero.ui.WikiScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything the shared UI needs from the platform, assembled by each host app. */
class AppEnvironment(
    val wiki: WikiRepository,
    val power: PowerGovernor,
    val haptics: HapticPlayer,
    val mesh: MeshRouter,
)

private enum class Tab(val label: String, val glyph: String) {
    MeshMap("Mesh Map", "◎"),
    SensorHub("Sensor Hub", "⌁"),
    OfflineWiki("Offline Wiki", "☰"),
}

@Composable
fun App(env: AppEnvironment) {
    var tab by rememberSaveable { mutableStateOf(Tab.MeshMap) }

    LaunchedEffect(env.wiki) {
        withContext(Dispatchers.Default) { WikiSeed.ensure(env.wiki) }
    }

    BastionTheme {
        Scaffold(
            containerColor = BastionColors.Black,
            bottomBar = {
                NavigationBar(containerColor = BastionColors.Black) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Text(t.glyph) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BastionColors.Red,
                                selectedTextColor = BastionColors.Red,
                                unselectedIconColor = BastionColors.DimRed,
                                unselectedTextColor = BastionColors.DimRed,
                                indicatorColor = BastionColors.Ember,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            val m = Modifier.padding(padding)
            when (tab) {
                Tab.MeshMap -> MeshMapScreen(env.mesh, m)
                Tab.SensorHub -> SensorHubScreen(env.power, env.haptics, m)
                Tab.OfflineWiki -> WikiScreen(env.wiki, m)
            }
        }
    }
}
