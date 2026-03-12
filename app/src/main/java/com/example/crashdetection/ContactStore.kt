package com.example.crashdetection

import android.content.Context

object ContactStore {
    private const val PREF = "crash_prefs"
    private const val KEY_CONTACTS = "emergency_contacts"

    fun save(context: Context, number: String) {
        add(context, number)
    }

    fun add(context: Context, number: String) {
        val normalized = number.trim()
        if (normalized.isEmpty()) return
        val contacts = getAll(context).toMutableSet()
        contacts.add(normalized)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_CONTACTS, contacts)
            .apply()
    }

    fun remove(context: Context, number: String) {
        val contacts = getAll(context).toMutableSet()
        contacts.remove(number)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_CONTACTS, contacts)
            .apply()
    }

    fun get(context: Context): String? = getAll(context).firstOrNull()

    fun getAll(context: Context): List<String> {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getStringSet(KEY_CONTACTS, emptySet())
            ?.filter { it.isNotBlank() }
            ?.sorted()
            ?: emptyList()
    }
}
