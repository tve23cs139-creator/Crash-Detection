package com.example.crashdetection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class SimulationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SIMULATE_CRASH) {
            return
        }

        FirebaseLogger.init(context)

        val maxG = intent.getFloatExtra(EXTRA_MAX_G, 0f)
        val severity = intent.getStringExtra(EXTRA_SEVERITY).orEmpty()

        Log.d(TAG, "Received simulated crash broadcast: maxG=$maxG, severity=$severity")
        FirebaseLogger.logWithTimestamp(
            maxG = maxG,
            severity = severity,
            latitude = null,
            longitude = null
        )
    }

    companion object {
        const val ACTION_SIMULATE_CRASH = "com.example.crashdetection.SIMULATE_CRASH"
        const val EXTRA_MAX_G = "maxG"
        const val EXTRA_SEVERITY = "severity"

        private const val TAG = "SimulationReceiver"
    }
}
