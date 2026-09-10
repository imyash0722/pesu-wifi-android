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
    private const val PORTAL_BASE = "http://192.168.254.1:8090"
    private const val LOGIN_URL = "$PORTAL_BASE/login.xml"
    private const val LOGOUT_URL = "$PORTAL_BASE/logout.xml"
    private const val LIVE_URL = "$PORTAL_BASE/live"

    @Volatile
    private var wifiSocketFactory: SocketFactory? = null

    /**
     * Binds OkHttp calls to the Wi-Fi network interface's SocketFactory so that
     * campus portal requests bypass Mobile Data / Cellular routing even when Mobile Data is active.
     */
    fun setWifiSocketFactory(factory: SocketFactory?) {
        wifiSocketFactory = factory
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
        try {
            val request = Request.Builder()
                .url("$PORTAL_BASE/httpclient.html")
                .get()
                .build()
            val ok = getClient(3500).newCall(request).execute().use { response ->
                response.code in 200..499
            }
            Log.d(TAG, "isPortalOnline probe: $ok")
            AppLogger.d(TAG, "isPortalOnline probe: $ok")
            ok
        } catch (e: Exception) {
            Log.w(TAG, "isPortalOnline probe failed: ${e.message}")
            AppLogger.d(TAG, "isPortalOnline probe unreachable: ${e.message}")
            if (e.message?.contains("EPERM", ignoreCase = true) == true) {
                wifiSocketFactory = null
            }
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
        suspend fun attempt(): Boolean {
            return try {
                val url = "$LIVE_URL?mode=192&username=$username&a=${getTimestamp()}&producttype=0"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                getClient(4500).newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: return@use false
                    val parsed = parseXml(body)
                    val ack = parsed["ack"]?.trim()?.lowercase() ?: ""
                    val status = parsed["status"]?.trim()?.lowercase() ?: ""
                    val live = ack == "ack" || status.contains("live") || status.contains("ok")
                    Log.d(TAG, "checkLive for $username: live=$live (ack='$ack', status='$status')")
                    AppLogger.d(TAG, "checkLive($username): isLive=$live, ack=$ack, status=$status")
                    live
                }
            } catch (e: Exception) {
                Log.w(TAG, "checkLive attempt failed for $username: ${e.message}")
                AppLogger.d(TAG, "checkLive($username) failed: ${e.message}")
                false
            }
        }

        val first = attempt()
        if (first || !retryOnFail) return@withContext first

        // Wi-Fi jitter retry: wait 500ms and try once more before reporting session dropped
        delay(500L)
        attempt()
    }

    /**
     * Logs in with given credentials.
     * Returns Result.success with display message or Result.failure with error message.
     */
    suspend fun login(username: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        AppLogger.i(TAG, "Attempting portal login for user: $username")
        try {
            Log.i(TAG, "Attempting portal login for user: $username")
            val formBody = FormBody.Builder()
                .add("mode", "191")
                .add("username", username)
                .add("password", password)
                .add("a", getTimestamp().toString())
                .add("producttype", "0")
                .build()

            val request = Request.Builder()
                .url(LOGIN_URL)
                .post(formBody)
                .build()

            getClient(8000).newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                Log.d(TAG, "Login raw response: $body")
                val parsed = parseXml(body)
                val status = (parsed["status"] ?: "").trim().uppercase()
                val message = (parsed["message"] ?: "").trim()

                AppLogger.i(TAG, "Login response: status=$status, message='$message'")

                val isLive = status == "LIVE" ||
                    message.contains("signed in", ignoreCase = true) ||
                    message.contains("you are signed in", ignoreCase = true)

                if (isLive) {
                    val successMsg = if (message.isNotEmpty()) message else "Signed in as $username"
                    Log.i(TAG, "Login success: $successMsg")
                    AppLogger.i(TAG, "Login success for $username: $successMsg")
                    Result.success(successMsg)
                } else {
                    val errorReason = when {
                        message.isNotEmpty() -> message
                        status == "LOGIN" -> "Login failed. Check credentials or concurrent session limit."
                        status.isNotEmpty() -> "Login failed ($status)"
                        else -> "Unexpected response from portal"
                    }
                    Log.e(TAG, "Login failed: status='$status', message='$message', errorReason='$errorReason'")
                    AppLogger.w(TAG, "Login rejected for $username: $errorReason")
                    Result.failure(Exception(errorReason))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login network exception: ${e.message}", e)
            AppLogger.e(TAG, "Login failed: ${e.message}", e)
            if (e.message?.contains("EPERM", ignoreCase = true) == true) {
                wifiSocketFactory = null
            }
            val friendlyMsg = when {
                e.message?.contains("EPERM", ignoreCase = true) == true ->
                    "Portal unreachable: VPN or system policy blocked direct socket. Falling back to default network."
                e.message?.contains("timed out", ignoreCase = true) == true ->
                    "Portal unreachable: Gateway timed out. Check if you are on PESU Wi-Fi."
                else -> "Portal unreachable (${e.localizedMessage ?: "network error"})"
            }
            Result.failure(Exception(friendlyMsg, e))
        }
    }

    /**
     * Logs out the user session.
     */
    suspend fun logout(username: String): Result<String> = withContext(Dispatchers.IO) {
        AppLogger.i(TAG, "Attempting portal logout for user: $username")
        try {
            Log.i(TAG, "Attempting portal logout for user: $username")
            val formBody = FormBody.Builder()
                .add("mode", "193")
                .add("username", username)
                .add("a", getTimestamp().toString())
                .add("producttype", "0")
                .build()

            val request = Request.Builder()
                .url(LOGOUT_URL)
                .post(formBody)
                .build()

            getClient(8000).newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                Log.d(TAG, "Logout raw response: $body")
                val parsed = parseXml(body)
                val message = parsed["message"]?.trim()
                    ?: parsed["logoutmessage"]?.trim()
                    ?: "Signed out successfully"
                Log.i(TAG, "Logout success: $message")
                AppLogger.i(TAG, "Logout success: $message")
                Result.success(message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Logout network exception: ${e.message}", e)
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
