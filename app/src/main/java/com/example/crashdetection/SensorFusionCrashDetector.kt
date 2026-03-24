package com.example.crashdetection

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class SensorFusionCrashDetector(
    context: Context,
    private val impactGThreshold: Float = 5.0f,
    private val minTimeBetweenTriggersMs: Long = 1500L,
    private val onWindowReady: (WindowReadyEvent) -> Unit
) : SensorEventListener {

    data class SensorReading(
        val ax: Float,
        val ay: Float,
        val az: Float,
        val gx: Float,
        val gy: Float,
        val gz: Float
    )

    data class WindowReadyEvent(
        val peakGForce: Float,
        val window: Array<FloatArray>
    )

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private var latestAx = 0f
    private var latestAy = 0f
    private var latestAz = SensorManager.GRAVITY_EARTH
    private var latestGx = 0f
    private var latestGy = 0f
    private var latestGz = 0f
    private var peakGForceSinceArmed = 0f
    private var triggerArmed = false
    private var lastTriggerTimeMs = 0L

    private val readingsBuffer = ArrayDeque<SensorReading>(WINDOW_SIZE)

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        readingsBuffer.clear()
        triggerArmed = false
        peakGForceSinceArmed = 0f
    }

    override fun onSensorChanged(event: SensorEvent) {
        val nowMs = System.currentTimeMillis()

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                latestAx = event.values[0]
                latestAy = event.values[1]
                latestAz = event.values[2]
            }
            Sensor.TYPE_GYROSCOPE -> {
                latestGx = event.values[0]
                latestGy = event.values[1]
                latestGz = event.values[2]
            }
            else -> return
        }

        val sample = SensorReading(
            ax = latestAx,
            ay = latestAy,
            az = latestAz,
            gx = latestGx,
            gy = latestGy,
            gz = latestGz
        )
        readingsBuffer.addLast(sample)
        while (readingsBuffer.size > WINDOW_SIZE) {
            readingsBuffer.removeFirst()
        }

        val gForce = computeGForce(latestAx, latestAy, latestAz)
        if (gForce >= impactGThreshold && nowMs - lastTriggerTimeMs > minTimeBetweenTriggersMs) {
            triggerArmed = true
            peakGForceSinceArmed = maxOf(peakGForceSinceArmed, gForce)
        }

        if (!triggerArmed || readingsBuffer.size < WINDOW_SIZE) return

        val readingsSnapshot = readingsBuffer.toList()
        onWindowReady(
            WindowReadyEvent(
                peakGForce = peakGForceSinceArmed,
                window = readingsSnapshot.toModelInputWindow()
            )
        )
        triggerArmed = false
        peakGForceSinceArmed = 0f
        lastTriggerTimeMs = nowMs
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun computeGForce(ax: Float, ay: Float, az: Float): Float {
        val gx = ax / SensorManager.GRAVITY_EARTH
        val gy = ay / SensorManager.GRAVITY_EARTH
        val gz = az / SensorManager.GRAVITY_EARTH
        return sqrt(gx * gx + gy * gy + gz * gz)
    }

    private fun List<SensorReading>.toModelInputWindow(): Array<FloatArray> {
        return Array(WINDOW_SIZE) { index ->
            val s = this[index]
            floatArrayOf(s.ax, s.ay, s.az, s.gx, s.gy, s.gz)
        }
    }

    companion object {
        const val WINDOW_SIZE = 50
    }
}
