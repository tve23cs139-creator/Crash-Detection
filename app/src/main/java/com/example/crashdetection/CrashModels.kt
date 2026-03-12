package com.example.crashdetection

data class SensorSample(
    val timestamp: Long,
    val gForce: Float,
    val gyroMagnitude: Float
)

data class CrashSegment(
    val peakGForce: Float,
    val peakGyro: Float,
    val orientationShift: Float,
    val samples: List<SensorSample>
)
