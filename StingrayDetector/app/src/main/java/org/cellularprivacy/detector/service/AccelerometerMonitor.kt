package org.cellularprivacy.detector.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Detects whether the device is physically moving, so heuristics can ignore
 * cell/signal changes that are just the user walking past cells. Many
 * detections (unknown cell, signal anomaly, downgrade) are only meaningful
 * when stationary.
 *
 * Movement = the acceleration magnitude departs from gravity (9.81) by more
 * than a small threshold. We hold "moving" for [stillnessWindowMs] after the
 * last movement so brief stillness between steps doesn't flip the state.
 */
class AccelerometerMonitor(
    context: Context,
    private val movementThreshold: Float = 0.6f,
    private val stillnessWindowMs: Long = 4_000L
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    @Volatile private var lastMovementMs = 0L

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /** True if movement was detected within the stillness window. */
    fun isMoving(): Boolean =
        System.currentTimeMillis() - lastMovementMs < stillnessWindowMs

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
        val magnitude = sqrt(x * x + y * y + z * z)
        if (abs(magnitude - SensorManager.GRAVITY_EARTH) > movementThreshold) {
            lastMovementMs = System.currentTimeMillis()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* no-op */ }
}
