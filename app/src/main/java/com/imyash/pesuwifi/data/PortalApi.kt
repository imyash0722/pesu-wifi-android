package com.imyash.pesuwifi.data

import android.util.Log
import com.imyash.pesuwifi.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

object PortalApi {
    private const val TAG = "PesuWifiApi"
    const val DEFAULT_PORTAL_BASE = "http://192.168.254.1:8090"

    @Volatile
    var portalBaseUrl: String = DEFAULT_PORTAL_BASE

    val loginUrl: String get() = "$portalBaseUrl/login.xml"
    val logoutUrl: String get() = "$portalBaseUrl/logout.xml"
    val liveUrl: String get() = "$portalBaseUrl/live"
    val probeUrl: String get() = "$portalBaseUrl/httpclient.html"

    @Volatile
    private var wifiSocketFactory: SocketFactory? = null

    /**
     * Binds OkHttp calls to the Wi-Fi network interface's SocketFactory so that
     * campus portal requests bypass Mobile Data / Cellular routing and Tailscale VPN tunnels.
     */
    fun setWifiSocketFactory(factory: SocketFactory?) {
        wifiSocketFactory = factory
    }

    /**
     * Evicts cached sockets to force fresh SYN handshakes upon roaming or gateway IP change.
     */
    fun evictConnectionPool() {
        baseClient.connectionPool.evictAll()
    }

    // Base client:
    // - ConnectionPool(0, 1, TimeUnit.NANOSECONDS): Never pool or reuse sockets, matching Python CLI fresh Session.
    // - Proxy.NO_PROXY: Prevents local RFC-1918 gateway (192.168.254.1) from being routed through system/Squid proxies.
    // - retryOnConnectionFailure(true): Recovers from transient Wi-Fi packet drops.
    // - Application interceptor: Injects standard headers cleanly before connection setup.
    private val baseClient: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
        .proxy(Proxy.NO_PROXY)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Connection", "close")
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:130.0) Gecko/130.0 Firefox/130.0")
                .header("Accept-Language", "en-US,en;q=0.5")
                .build()
            chain.proceed(request)
        }
        .build()

    private fun getClient(timeoutMs: Long): OkHttpClient {
        val builder = baseClient.newBuilder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)

        wifiSocketFactory?.let { factory ->
            builder.socketFactory(ResilientSocketFactory(factory))
        }
        return builder.build()
    }

    private fun getTimestamp(): Long = System.currentTimeMillis()

    /**
     * Fast gateway check to determine if portal is reachable.
     */
    suspend fun isPortalOnline(): Boolean = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url(probeUrl)
                .get()
                .build()
            val (code, ok) = getClient(3500).newCall(request).execute().use { response ->
                Pair(response.code, response.code in 200..499)
            }
            val latency = System.currentTimeMillis() - start
            AppLogger.portal(TAG, "isPortalOnline probe: reachable=$ok (HTTP $code, latency=${latency}ms, url=$probeUrl)")
            ok
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            AppLogger.portal(TAG, "isPortalOnline probe unreachable: ${e.message} (latency=${latency}ms, url=$probeUrl)")
            false
        }
    }

    /**
     * Checks if current session is alive.
     * Portal responds with <ack>ack</ack> in ~10-15ms when live,
     * or drops/hangs the connection when logged out.
     * Includes a quick jitter retry to avoid false session drops on congested Wi-Fi.
     */
    suspend fun checkLive(username: String, retryOnFail: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        suspend fun attempt(attemptNum: Int): Boolean {
            val start = System.currentTimeMillis()
            return try {
                val url = "$liveUrl?mode=192&username=$username&a=${getTimestamp()}&producttype=0"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                getClient(4500).newCall(request).execute().use { response ->
                    val latency = System.currentTimeMillis() - start
                    val body = response.body?.string() ?: return@use false
                    val parsed = parseXml(body)
                    val ack = parsed["ack"]?.trim()?.lowercase() ?: ""
                    val status = parsed["status"]?.trim()?.lowercase() ?: ""
                    val live = ack == "ack" || status.contains("live") || status.contains("ok")
                    AppLogger.portal(TAG, "checkLive(user=$username, attempt=$attemptNum): isLive=$live, ack='$ack', status='$status' (latency=${latency}ms)")
                    live
                }
            } catch (e: Exception) {
                val latency = System.currentTimeMillis() - start
                AppLogger.w(TAG, "checkLive(user=$username, attempt=$attemptNum) failed: ${e.message} (latency=${latency}ms)")
                false
            }
        }

        val first = attempt(1)
        if (first || !retryOnFail) return@withContext first

        // Wi-Fi jitter retry: wait 500ms and try once more before reporting session dropped
        delay(500L)
        attempt(2)
    }

    /**
     * Verifies whether real outbound HTTP traffic routes to the internet without captive interception.
     * Returns true if HTTP 204 No Content is returned by Google's connectivity check.
     */
    suspend fun verifyInternetConnectivity(): Boolean = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url("http://connectivitycheck.gstatic.com/generate_204")
                .get()
                .build()
            val code = getClient(3000).newCall(request).execute().use { response ->
                response.code
            }
            val latency = System.currentTimeMillis() - start
            val ok = code == 204
            AppLogger.portal(TAG, "verifyInternetConnectivity probe: result=$ok (HTTP $code, latency=${latency}ms)")
            ok
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            AppLogger.d(TAG, "verifyInternetConnectivity probe error: ${e.message} (latency=${latency}ms)")
            false
        }
    }

    /**
     * Logs in with given credentials.
     * Returns Result.success with display message or Result.failure with error message.
     */
    suspend fun login(username: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        loginInternal(username, password, isRetryAfterStaleClear = false)
    }

    private suspend fun loginInternal(username: String, password: String, isRetryAfterStaleClear: Boolean): Result<String> {
        val start = System.currentTimeMillis()
        AppLogger.portal(TAG, ">>> Portal Login Request (staleRetry=$isRetryAfterStaleClear): user=$username, target=$loginUrl")
        return try {
            val formBody = FormBody.Builder()
                .add("mode", "191")
                .add("username", username)
                .add("password", password)
                .add("a", getTimestamp().toString())
                .add("producttype", "0")
                .build()

            val request = Request.Builder()
                .url(loginUrl)
                .post(formBody)
                .build()

            getClient(8000).newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                val body = response.body?.string() ?: ""
                val parsed = parseXml(body)
                val status = (parsed["status"] ?: "").trim().uppercase()
                val message = (parsed["message"] ?: "").trim()

                AppLogger.portal(TAG, "<<< Portal Login Response: status=$status, message='$message' (HTTP ${response.code}, latency=${latency}ms, rawXml='$body')")

                val isLive = status == "LIVE" ||
                    message.contains("signed in", ignoreCase = true) ||
                    message.contains("you are signed in", ignoreCase = true)

                if (isLive) {
                    val successMsg = if (message.isNotEmpty()) message else "Signed in as $username"
                    AppLogger.portal(TAG, "Login SUCCESS for $username: $successMsg (latency=${latency}ms)")
                    Result.success(successMsg)
                } else {
                    // Check for Cyberoam concurrent login limit / stale session on old AP
                    if (!isRetryAfterStaleClear && (message.contains("limit", ignoreCase = true) || message.contains("maximum", ignoreCase = true))) {
                        AppLogger.roam(TAG, "Cyberoam Maximum Login Limit encountered for $username. Auto-evicting stale session on previous AP via logout...")
                        logout(username)
                        delay(600L)
                        AppLogger.roam(TAG, "Retrying portal login for $username following stale session eviction...")
                        return loginInternal(username, password, isRetryAfterStaleClear = true)
                    }

                    val errorReason = when {
                        message.isNotEmpty() -> message
                        status == "LOGIN" -> "Login failed. Check credentials or concurrent session limit."
                        status.isNotEmpty() -> "Login failed ($status)"
                        else -> "Unexpected response from portal"
                    }
                    AppLogger.w(TAG, "Login REJECTED for $username: reason='$errorReason', status='$status', raw='$body'")
                    Result.failure(Exception(errorReason))
                }
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            AppLogger.e(TAG, "Login EXCEPTION for $username: ${e.message} (latency=${latency}ms)", e)
            val friendlyMsg = when {
                e.message?.contains("EPERM", ignoreCase = true) == true ->
                    "Portal unreachable: VPN or system policy blocked direct socket."
                e.message?.contains("timed out", ignoreCase = true) == true ->
                    "Portal unreachable: Gateway timed out after ${latency}ms. Check if you are on PESU Wi-Fi."
                else -> "Portal unreachable (${e.localizedMessage ?: "network error"})"
            }
            Result.failure(Exception(friendlyMsg, e))
        }
    }

    /**
     * Logs out the user session.
     */
    suspend fun logout(username: String): Result<String> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        AppLogger.portal(TAG, ">>> Portal Logout Request: user=$username, target=$logoutUrl")
        try {
            val formBody = FormBody.Builder()
                .add("mode", "193")
                .add("username", username)
                .add("a", getTimestamp().toString())
                .add("producttype", "0")
                .build()

            val request = Request.Builder()
                .url(logoutUrl)
                .post(formBody)
                .build()

            getClient(8000).newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                val body = response.body?.string() ?: ""
                val parsed = parseXml(body)
                val message = parsed["message"]?.trim()
                    ?: parsed["logoutmessage"]?.trim()
                    ?: "Signed out successfully"
                AppLogger.portal(TAG, "<<< Portal Logout Response: message='$message' (HTTP ${response.code}, latency=${latency}ms, raw='$body')")
                Result.success(message)
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            AppLogger.e(TAG, "Logout EXCEPTION for $username: ${e.message} (latency=${latency}ms)", e)
            AppLogger.e(TAG, "Logout failed: ${e.message}", e)
            Result.failure(Exception("Portal unreachable (${e.localizedMessage ?: "network error"})", e))
        }
    }

    /**
     * Lightweight XML parser using XmlPullParser with robust CDATA and multiline support.
     */
    fun parseXml(xmlContent: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (xmlContent.isBlank()) return result

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlContent))
            var eventType = parser.eventType
            var currentTag = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name.lowercase()
                    }
                    XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                        if (currentTag.isNotEmpty()) {
                            val text = parser.text ?: ""
                            result[currentTag] = (result[currentTag] ?: "") + text
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            // Fallback regex extraction if XML is malformed or parser fails
            val tags = listOf("status", "message", "ack", "logoutmessage", "state")
            for (tag in tags) {
                val regex = "<$tag>(?:<!\\[CDATA\\[)?([\\s\\S]*?)(?:\\]\\]>)?</$tag>".toRegex(RegexOption.IGNORE_CASE)
                val match = regex.find(xmlContent)
                if (match != null) {
                    result[tag] = match.groupValues[1].trim()
                }
            }
        }

        return result.mapValues { (_, value) ->
            cleanMessage(value)
        }
    }

    fun cleanMessage(raw: String): String {
        return raw.replace("<![CDATA[", "")
            .replace("]]>", "")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }
}
