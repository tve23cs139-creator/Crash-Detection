package com.example.crashdetection

import android.content.Context

object ContactStore {
    private const val PREF = "crash_prefs"
    private const val KEY = "emergency_contact"

    fun save(context: Context, number: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, number).apply()
    }

    fun get(context: Context): String? {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, null)
    }
}