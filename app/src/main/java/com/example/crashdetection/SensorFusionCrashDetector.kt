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
    private val gyroThresholdRadPerSec: Float = 3.0f,
    private val confirmationWindowMs: Long = 300L,
    private val minTimeBetweenEventsMs: Long = 1500L,
    private val onCrashDetected: (CrashEvent) -> Unit
) : SensorEventListener {

    data class SensorSample(
        val timestampMs: Long,
        val ax: Float,
        val ay: Float,
        val az: Float,
        val gx: Float,
        val gy: Float,
        val gz: Float,
        val gForce: Float,
        val angularVelocityMag: Float
    )

    data class CrashEvent(
        val detectedAtMs: Long,
        val maxGForce: Float,
        val maxAngularVelocity: Float,
        val samples: List<SensorSample>
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

    private var lastEventTimeMs = 0L

    private var candidateActive = false
    private var candidateStartMs = 0L
    private var candidateMaxG = 0f
    private var candidateMaxGyro = 0f
    private var sawImpact = false
    private var sawOrientationChange = false

    private val samplesBuffer = ArrayDeque<SensorSample>()

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
        samplesBuffer.clear()
        resetCandidate()
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

        val gForce = sqrt(
            (latestAx / SensorManager.GRAVITY_EARTH) * (latestAx / SensorManager.GRAVITY_EARTH) +
                (latestAy / SensorManager.GRAVITY_EARTH) * (latestAy / SensorManager.GRAVITY_EARTH) +
                (latestAz / SensorManager.GRAVITY_EARTH) * (latestAz / SensorManager.GRAVITY_EARTH)
        )
        val angularVelocityMag = sqrt(latestGx * latestGx + latestGy * latestGy + latestGz * latestGz)

        val sample = SensorSample(
            timestampMs = nowMs,
            ax = latestAx,
            ay = latestAy,
            az = latestAz,
            gx = latestGx,
            gy = latestGy,
            gz = latestGz,
            gForce = gForce,
            angularVelocityMag = angularVelocityMag
        )
        samplesBuffer.addLast(sample)

        // Keep only ~1 second for low overhead.
        while (samplesBuffer.isNotEmpty() && nowMs - samplesBuffer.first().timestampMs > 1000L) {
            samplesBuffer.removeFirst()
        }

        // Stage 1: impact filter from accelerometer.
        if (!candidateActive && gForce > impactGThreshold && nowMs - lastEventTimeMs > minTimeBetweenEventsMs) {
            candidateActive = true
            candidateStartMs = nowMs
            candidateMaxG = gForce
            candidateMaxGyro = angularVelocityMag
            sawImpact = true
            sawOrientationChange = angularVelocityMag > gyroThresholdRadPerSec
            return
        }

        if (!candidateActive) return

        candidateMaxG = maxOf(candidateMaxG, gForce)
        candidateMaxGyro = maxOf(candidateMaxGyro, angularVelocityMag)
        if (gForce > impactGThreshold) sawImpact = true

        // Stage 2: orientation/rotation validation from gyroscope.
        if (angularVelocityMag > gyroThresholdRadPerSec) {
            sawOrientationChange = true
        }

        // Stage 3: confirm only after short time window.
        val windowElapsed = nowMs - candidateStartMs
        if (windowElapsed >= confirmationWindowMs) {
            if (sawImpact && sawOrientationChange) {
                lastEventTimeMs = nowMs
                val eventSamples = samplesBuffer
                    .filter { it.timestampMs >= candidateStartMs - 100L && it.timestampMs <= nowMs }
                onCrashDetected(
                    CrashEvent(
                        detectedAtMs = nowMs,
                        maxGForce = candidateMaxG,
                        maxAngularVelocity = candidateMaxGyro,
                        samples = eventSamples
                    )
                )
            }
            resetCandidate()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun resetCandidate() {
        candidateActive = false
        candidateStartMs = 0L
        candidateMaxG = 0f
        candidateMaxGyro = 0f
        sawImpact = false
        sawOrientationChange = false
    }
}
