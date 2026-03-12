package com.example.crashdetection

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class CrashDetector(
    context: Context,
    private val onCrashDetected: (CrashSegment) -> Unit
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val gyroscope =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val crashGForceThreshold = 5.0f
    private val orientationShiftThreshold = 2.2f
    private val normalMovementGyroThreshold = 1.5f
    private val minTimeBetweenEventsMs = 1500L
    private val fusionWindowMs = 1200L

    private var lastEventTime = 0L
    private var latestGyroMagnitude = 0f
    private var lastGyroTimestampNanos = 0L
    private var cumulativeOrientationShift = 0f

    private val crashBuffer = ArrayDeque<SensorSample>()

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
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> handleGyroscope(event)
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event)
        }
    }

    private fun handleGyroscope(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        latestGyroMagnitude = sqrt(x * x + y * y + z * z)

        if (lastGyroTimestampNanos > 0L) {
            val deltaSeconds = (event.timestamp - lastGyroTimestampNanos) / 1_000_000_000f
            cumulativeOrientationShift += latestGyroMagnitude * deltaSeconds
        }
        lastGyroTimestampNanos = event.timestamp
    }

    private fun handleAccelerometer(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)
        val now = System.currentTimeMillis()

        crashBuffer.addLast(
            SensorSample(
                timestamp = now,
                gForce = gForce,
                gyroMagnitude = latestGyroMagnitude
            )
        )

        while (crashBuffer.isNotEmpty() && now - crashBuffer.first().timestamp > fusionWindowMs) {
            crashBuffer.removeFirst()
        }

        if (gForce <= crashGForceThreshold) return
        if (latestGyroMagnitude < normalMovementGyroThreshold) return
        if (cumulativeOrientationShift < orientationShiftThreshold) return
        if (now - lastEventTime <= minTimeBetweenEventsMs) return

        lastEventTime = now

        val snapshot = crashBuffer.toList()
        val peakG = snapshot.maxOfOrNull { it.gForce } ?: gForce
        val peakGyro = snapshot.maxOfOrNull { it.gyroMagnitude } ?: latestGyroMagnitude

        onCrashDetected(
            CrashSegment(
                peakGForce = peakG,
                peakGyro = peakGyro,
                orientationShift = cumulativeOrientationShift,
                samples = snapshot
            )
        )

        cumulativeOrientationShift = 0f
        crashBuffer.clear()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
