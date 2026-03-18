package com.example.crashdetection

data class CrashEvent(
    val timestamp: Long = 0L,
    val maxG: Float = 0f,
    val severity: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "timestamp" to timestamp,
            "maxG" to maxG,
            "severity" to severity,
            "latitude" to latitude,
            "longitude" to longitude
        )
    }
}
