package com.bastionzero.hal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Android implementation of [SensorProvider] utilizing Android's [SensorManager]
 * and the battery thermistor broadcast.
 */
actual class SensorProvider(
    private val context: Context? = null,
) : SensorStream {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _motionSamples = MutableSharedFlow<MotionSample>(extraBufferCapacity = 64)
    override val motionSamples: SharedFlow<MotionSample> = _motionSamples.asSharedFlow()

    private val _environmentalSamples = MutableSharedFlow<EnvironmentalSample>(extraBufferCapacity = 32)
    override val environmentalSamples: SharedFlow<EnvironmentalSample> = _environmentalSamples.asSharedFlow()

    private val _batteryTemperatureCelsius = MutableStateFlow(22.0f)
    override val batteryTemperatureCelsius: StateFlow<Float> = _batteryTemperatureCelsius.asStateFlow()

    private val sensorManager = context?.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    // Current latest motion components
    private var ax = 0f; private var ay = 0f; private var az = 9.80665f
    private var gx = 0f; private var gy = 0f; private var gz = 0f
    private var mx = 0f; private var my = 0f; private var mz = 0f

    // Current latest environmental components
    private var pressureHpa = 1013.25f

    private var isRunning = false

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null || !isRunning) return
            val nowNs = event.timestamp

            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    ax = event.values[0]
                    ay = event.values[1]
                    az = event.values[2]
                    emitMotion(nowNs)
                }
                Sensor.TYPE_GYROSCOPE -> {
                    gx = event.values[0]
                    gy = event.values[1]
                    gz = event.values[2]
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    mx = event.values[0]
                    my = event.values[1]
                    mz = event.values[2]
                }
                Sensor.TYPE_PRESSURE -> {
                    pressureHpa = event.values[0]
                    // Calculate barometric altitude using standard atmospheric hypsometric formula
                    val altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressureHpa)
                    _environmentalSamples.tryEmit(
                        EnvironmentalSample(
                            timestampNs = nowNs,
                            pressureHpa = pressureHpa,
                            temperatureCelsius = _batteryTemperatureCelsius.value,
                            altitudeMeters = altitude,
                        )
                    )
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            val rawTemp = intent?.getIntExtra("temperature", -1) ?: -1
            if (rawTemp > 0) {
                // Android reports battery temperature in tenths of a degree Celsius (e.g., 295 = 29.5°C)
                _batteryTemperatureCelsius.value = rawTemp / 10.0f
            }
        }
    }

    private fun emitMotion(timestampNs: Long) {
        _motionSamples.tryEmit(
            MotionSample(
                timestampNs = timestampNs,
                ax = ax, ay = ay, az = az,
                gx = gx, gy = gy, gz = gz,
                mx = mx, my = my, mz = mz,
            )
        )
    }

    override fun start(intervalMs: Long) {
        if (isRunning || sensorManager == null) return
        isRunning = true

        val samplingPeriodUs = (intervalMs * 1000).toInt()

        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val press = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

        accel?.let { sensorManager.registerListener(sensorListener, it, samplingPeriodUs) }
        gyro?.let { sensorManager.registerListener(sensorListener, it, samplingPeriodUs) }
        mag?.let { sensorManager.registerListener(sensorListener, it, samplingPeriodUs) }
        press?.let { sensorManager.registerListener(sensorListener, it, samplingPeriodUs) }

        context?.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun stop() {
        if (!isRunning) return
        isRunning = false

        sensorManager?.unregisterListener(sensorListener)
        try {
            context?.unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {
        }
    }
}
