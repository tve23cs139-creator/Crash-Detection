package com.example.crashdetection

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.FirebaseDatabase

object FirebaseLogger {
    private const val TAG = "FirebaseLogger"
    private const val REPORTED_ACCIDENTS_NODE = "reported_accidents"
    private const val DATABASE_URL =
        "https://crash-detection-6785b-default-rtdb.asia-southeast1.firebasedatabase.app"

    // Fill these with the Android app's Firebase credentials.
    private const val API_KEY = "REPLACE_WITH_FIREBASE_API_KEY"
    private const val APPLICATION_ID = "REPLACE_WITH_FIREBASE_APP_ID"
    private const val PROJECT_ID = "crash-detection-6785b"

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun log(event: CrashEvent) {
        val context = appContext?.applicationContext
        if (context == null) {
            Log.e(TAG, "FirebaseLogger not initialized. Call FirebaseLogger.init(context) first.")
            return
        }

        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApiKey(API_KEY)
                    .setApplicationId(APPLICATION_ID)
                    .setProjectId(PROJECT_ID)
                    .setDatabaseUrl(DATABASE_URL)
                    .build()
                FirebaseApp.initializeApp(context, options)
                Log.d(TAG, "Firebase manually initialized")
            }

            val firebaseApp = FirebaseApp.getInstance()
            val databaseRef = FirebaseDatabase.getInstance(firebaseApp, DATABASE_URL).reference
            val key = databaseRef.child(REPORTED_ACCIDENTS_NODE).push().key

            if (key != null) {
                databaseRef.child(REPORTED_ACCIDENTS_NODE).child(key).setValue(event.toMap())
                    .addOnSuccessListener {
                        Log.d(TAG, "Crash event logged successfully with key: $key")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to log crash event: ${e.message}", e)
                    }
            } else {
                Log.e(TAG, "Failed to generate unique key for crash event")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in log(): ${e.message}", e)
        }
    }

    fun logWithTimestamp(
        maxG: Float,
        severity: String,
        latitude: Double?,
        longitude: Double?
    ) {
        val event = CrashEvent(
            timestamp = System.currentTimeMillis(),
            maxG = maxG,
            severity = severity,
            latitude = latitude,
            longitude = longitude
        )
        log(event)
    }
}
