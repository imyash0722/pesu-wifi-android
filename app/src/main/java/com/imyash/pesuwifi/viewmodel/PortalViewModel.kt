package com.imyash.pesuwifi.viewmodel

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.imyash.pesuwifi.data.AccountRepository
import com.imyash.pesuwifi.data.PortalRepository
import com.imyash.pesuwifi.data.PortalStatus
import com.imyash.pesuwifi.data.WifiSuggestionManager
import com.imyash.pesuwifi.service.WifiKeepaliveService
import com.imyash.pesuwifi.util.PermissionManager
import com.imyash.pesuwifi.util.PermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class UiState(
    val status: PortalStatus = PortalStatus(),
    val accounts: Map<String, String> = emptyMap(),
    val activeUser: String? = null,
    val isDaemonRunning: Boolean = false,
    val permissionState: PermissionState = PermissionState(),
    val isLoading: Boolean = false,
    val userMessage: String? = null,
    val errorMessage: String? = null
)

class PortalViewModel(application: Application) : AndroidViewModel(application) {

    private val portalRepository = PortalRepository.getInstance(application)
    private val accountRepository = AccountRepository.getInstance(application)

    private val _isLoading = MutableStateFlow(false)
    private val _permissionState = MutableStateFlow(PermissionManager.getPermissionState(application))
    private val _userMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<UiState> = combine(
        portalRepository.statusFlow,
        accountRepository.accountsFlow,
        accountRepository.activeUserFlow,
        WifiKeepaliveService.isServiceRunning,
        _permissionState,
        _isLoading,
        _userMessage,
        _errorMessage
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        UiState(
            status = args[0] as PortalStatus,
            accounts = args[1] as Map<String, String>,
            activeUser = args[2] as? String,
            isDaemonRunning = args[3] as Boolean,
            permissionState = args[4] as PermissionState,
            isLoading = args[5] as Boolean,
            userMessage = args[6] as? String,
            errorMessage = args[7] as? String
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState(permissionState = PermissionManager.getPermissionState(application))
    )

    init {
        refreshPermissions()
        val context = getApplication<Application>().applicationContext
        WifiKeepaliveService.start(context)
        refresh()
    }

    fun refreshPermissions() {
        val context = getApplication<Application>().applicationContext
        _permissionState.value = PermissionManager.getPermissionState(context)
    }

    fun setBatteryOptimizationOverride(overridden: Boolean = true) {
        val context = getApplication<Application>().applicationContext
        PermissionManager.setBatteryOptimizationOverride(context, overridden)
        refreshPermissions()
    }

    fun checkBatteryOptimization() {
        refreshPermissions()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                portalRepository.refreshStatus()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private var lastLoginAttemptTime = 0L

    fun connectAndLogin() {
        val now = SystemClock.elapsedRealtime()
        if (_isLoading.value || (now - lastLoginAttemptTime) < 1500L) {
            return
        }
        lastLoginAttemptTime = now
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _userMessage.value = "Preparing connection..."
            try {
                val context = getApplication<Application>().applicationContext
                val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager

                // 1. Request to turn on Wi-Fi if disabled
                if (wm?.isWifiEnabled != true) {
                    _userMessage.value = "Requesting to turn on Wi-Fi..."
                    WifiSuggestionManager.requestEnableWifi(context)
                    withTimeoutOrNull(4000L) {
                        while (wm?.isWifiEnabled != true) {
                            delay(300L)
                        }
                    }
                }

                // 2. Scan and prioritize the strongest campus AP
                if (wm?.isWifiEnabled == true) {
                    _userMessage.value = "Scanning for strongest campus AP..."
                    val strongest = WifiSuggestionManager.findStrongestCampusAp(context)
                    if (strongest != null) {
                        _userMessage.value = "Connecting to ${strongest.ssid} (${strongest.level} dBm)..."
                        WifiSuggestionManager.prioritizeTarget(context, strongest.ssid, strongest.bssid)
                    } else {
                        try {
                            @Suppress("DEPRECATION")
                            wm.startScan()
                        } catch (_: Exception) {}
                        WifiSuggestionManager.ensureSuggestionRegistered(context, forceRefresh = true)
                    }

                    // Wait for association if not already on campus Wi-Fi (up to 8s)
                    if (!portalRepository.isCampusNetwork()) {
                        _userMessage.value = "Associating with campus Wi-Fi..."
                        withTimeoutOrNull(8000L) {
                            while (!portalRepository.isCampusNetwork()) {
                                delay(400L)
                            }
                        }
                    }
                }

                // 3. Ensure background keepalive daemon is active (default 120s)
                WifiKeepaliveService.start(context)

                // 4. Authenticate credentials with portal
                _userMessage.value = "Signing in..."
                val result = portalRepository.login()
                if (result.isSuccess) {
                    _userMessage.value = "Signed in as ${accountRepository.getActiveUser()}"
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Login failed"
                    _errorMessage.value = "Login failed: $err"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Connection failed: ${e.message}"
            } finally {
                _isLoading.value = false
                refresh()
            }
        }
    }

    fun login(targetUsername: String? = null) {
        if (targetUsername != null) {
            val now = SystemClock.elapsedRealtime()
            if (_isLoading.value || (now - lastLoginAttemptTime) < 1500L) {
                return
            }
            lastLoginAttemptTime = now
            viewModelScope.launch {
                _isLoading.value = true
                _errorMessage.value = null
                _userMessage.value = "Signing in as '$targetUsername'..."
                try {
                    val context = getApplication<Application>().applicationContext
                    WifiKeepaliveService.start(context)
                    val result = portalRepository.login(targetUsername)
                    if (result.isSuccess) {
                        _userMessage.value = result.getOrNull() ?: "Signed in as $targetUsername"
                    } else {
                        val err = result.exceptionOrNull()?.message ?: "Login failed"
                        _errorMessage.value = "Login failed for '$targetUsername': $err"
                    }
                } finally {
                    _isLoading.value = false
                    refresh()
                }
            }
        } else {
            connectAndLogin()
        }
    }

    fun switchAccount(username: String) {
        val now = SystemClock.elapsedRealtime()
        if (_isLoading.value || (now - lastLoginAttemptTime) < 1500L) {
            return
        }
        lastLoginAttemptTime = now
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _userMessage.value = "Switching to '$username'..."
            try {
                val result = portalRepository.login(username)
                if (result.isSuccess) {
                    _userMessage.value = "Signed in as $username"
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Login failed"
                    _errorMessage.value = "Login failed for '$username': $err"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _userMessage.value = null
            try {
                val result = portalRepository.logout()
                if (result.isSuccess) {
                    _userMessage.value = result.getOrNull() ?: "Signed out"
                } else {
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "Logout failed"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleDaemon() {
        val running = WifiKeepaliveService.isServiceRunning.value
        val context = getApplication<Application>().applicationContext
        if (running) {
            WifiKeepaliveService.stop(context)
            _userMessage.value = "Keepalive daemon stopped"
        } else {
            WifiKeepaliveService.start(context)
            _userMessage.value = "Keepalive daemon started"
        }
    }

    fun saveAccount(username: String, password: String) {
        accountRepository.saveAccount(username, password)
        _userMessage.value = "Account '$username' saved"
        refresh()
    }

    fun deleteAccount(username: String) {
        accountRepository.deleteAccount(username)
        _userMessage.value = "Account '$username' deleted"
        refresh()
    }

    fun selectActiveUser(username: String) {
        accountRepository.setActiveUser(username)
        _userMessage.value = "Active user set to '$username'"
        refresh()
    }

    fun clearMessages() {
        _userMessage.value = null
        _errorMessage.value = null
    }

    fun exportConfig(): String {
        return accountRepository.exportConfigJson()
    }

    fun importConfig(json: String): Boolean {
        val success = accountRepository.importConfigJson(json)
        if (success) {
            _userMessage.value = "Accounts imported successfully"
            refresh()
        } else {
            _errorMessage.value = "Failed to import accounts: Invalid format"
        }
        return success
    }
}
