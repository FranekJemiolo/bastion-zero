package com.bastionzero.power

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryLevelDidChangeNotification
import platform.UIKit.UIDeviceBatteryState
import platform.UIKit.UIDeviceBatteryStateDidChangeNotification

const val IOS_REFRESH_TASK_ID = "com.bastionzero.ios.refresh"

/**
 * Must be called from Swift before the app finishes launching (App.init):
 * BGTaskScheduler rejects registrations made later.
 * iOS gives no guaranteed background execution: the system decides if/when this runs.
 */
@OptIn(ExperimentalForeignApi::class)
fun registerBackgroundTasks() {
    BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(IOS_REFRESH_TASK_ID, null) { task ->
        scheduleBackgroundRefresh() // always chain the next opportunity
        IosPowerState.refreshBattery() // Phase 3 will also drain the mesh queue here
        task?.setTaskCompletedWithSuccess(true)
    }
}

@OptIn(ExperimentalForeignApi::class)
fun scheduleBackgroundRefresh(minutes: Int = 15) {
    val request = BGAppRefreshTaskRequest(identifier = IOS_REFRESH_TASK_ID)
    request.earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(minutes * 60.0)
    BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null)
}

internal object IosPowerState {
    private val _inputs = MutableStateFlow(PowerInputs(batteryPercent = 100, isCharging = false, isMoving = true))
    val inputs: StateFlow<PowerInputs> = _inputs

    fun refreshBattery() {
        val device = UIDevice.currentDevice
        val level = device.batteryLevel // -1f when unknown (e.g. simulator)
        val pct = if (level < 0f) 100 else (level * 100).toInt()
        val charging = device.batteryState == UIDeviceBatteryState.UIDeviceBatteryStateCharging ||
            device.batteryState == UIDeviceBatteryState.UIDeviceBatteryStateFull
        _inputs.update { it.copy(batteryPercent = pct, isCharging = charging) }
    }
}

/**
 * Graceful degradation: iOS cannot run a keep-alive service or kill radios. We observe
 * battery state and ask for periodic background refreshes where the OS permits.
 * Motion is assumed `true` until CoreMotion arrives in Phase 4.
 */
class IosPowerBackend : PowerBackend {
    override val inputs: StateFlow<PowerInputs> = IosPowerState.inputs
    private val observers = mutableListOf<Any>()

    override fun start() {
        UIDevice.currentDevice.batteryMonitoringEnabled = true
        IosPowerState.refreshBattery()
        if (observers.isEmpty()) {
            val center = NSNotificationCenter.defaultCenter
            listOf(UIDeviceBatteryLevelDidChangeNotification, UIDeviceBatteryStateDidChangeNotification).forEach { name ->
                observers += center.addObserverForName(name, null, NSOperationQueue.mainQueue) {
                    IosPowerState.refreshBattery()
                }
            }
        }
        scheduleBackgroundRefresh()
    }

    override fun stop() {
        val center = NSNotificationCenter.defaultCenter
        observers.forEach { center.removeObserver(it) }
        observers.clear()
        UIDevice.currentDevice.batteryMonitoringEnabled = false
    }
}
