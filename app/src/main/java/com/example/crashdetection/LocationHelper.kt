package com.example.crashdetection

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices

class LocationHelper(private val context: Context) {

    private val fused = LocationServices
        .getFusedLocationProviderClient(context)

    fun get(callback: (Double?, Double?) -> Unit) {

        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fine && !coarse) {
            callback(null, null)
            return
        }

        getLastLocation(callback)
    }

    @SuppressLint("MissingPermission")
    private fun getLastLocation(callback: (Double?, Double?) -> Unit) {

        fused.lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    callback(loc.latitude, loc.longitude)
                } else {
                    callback(null, null)
                }
            }
            .addOnFailureListener {
                callback(null, null)
            }
    }
}