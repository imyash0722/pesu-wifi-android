package com.imyash.pesuwifi.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

object PortalApi {
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
            builder.socketFactory(factory)
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
                .url(PORTAL_BASE)
                .get()
                .build()
            getClient(4000).newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
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
                    ack == "ack" || status.contains("live") || status.contains("ok")
                }
            } catch (e: Exception) {
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
        try {
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
                val parsed = parseXml(body)
                val status = (parsed["status"] ?: "").trim().uppercase()
                val message = (parsed["message"] ?: "").trim()

                if (status == "LIVE") {
                    Result.success("Signed in as $username")
                } else if (message.contains("signed in", ignoreCase = true)) {
                    Result.success("Signed in as $username")
                } else if (message.contains("failed", ignoreCase = true) || message.contains("invalid", ignoreCase = true)) {
                    Result.failure(Exception(if (message.isNotEmpty()) message else "Login failed: Invalid credentials"))
                } else if (status.isNotEmpty()) {
                    Result.success("Signed in as $username")
                } else {
                    Result.failure(Exception(if (message.isNotEmpty()) message else "Unexpected response from portal"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Portal unreachable (${e.localizedMessage ?: "network error"})", e))
        }
    }

    /**
     * Logs out the user session.
     */
    suspend fun logout(username: String): Result<String> = withContext(Dispatchers.IO) {
        try {
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
                val parsed = parseXml(body)
                val message = parsed["message"]?.trim() ?: "Signed out successfully"
                Result.success(message)
            }
        } catch (e: Exception) {
            Result.failure(Exception("Portal unreachable (${e.localizedMessage ?: "network error"})", e))
        }
    }

    /**
     * Lightweight XML parser using XmlPullParser.
     */
    fun parseXml(xmlContent: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (xmlContent.isBlank()) return result

        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xmlContent))
            var eventType = parser.eventType
            var currentTag = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name.lowercase()
                    }
                    XmlPullParser.TEXT -> {
                        if (currentTag.isNotEmpty()) {
                            val text = parser.text.trim()
                            if (text.isNotEmpty()) {
                                result[currentTag] = text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            // Fallback simple regex extraction if XML is malformed
            val tags = listOf("status", "message", "ack", "logoutmessage", "state")
            for (tag in tags) {
                val regex = "<$tag>([^<]*)</$tag>".toRegex(RegexOption.IGNORE_CASE)
                val match = regex.find(xmlContent)
                if (match != null) {
                    result[tag] = match.groupValues[1].trim()
                }
            }
        }
        return result
    }
}
