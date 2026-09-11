package com.imyash.pesuwifi.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Persistent catalog of every campus AP BSSID (MAC address) the device has
 * ever seen or connected to. Written only in the Stable build (ENABLE_UNIVERSAL_LOGS=false)
 * as a lightweight replacement for the verbose diagnostic log.
 *
 * Goal: build a complete BSSID map of every router on the PESU EC campus.
 *
 * Storage: SharedPreferences JSON set — zero extra dependencies, survives
 * app reinstalls via Android Backup.
 */
object BssidDatabase {
    private const val PREFS_NAME = "pesu_bssid_db"
    private const val KEY_BSSIDS = "seen_bssids"
    private val gson = Gson()

    /**
     * Record a BSSID. No-op if already known or if the address is the Android
     * privacy-placeholder "02:00:00:00:00:00".
     */
    fun record(context: Context, bssid: String) {
        if (bssid.isBlank() || bssid == "02:00:00:00:00:00") return
        val norm = bssid.lowercase().trim()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getAll(context).toMutableSet()
        if (current.add(norm)) {
            prefs.edit().putString(KEY_BSSIDS, gson.toJson(current)).apply()
        }
    }

    /** Returns the full set of known campus BSSIDs (lowercase normalised). */
    fun getAll(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_BSSIDS, null) ?: return emptySet()
        return try {
            val type = object : TypeToken<Set<String>>() {}.type
            gson.fromJson(json, type) ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    /** Total number of unique campus APs seen so far. */
    fun count(context: Context): Int = getAll(context).size
}
