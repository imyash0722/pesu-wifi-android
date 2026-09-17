package com.imyash.pesuwifi.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.imyash.pesuwifi.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * Data model for a mapped campus Wi-Fi access point radio.
 */
data class CampusAp(
    val bssid: String,
    val ssid: String = "PESU-EC-Campus",
    val band: String = "5GHz", // "2.4GHz" or "5GHz"
    val channel: Int = 0,
    val gateway: String? = null,
    val tag: String? = null, // e.g. "GJBC 4th Floor Classroom 402"
    val lastSeen: Long = System.currentTimeMillis()
)

/**
 * Persistent two-tier catalog of every campus AP BSSID (MAC address) on the PESU EC campus.
 *
 * Tier 1: Bundled static database (assets/campus_bssids.json) shipped with the APK.
 * Tier 2: Dynamic SharedPreferences database (persists newly observed APs as you walk around).
 *
 * In-Memory: High-performance ConcurrentHashMap for O(1) 0ms lookup during network checks.
 */
object BssidDatabase {
    private const val TAG = "BssidDatabase"
    private const val PREFS_NAME = "pesu_bssid_db"
    private const val KEY_APS_V2 = "campus_aps_v2"
    private const val KEY_LEGACY_BSSIDS = "seen_bssids"

    private val gson: Gson = Gson()
    private val prettyGson: Gson = GsonBuilder().setPrettyPrinting().create()

    private val cache = ConcurrentHashMap<String, CampusAp>()
    private val _campusApsFlow = MutableStateFlow<List<CampusAp>>(emptyList())
    val campusApsFlow: StateFlow<List<CampusAp>> = _campusApsFlow.asStateFlow()

    @Volatile
    private var isInitialized = false

    /**
     * Initializes the in-memory cache from assets (Tier 1) and SharedPreferences (Tier 2).
     */
    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            try {
                // 1. Load Tier 1: Bundled asset baseline
                try {
                    context.assets.open("campus_bssids.json").use { stream ->
                        val reader = InputStreamReader(stream)
                        val type = object : TypeToken<List<CampusAp>>() {}.type
                        val seedList: List<CampusAp>? = gson.fromJson(reader, type)
                        seedList?.forEach { ap ->
                            val norm = ap.bssid.lowercase().trim()
                            cache[norm] = ap.copy(bssid = norm)
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.d(TAG, "No campus_bssids.json asset or failed loading: ${e.message}")
                }

                // 2. Load Tier 2: Dynamic local SharedPreferences
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val dynamicJson = prefs.getString(KEY_APS_V2, null)
                if (!dynamicJson.isNullOrBlank()) {
                    try {
                        val type = object : TypeToken<List<CampusAp>>() {}.type
                        val dynamicList: List<CampusAp>? = gson.fromJson(dynamicJson, type)
                        dynamicList?.forEach { ap ->
                            val norm = ap.bssid.lowercase().trim()
                            val existing = cache[norm]
                            if (existing == null) {
                                cache[norm] = ap.copy(bssid = norm)
                            } else {
                                // Merge dynamic data (dynamic has newer lastSeen and user custom tag)
                                cache[norm] = existing.copy(
                                    tag = ap.tag ?: existing.tag,
                                    gateway = ap.gateway ?: existing.gateway,
                                    channel = if (ap.channel > 0) ap.channel else existing.channel,
                                    band = if (ap.band.isNotEmpty() && ap.band != "Unknown") ap.band else existing.band,
                                    lastSeen = maxOf(existing.lastSeen, ap.lastSeen)
                                )
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "Failed parsing dynamic APs from prefs: ${e.message}")
                    }
                }

                // 3. Migrate any legacy string-only entries (from older v1.4.x builds)
                val legacyJson = prefs.getString(KEY_LEGACY_BSSIDS, null)
                if (!legacyJson.isNullOrBlank()) {
                    try {
                        val legacyType = object : TypeToken<Set<String>>() {}.type
                        val legacySet: Set<String>? = gson.fromJson(legacyJson, legacyType)
                        legacySet?.forEach { bssid ->
                            val norm = bssid.lowercase().trim()
                            if (norm.isNotBlank() && norm != "02:00:00:00:00:00" && !cache.containsKey(norm)) {
                                cache[norm] = CampusAp(bssid = norm, tag = "Legacy Learned")
                            }
                        }
                    } catch (_: Exception) {}
                }

                updateFlow()
                isInitialized = true
                AppLogger.i(TAG, "Loaded BSSID database with ${cache.size} total campus APs")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error initializing BssidDatabase", e)
            }
        }
    }

    private fun ensureInitialized(context: Context) {
        if (!isInitialized) {
            init(context.applicationContext)
        }
    }

    /**
     * Checks if a given BSSID belongs to a known campus router in O(1) time.
     */
    fun isCampusBssid(context: Context, bssid: String?): Boolean {
        if (bssid.isNullOrBlank() || bssid == "02:00:00:00:00:00") return false
        ensureInitialized(context)
        return cache.containsKey(bssid.lowercase().trim())
    }

    /**
     * Records a campus AP observed in Wi-Fi scan results or on active connection.
     * Automatically calculates channel, band, and Cisco dual-band radio pair.
     */
    fun record(
        context: Context,
        bssid: String,
        ssid: String = "PESU-EC-Campus",
        frequency: Int? = null,
        level: Int? = null,
        gateway: String? = null
    ) {
        if (bssid.isBlank() || bssid == "02:00:00:00:00:00") return
        val norm = bssid.lowercase().trim()
        ensureInitialized(context)

        val ch = if (frequency != null && frequency > 0) frequencyToChannel(frequency) else 0
        val band = when {
            frequency != null && frequency > 4000 -> "5GHz"
            frequency != null && frequency > 0 -> "2.4GHz"
            else -> "Unknown"
        }

        var changed = false
        val existing = cache[norm]

        if (existing == null) {
            val newAp = CampusAp(
                bssid = norm,
                ssid = if (ssid.isNotBlank()) ssid else "PESU-EC-Campus",
                band = band,
                channel = ch,
                gateway = gateway,
                lastSeen = System.currentTimeMillis()
            )
            cache[norm] = newAp
            changed = true
            AppLogger.d(TAG, "Discovered new campus AP: $norm ($band, Ch $ch, GW=$gateway)")
        } else {
            // Update existing entry with newer telemetry if available
            val updated = existing.copy(
                ssid = if (ssid.isNotBlank()) ssid else existing.ssid,
                band = if (band != "Unknown") band else existing.band,
                channel = if (ch > 0) ch else existing.channel,
                gateway = gateway ?: existing.gateway,
                lastSeen = System.currentTimeMillis()
            )
            if (updated != existing) {
                cache[norm] = updated
                changed = true
            }
        }

        // Auto-discover Cisco enterprise dual-band twin radio (bit 6 flip on 4th octet)
        val twinBssid = getCiscoTwinBssid(norm)
        if (twinBssid != null && !cache.containsKey(twinBssid)) {
            val twinBand = if (band == "5GHz") "2.4GHz" else if (band == "2.4GHz") "5GHz" else "Unknown"
            cache[twinBssid] = CampusAp(
                bssid = twinBssid,
                ssid = if (ssid.isNotBlank()) ssid else "PESU-EC-Campus",
                band = twinBand,
                gateway = gateway,
                tag = existing?.tag?.let { "$it (Pair)" },
                lastSeen = System.currentTimeMillis()
            )
            changed = true
            AppLogger.d(TAG, "Auto-synthesized Cisco dual-band twin AP: $twinBssid ($twinBand)")
        }

        if (changed) {
            updateFlow()
            persistAsync(context)
        }
    }

    /**
     * User custom label for a specific AP (e.g. "GJBC Floor 4 Room 402").
     */
    fun setTag(context: Context, bssid: String, tag: String?) {
        val norm = bssid.lowercase().trim()
        ensureInitialized(context)
        val existing = cache[norm]
        if (existing != null) {
            cache[norm] = existing.copy(tag = tag?.takeIf { it.isNotBlank() })
            updateFlow()
            persistAsync(context)
        } else {
            cache[norm] = CampusAp(bssid = norm, tag = tag?.takeIf { it.isNotBlank() })
            updateFlow()
            persistAsync(context)
        }
    }

    /**
     * Returns an AP by BSSID if known.
     */
    fun getAp(context: Context, bssid: String): CampusAp? {
        ensureInitialized(context)
        return cache[bssid.lowercase().trim()]
    }

    /**
     * Returns all known campus APs sorted by last seen descending.
     */
    fun getAll(context: Context): List<CampusAp> {
        ensureInitialized(context)
        return cache.values.toList().sortedByDescending { it.lastSeen }
    }

    /**
     * Total count of unique campus APs known to the database.
     */
    fun count(context: Context): Int {
        ensureInitialized(context)
        return cache.size
    }

    /**
     * Exports the entire database as pretty-printed JSON.
     */
    fun exportJson(context: Context): String {
        val list = getAll(context)
        return prettyGson.toJson(list)
    }

    /**
     * Imports a shared JSON list of CampusAp into the database.
     * Returns the count of newly added APs.
     */
    fun importJson(context: Context, json: String): Int {
        ensureInitialized(context)
        return try {
            val type = object : TypeToken<List<CampusAp>>() {}.type
            val imported: List<CampusAp>? = gson.fromJson(json, type)
            if (imported.isNullOrEmpty()) return 0

            var newCount = 0
            for (ap in imported) {
                val norm = ap.bssid.lowercase().trim()
                if (norm.isBlank() || norm == "02:00:00:00:00:00") continue
                val existing = cache[norm]
                if (existing == null) {
                    cache[norm] = ap.copy(bssid = norm)
                    newCount++
                } else {
                    cache[norm] = existing.copy(
                        tag = ap.tag ?: existing.tag,
                        gateway = ap.gateway ?: existing.gateway,
                        channel = if (ap.channel > 0) ap.channel else existing.channel,
                        band = if (ap.band.isNotEmpty() && ap.band != "Unknown") ap.band else existing.band,
                        lastSeen = maxOf(existing.lastSeen, ap.lastSeen)
                    )
                }
            }
            if (newCount > 0 || imported.isNotEmpty()) {
                updateFlow()
                persistAsync(context)
            }
            newCount
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed importing JSON AP database", e)
            0
        }
    }

    /**
     * Derives the dual-band twin BSSID for Cisco enterprise APs (Aironet/Catalyst).
     * Bit 6 (0x40) of the 4th octet is flipped between 2.4 GHz and 5 GHz radios.
     */
    fun getCiscoTwinBssid(bssid: String): String? {
        val parts = bssid.lowercase().trim().split(":")
        if (parts.size != 6) return null
        if (parts[0] != "c8" || parts[1] != "a6" || parts[2] != "08") return null
        return try {
            val byte4 = parts[3].toInt(16)
            val twinByte4 = (byte4 xor 0x40).toString(16).padStart(2, '0')
            "${parts[0]}:${parts[1]}:${parts[2]}:$twinByte4:${parts[4]}:${parts[5]}"
        } catch (_: Exception) {
            null
        }
    }

    fun frequencyToChannel(freq: Int): Int {
        return when {
            freq == 2484 -> 14
            freq in 2412..2472 -> (freq - 2412) / 5 + 1
            freq in 5170..5825 -> (freq - 5170) / 5 + 34
            freq in 5925..7125 -> (freq - 5925) / 5 + 1
            else -> 0
        }
    }

    private fun updateFlow() {
        _campusApsFlow.value = cache.values.toList().sortedByDescending { it.lastSeen }
    }

    private fun persistAsync(context: Context) {
        val appCtx = context.applicationContext
        Thread {
            try {
                val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val json = gson.toJson(cache.values.toList())
                prefs.edit().putString(KEY_APS_V2, json).apply()
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed saving BSSID db to prefs: ${e.message}")
            }
        }.start()
    }
}
