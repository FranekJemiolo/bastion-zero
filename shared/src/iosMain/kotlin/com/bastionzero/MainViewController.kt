package com.bastionzero

import androidx.compose.ui.window.ComposeUIViewController
import com.bastionzero.crypto.LibsodiumEd25519
import com.bastionzero.db.DatabaseDriverFactory
import com.bastionzero.db.WikiRepository
import com.bastionzero.db.createDatabase
import com.bastionzero.haptics.NoOpHapticPlayer
import com.bastionzero.mesh.BluetoothMesh
import com.bastionzero.mesh.MeshFactory
import com.bastionzero.power.IosPowerBackend
import com.bastionzero.power.PowerGovernor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

@Suppress("FunctionName")
fun MainViewController() = ComposeUIViewController {
    val env = androidx.compose.runtime.remember {
        appScope.launch {
            try {
                LibsodiumEd25519.ensureInitialized()
            } catch (_: Throwable) {}
        }

        val governor = PowerGovernor(IosPowerBackend(), appScope).also { it.start() }
        val haptics = NoOpHapticPlayer()
        val bleMesh = BluetoothMesh()

        val meshContext = MeshFactory.create(
            transport = bleMesh,
            haptics = haptics,
            powerGovernor = governor,
            scope = appScope,
        ).also { it.router.start() }

        AppEnvironment(
            wiki = WikiRepository(createDatabase(DatabaseDriverFactory())),
            power = governor,
            haptics = haptics,
            mesh = meshContext.router,
            usbSerialHostDriver = com.bastionzero.hal.UsbSerialHostDriver(),
            gnssRawIngestor = com.bastionzero.hal.GnssRawMeasurementIngestor(),
        )
    }
    App(env)
}
