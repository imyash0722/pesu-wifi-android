package com.imyash.pesuwifi.data

data class WifiTelemetry(
    val isConnected: Boolean = false,
    val ssid: String? = null,
    val bssid: String? = null,
    val rssi: Int? = null,
    val frequency: Int? = null,
    val channel: Int? = null,
    val ip: String? = null,
    val gateway: String? = null,
    val isCampus: Boolean = false,
    val lastUpdate: Long = 0L
)

fun frequencyToChannel(freq: Int): Int {
    return when {
        freq == 2484 -> 14
        freq in 2412..2472 -> (freq - 2412) / 5 + 1
        freq in 5170..5825 -> (freq - 5170) / 5 + 34
        freq in 5925..7125 -> (freq - 5925) / 5 + 1
        else -> 0
    }
}
