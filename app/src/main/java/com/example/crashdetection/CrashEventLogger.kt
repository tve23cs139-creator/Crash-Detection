package com.example.crashdetection

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CrashEventLogger(context: Context) {
    private val appContext = context.applicationContext

    fun logCrash(
        timestamp: Long,
        severity: String,
        latitude: Double?,
        longitude: Double?
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                if (FirebaseApp.getApps(appContext).isEmpty()) {
                    FirebaseApp.initializeApp(appContext)
                }

                val event = hashMapOf(
                    "timestamp" to timestamp,
                    "severity" to severity,
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "deviceId" to Settings.Secure.getString(
                        appContext.contentResolver,
                        Settings.Secure.ANDROID_ID
                    )
                )

                FirebaseFirestore.getInstance()
                    .collection("crash_events")
                    .add(event)
            }.onFailure {
                Log.w(TAG, "Crash event upload failed", it)
            }
        }
    }

    companion object {
        private const val TAG = "CrashEventLogger"
    }
}
