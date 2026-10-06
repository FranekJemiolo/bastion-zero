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

import com.bastionzero.hal.AcousticEdgeAI
import com.bastionzero.acoustic.UltrasonicModem
import com.bastionzero.hal.DeadMansSwitch
import com.bastionzero.hal.GnssSpoofingDetector
import com.bastionzero.hal.InertialDeadReckoning
import com.bastionzero.hal.StructuralTiltMonitor
import com.bastionzero.hub.SdrSignalTriangulation
import com.bastionzero.hub.SolarInsolationOptimizer
import com.bastionzero.hub.TacticalHubBridge
import com.bastionzero.hub.UsbSerialPeripheralBridge
import com.bastionzero.medical.OpticalVitalsMonitor
import com.bastionzero.medical.WoundPhotogrammetry
import com.bastionzero.net.BluetoothChannelSounding
import com.bastionzero.net.NtnSatelliteMeshBridge
import com.bastionzero.rag.EdgeMedicalRag
import com.bastionzero.thermal.ThermalImagingEngine
import com.bastionzero.trauma.KinematicTraumaLogger

import com.bastionzero.airgap.AirgapBundleSync
import com.bastionzero.crypto.SecureEnclaveKeyManager
import com.bastionzero.net.UnifiedMeshRouter
import com.bastionzero.power.AutonomousPowerGovernor
import com.bastionzero.ui.TacticalFieldHud

/** Everything the shared UI needs from the platform, assembled by each host app. */
class AppEnvironment(
    val wiki: WikiRepository,
    val power: PowerGovernor,
    val haptics: HapticPlayer,
    val mesh: MeshRouter,
    val deadReckoning: InertialDeadReckoning = InertialDeadReckoning(),
    val tiltMonitor: StructuralTiltMonitor = StructuralTiltMonitor(),
    val deadMansSwitch: DeadMansSwitch = DeadMansSwitch(),
    val acousticEdgeAI: AcousticEdgeAI = AcousticEdgeAI(),
    val gnssSpoofing: GnssSpoofingDetector = GnssSpoofingDetector(),
    val traumaLogger: KinematicTraumaLogger = KinematicTraumaLogger(),
    val thermalEngine: ThermalImagingEngine = ThermalImagingEngine(),
    val tacticalHub: TacticalHubBridge = TacticalHubBridge(),
    val ultrasonicModem: UltrasonicModem = UltrasonicModem(),
    val vitalsMonitor: OpticalVitalsMonitor = OpticalVitalsMonitor(),
    val woundPhotogrammetry: WoundPhotogrammetry = WoundPhotogrammetry(),
    val solarOptimizer: SolarInsolationOptimizer = SolarInsolationOptimizer(),
    val sdrTriangulation: SdrSignalTriangulation = SdrSignalTriangulation(),
    val usbSerialBridge: UsbSerialPeripheralBridge = UsbSerialPeripheralBridge(),
    val channelSounding: BluetoothChannelSounding = BluetoothChannelSounding(),
    val satelliteBridge: NtnSatelliteMeshBridge = NtnSatelliteMeshBridge(),
    val medicalRag: EdgeMedicalRag = EdgeMedicalRag(),
    val unifiedRouter: UnifiedMeshRouter = UnifiedMeshRouter(),
    val autonomousPower: AutonomousPowerGovernor = AutonomousPowerGovernor(),
    val enclaveManager: SecureEnclaveKeyManager = SecureEnclaveKeyManager(),
    val airgapSync: AirgapBundleSync = AirgapBundleSync(),
    val vectorMapEngine: com.bastionzero.nav.OfflineVectorMapEngine = com.bastionzero.nav.OfflineVectorMapEngine(),
    val mapSnapping: com.bastionzero.nav.MapSnappingEngine = com.bastionzero.nav.MapSnappingEngine(),
    val neuralClassifier: com.bastionzero.acoustic.AcousticNeuralClassifier = com.bastionzero.acoustic.AcousticNeuralClassifier(),
    val semanticRouter: com.bastionzero.rag.EdgeRagSemanticRouter = com.bastionzero.rag.EdgeRagSemanticRouter(),
    val panicTrigger: com.bastionzero.hal.HardwarePanicTrigger = com.bastionzero.hal.HardwarePanicTrigger(),
    val meshtasticBridge: com.bastionzero.net.MeshtasticProtocolBridge = com.bastionzero.net.MeshtasticProtocolBridge(),
    val slottedSuppression: com.bastionzero.net.SlottedRebroadcastSuppression = com.bastionzero.net.SlottedRebroadcastSuppression(),
    val apkBeacon: com.bastionzero.airgap.AirgapApkBeacon = com.bastionzero.airgap.AirgapApkBeacon(),
    val usbSerialHostDriver: com.bastionzero.hal.UsbSerialHostDriver? = null,
    val gnssRawIngestor: com.bastionzero.hal.GnssRawMeasurementIngestor? = null,
)

private enum class Tab(val label: String, val glyph: String) {
    MeshMap("Mesh Map", "◎"),
    SensorHub("Sensor Hub", "⌁"),
    TacticalHud("Tactical HUD", "⚡"),
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
                Tab.SensorHub -> SensorHubScreen(env, m)
                Tab.TacticalHud -> TacticalFieldHud()
                Tab.OfflineWiki -> WikiScreen(env.wiki, m)
            }
        }
    }
}
