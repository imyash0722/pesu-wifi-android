package com.imyash.pesuwifi.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.imyash.pesuwifi.data.AccountRepository
import com.imyash.pesuwifi.data.PortalRepository
import com.imyash.pesuwifi.data.PortalStatus
import com.imyash.pesuwifi.service.WifiKeepaliveService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val status: PortalStatus = PortalStatus(),
    val accounts: Map<String, String> = emptyMap(),
    val activeUser: String? = null,
    val isDaemonRunning: Boolean = false,
    val isLoading: Boolean = false,
    val userMessage: String? = null,
    val errorMessage: String? = null
)

class PortalViewModel(application: Application) : AndroidViewModel(application) {

    private val portalRepository = PortalRepository.getInstance(application)
    private val accountRepository = AccountRepository.getInstance(application)

    private val _isLoading = MutableStateFlow(false)
    private val _userMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<UiState> = combine(
        portalRepository.statusFlow,
        accountRepository.accountsFlow,
        accountRepository.activeUserFlow,
        WifiKeepaliveService.isServiceRunning,
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
            isLoading = args[4] as Boolean,
            userMessage = args[5] as? String,
            errorMessage = args[6] as? String
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState()
    )

    init {
        refresh()
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

    fun login(targetUsername: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _userMessage.value = null
            try {
                val result = portalRepository.login(targetUsername)
                if (result.isSuccess) {
                    _userMessage.value = result.getOrNull() ?: "Signed in successfully"
                } else {
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "Login failed"
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
