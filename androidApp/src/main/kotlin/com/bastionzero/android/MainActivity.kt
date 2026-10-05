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
import com.bastionzero.db.DatabaseDriverFactory
import com.bastionzero.db.WikiRepository
import com.bastionzero.db.createDatabase
import com.bastionzero.power.AndroidPowerBackend
import com.bastionzero.power.PowerGovernor
import com.bastionzero.power.PowerPolicy
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var governor: PowerGovernor

    // The foreground-service notification is invisible without this on API 33+, but the
    // service runs either way, so we start it regardless of the answer.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { governor.start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        governor = PowerGovernor(AndroidPowerBackend(applicationContext), lifecycleScope)
        val env = AppEnvironment(
            wiki = WikiRepository(createDatabase(DatabaseDriverFactory(applicationContext))),
            power = governor,
            haptics = AndroidHapticPlayer(this),
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                governor.policy.collect(::applyDisplayPolicy)
            }
        }

        startNode()
        setContent { App(env) }
    }

    private fun startNode() {
        val needsAsk = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsAsk) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else governor.start()
    }

    /** Brightness and preferred refresh rate follow the power policy (both best-effort hints). */
    private fun applyDisplayPolicy(policy: PowerPolicy) {
        window.attributes = window.attributes.apply {
            screenBrightness = policy.screenBrightness
            preferredRefreshRate = policy.uiRefreshHz.toFloat()
        }
    }
}
