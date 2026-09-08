package com.imyash.pesuwifi.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class PortalStatus(
    val isWifiConnected: Boolean = false,
    val isPortalOnline: Boolean = false,
    val isLoggedIn: Boolean = false,
    val activeUsername: String? = null,
    val latencyMs: Long? = null,
    val lastCheckedTimestamp: Long = 0,
    val statusMessage: String = "Initializing..."
)

class PortalRepository(
    private val context: Context,
    private val accountRepository: AccountRepository = AccountRepository.getInstance(context),
    private val api: PortalApi = PortalApi
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _statusFlow = MutableStateFlow(PortalStatus())
    val statusFlow: StateFlow<PortalStatus> = _statusFlow.asStateFlow()

    fun isWifiConnected(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    suspend fun refreshStatus(): PortalStatus = withContext(Dispatchers.IO) {
        val wifiConnected = isWifiConnected()
        val activeUser = accountRepository.getActiveUser()

        if (!wifiConnected) {
            val status = PortalStatus(
                isWifiConnected = false,
                isPortalOnline = false,
                isLoggedIn = false,
                activeUsername = activeUser,
                lastCheckedTimestamp = System.currentTimeMillis(),
                statusMessage = "Wi-Fi disconnected"
            )
            _statusFlow.value = status
            return@withContext status
        }

        val start = System.currentTimeMillis()
        val portalUp = api.isPortalOnline()
        val latency = System.currentTimeMillis() - start

        if (!portalUp) {
            val status = PortalStatus(
                isWifiConnected = true,
                isPortalOnline = false,
                isLoggedIn = false,
                activeUsername = activeUser,
                latencyMs = latency,
                lastCheckedTimestamp = System.currentTimeMillis(),
                statusMessage = "Portal gateway unreachable"
            )
            _statusFlow.value = status
            return@withContext status
        }

        val targetUser = activeUser ?: "test"
        val loggedIn = api.checkLive(targetUser)

        val message = if (loggedIn) {
            "Connected as ${activeUser ?: "active session"}"
        } else {
            "Session inactive (logged out)"
        }

        val status = PortalStatus(
            isWifiConnected = true,
            isPortalOnline = true,
            isLoggedIn = loggedIn,
            activeUsername = activeUser,
            latencyMs = latency,
            lastCheckedTimestamp = System.currentTimeMillis(),
            statusMessage = message
        )
        _statusFlow.value = status
        status
    }

    suspend fun login(targetUsername: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val username = targetUsername ?: accountRepository.getActiveUser()
            ?: return@withContext Result.failure(Exception("No account configured. Add an account first."))
        val password = accountRepository.getPassword(username)
            ?: return@withContext Result.failure(Exception("No password saved for '$username'"))

        val result = api.login(username, password)
        if (result.isSuccess) {
            accountRepository.setActiveUser(username)
            refreshStatus()
        }
        result
    }

    suspend fun logout(): Result<String> = withContext(Dispatchers.IO) {
        val username = accountRepository.getActiveUser() ?: "user"
        val result = api.logout(username)
        refreshStatus()
        result
    }

    companion object {
        @Volatile
        private var instance: PortalRepository? = null

        fun getInstance(context: Context): PortalRepository {
            return instance ?: synchronized(this) {
                instance ?: PortalRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
