package com.imyash.pesuwifi.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Utility to ping the Wi-Fi router default gateway or captive portal host.
 * Uses ICMP ping with a fast fallback to TCP socket probing.
 */
object RouterPing {
    private const val TAG = "RouterPing"

    /**
     * Pings the router gateway IP via ICMP.
     * Falls back to probing common router ports (8090, 80, 53) if ICMP is filtered.
     * Returns true if the router is reachable.
     */
    suspend fun pingGateway(gatewayIp: String?, timeoutMs: Int = 1500): Boolean = withContext(Dispatchers.IO) {
        if (gatewayIp.isNullOrBlank()) {
            AppLogger.d(TAG, "pingGateway: No gateway IP provided")
            return@withContext false
        }

        // 1. Try ICMP ping first
        try {
            val timeoutSec = ((timeoutMs + 999) / 1000).coerceAtLeast(1)
            val process = ProcessBuilder("ping", "-c", "1", "-W", timeoutSec.toString(), gatewayIp)
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                AppLogger.d(TAG, "pingGateway($gatewayIp): ICMP ping successful (0% packet loss)")
                return@withContext true
            }
        } catch (e: Exception) {
            AppLogger.d(TAG, "pingGateway($gatewayIp): ICMP ping threw ${e.message}")
        }

        // 2. TCP socket fallback
        val probePorts = listOf(8090, 80, 53)
        for (port in probePorts) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(gatewayIp, port), timeoutMs)
                    AppLogger.d(TAG, "pingGateway($gatewayIp:$port): TCP connect successful")
                    return@withContext true
                }
            } catch (_: Exception) {
                // Continue to next probe port
            }
        }

        AppLogger.w(TAG, "pingGateway($gatewayIp): Router unreachable via ICMP and TCP probes")
        false
    }
}
