package com.example.crashdetection

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class CrashDetector(
    context: Context,
    private val onCrashDetected: (Float) -> Unit
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val CRASH_G_FORCE_THRESHOLD = 5.0
    private val MIN_TIME_BETWEEN_EVENTS_MS = 1500

    private var lastEventTime = 0L

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        val gForce = sqrt(gX*gX + gY*gY + gZ*gZ).toFloat()

        if (gForce > CRASH_G_FORCE_THRESHOLD) {
            val now = System.currentTimeMillis()
            if (now - lastEventTime > MIN_TIME_BETWEEN_EVENTS_MS) {
                lastEventTime = now
                onCrashDetected(gForce)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}