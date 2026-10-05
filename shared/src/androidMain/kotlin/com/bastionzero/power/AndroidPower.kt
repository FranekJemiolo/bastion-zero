package com.bastionzero.power

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Process-wide bridge between the service (producer) and [AndroidPowerBackend] (consumer). */
internal object AndroidPowerState {
    private val _inputs = MutableStateFlow(PowerInputs(batteryPercent = 100, isCharging = false, isMoving = true))
    val inputs: StateFlow<PowerInputs> = _inputs

    fun update(transform: (PowerInputs) -> PowerInputs) = _inputs.update(transform)

    fun readBattery(intent: Intent?): Pair<Int, Boolean>? {
        intent ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return (level * 100 / scale) to charging
    }
}

/**
 * Doze workaround stack:
 *  1. sticky foreground service (START_STICKY) with a persistent notification,
 *  2. a timed partial WakeLock, held only while the policy says it is worth the battery,
 *  3. TYPE_SIGNIFICANT_MOTION trigger sensor: a hardware interrupt that can wake the CPU
 *     in Doze, used to flip the governor between moving/still without polling.
 */
class BastionForegroundService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var sensorManager: SensorManager? = null
    private var motionSensor: Sensor? = null

    private val stillRunnable = Runnable { AndroidPowerState.update { it.copy(isMoving = false) } }
    private val renewWakeLock = object : Runnable {
        override fun run() {
            applyWakeLockPolicy()
            handler.postDelayed(this, WAKELOCK_RENEW_MS)
        }
    }

    private val motionListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            AndroidPowerState.update { it.copy(isMoving = true) }
            handler.removeCallbacks(stillRunnable)
            handler.postDelayed(stillRunnable, STILL_AFTER_MS)
            armMotionTrigger() // trigger sensors are one-shot; re-arm every time
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = onBattery(intent)
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        motionSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        val sticky = ContextCompat.registerReceiver(
            this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onBattery(sticky)
        armMotionTrigger()
        handler.removeCallbacks(renewWakeLock)
        handler.post(renewWakeLock)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(batteryReceiver) }
        motionSensor?.let { sensorManager?.cancelTriggerSensor(motionListener, it) }
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onBattery(intent: Intent?) {
        val (pct, charging) = AndroidPowerState.readBattery(intent) ?: return
        AndroidPowerState.update { it.copy(batteryPercent = pct, isCharging = charging) }
        applyWakeLockPolicy()
    }

    private fun armMotionTrigger() {
        val s = motionSensor
        if (s == null) {
            // No hardware trigger: stay conservative and treat the user as moving.
            AndroidPowerState.update { it.copy(isMoving = true) }
            return
        }
        sensorManager?.requestTriggerSensor(motionListener, s)
    }

    private fun applyWakeLockPolicy() {
        val policy = PowerPolicyCalculator.compute(AndroidPowerState.inputs.value)
        if (policy.holdWakeLock) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            val lock = wakeLock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bastionzero:mesh")
                .also { it.setReferenceCounted(false); wakeLock = it }
            lock.acquire(WAKELOCK_TIMEOUT_MS) // timed: can never leak past a crash
        } else {
            releaseWakeLock()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Bastion Zero node", NotificationManager.IMPORTANCE_LOW),
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Bastion Zero active")
            .setContentText("Node running. Power-saving policy applied.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "bastion_node"
        const val NOTIFICATION_ID = 1
        const val WAKELOCK_TIMEOUT_MS = 10 * 60_000L
        const val WAKELOCK_RENEW_MS = 9 * 60_000L
        const val STILL_AFTER_MS = 2 * 60_000L
    }
}

class AndroidPowerBackend(private val context: Context) : PowerBackend {
    init {
        // Seed with the current battery without needing the service to be running.
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        AndroidPowerState.readBattery(sticky)?.let { (pct, charging) ->
            AndroidPowerState.update { it.copy(batteryPercent = pct, isCharging = charging) }
        }
    }

    override val inputs: StateFlow<PowerInputs> = AndroidPowerState.inputs

    override fun start() {
        ContextCompat.startForegroundService(context, Intent(context, BastionForegroundService::class.java))
    }

    override fun stop() {
        context.stopService(Intent(context, BastionForegroundService::class.java))
    }
}
