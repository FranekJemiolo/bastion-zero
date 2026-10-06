package com.bastionzero.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bastionzero.App
import com.bastionzero.AppEnvironment
import com.bastionzero.crypto.LibsodiumEd25519
import com.bastionzero.db.DatabaseDriverFactory
import com.bastionzero.db.WikiRepository
import com.bastionzero.db.createDatabase
import com.bastionzero.mesh.BluetoothMesh
import com.bastionzero.mesh.MeshContext
import com.bastionzero.mesh.MeshFactory
import com.bastionzero.power.AndroidPowerBackend
import com.bastionzero.power.PowerGovernor
import com.bastionzero.power.PowerPolicy
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var governor: PowerGovernor
    private lateinit var meshContext: MeshContext
    private val panicTrigger = com.bastionzero.hal.HardwarePanicTrigger()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        governor.start()
        if (hasBlePermissions()) {
            meshContext.router.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            try {
                LibsodiumEd25519.ensureInitialized()
            } catch (_: Throwable) {}
        }

        governor = PowerGovernor(AndroidPowerBackend(applicationContext), lifecycleScope)
        val haptics = AndroidHapticPlayer(this)
        val bleMesh = BluetoothMesh(applicationContext)

        meshContext = MeshFactory.create(
            transport = bleMesh,
            haptics = haptics,
            powerGovernor = governor,
            scope = lifecycleScope,
        )

        val env = AppEnvironment(
            wiki = WikiRepository(createDatabase(DatabaseDriverFactory(applicationContext))),
            power = governor,
            haptics = haptics,
            mesh = meshContext.router,
            panicTrigger = panicTrigger,
            usbSerialHostDriver = com.bastionzero.hal.UsbSerialHostDriver(applicationContext),
            gnssRawIngestor = com.bastionzero.hal.GnssRawMeasurementIngestor(applicationContext),
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                governor.policy.collect(::applyDisplayPolicy)
            }
        }

        requestPermissionsAndStart()
        setContent { App(env) }
    }

    private fun requestPermissionsAndStart() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            governor.start()
            meshContext.router.start()
        }
    }

    private fun hasBlePermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
            val triggered = panicTrigger.registerButtonPress(System.currentTimeMillis())
            if (triggered) {
                meshContext.router.broadcastSos(com.bastionzero.proto.SurvivalPacket.PacketType.SOS_MEDICAL, note = "PANIC_HARDWARE_TRIGGER")
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun applyDisplayPolicy(policy: PowerPolicy) {
        window.attributes = window.attributes.apply {
            screenBrightness = policy.screenBrightness
            preferredRefreshRate = policy.uiRefreshHz.toFloat()
        }
    }
}
