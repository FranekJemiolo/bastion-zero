package com.bastionzero.power

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Platform side of power management: supplies [PowerInputs] and keeps the app
 * alive as far as the OS allows. Implemented by an Android foreground service
 * and an iOS BGTaskScheduler/UIDevice observer.
 */
interface PowerBackend {
    val inputs: StateFlow<PowerInputs>
    fun start()
    fun stop()
}

/** Turns platform inputs into the [PowerPolicy] every subsystem must obey. */
class PowerGovernor(private val backend: PowerBackend, scope: CoroutineScope) {
    val inputs: StateFlow<PowerInputs> = backend.inputs

    val policy: StateFlow<PowerPolicy> = backend.inputs
        .map(PowerPolicyCalculator::compute)
        .stateIn(scope, SharingStarted.Eagerly, PowerPolicyCalculator.compute(backend.inputs.value))

    fun start() = backend.start()
    fun stop() = backend.stop()
}
