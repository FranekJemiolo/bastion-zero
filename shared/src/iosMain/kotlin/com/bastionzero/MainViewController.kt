package com.bastionzero

import androidx.compose.ui.window.ComposeUIViewController
import com.bastionzero.db.DatabaseDriverFactory
import com.bastionzero.db.WikiRepository
import com.bastionzero.db.createDatabase
import com.bastionzero.haptics.NoOpHapticPlayer
import com.bastionzero.power.IosPowerBackend
import com.bastionzero.power.PowerGovernor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

@Suppress("FunctionName")
fun MainViewController() = ComposeUIViewController {
    val env = androidx.compose.runtime.remember {
        val governor = PowerGovernor(IosPowerBackend(), appScope).also { it.start() }
        AppEnvironment(
            wiki = WikiRepository(createDatabase(DatabaseDriverFactory())),
            power = governor,
            haptics = NoOpHapticPlayer(),
        )
    }
    App(env)
}
