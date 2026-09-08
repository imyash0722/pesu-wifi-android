package com.imyash.pesuwifi.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AccountRepository(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILENAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val gson = Gson()

    private val _accountsFlow = MutableStateFlow<Map<String, String>>(emptyMap())
    val accountsFlow: StateFlow<Map<String, String>> = _accountsFlow.asStateFlow()

    private val _activeUserFlow = MutableStateFlow<String?>(null)
    val activeUserFlow: StateFlow<String?> = _activeUserFlow.asStateFlow()

    init {
        refreshFlows()
    }

    private fun refreshFlows() {
        val currentAccounts = getAllAccounts()
        val currentUser = getActiveUser()
        _accountsFlow.value = currentAccounts
        _activeUserFlow.value = currentUser
    }

    fun getActiveUser(): String? {
        val user = prefs.getString(KEY_ACTIVE_USER, null)
        val accounts = getAllAccounts()
        if (user != null && accounts.containsKey(user)) {
            return user
        }
        return accounts.keys.firstOrNull()
    }

    fun setActiveUser(username: String) {
        val accounts = getAllAccounts()
        if (accounts.containsKey(username)) {
            prefs.edit().putString(KEY_ACTIVE_USER, username).apply()
            refreshFlows()
        }
    }

    fun getAllAccounts(): Map<String, String> {
        val json = prefs.getString(KEY_ACCOUNTS_JSON, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun getPassword(username: String): String? {
        return getAllAccounts()[username]
    }

    fun getActiveCredentials(): Pair<String, String>? {
        val active = getActiveUser() ?: return null
        val pass = getPassword(active) ?: return null
        return Pair(active, pass)
    }

    fun saveAccount(username: String, password: String) {
        val trimmedUser = username.trim()
        val trimmedPass = password.trim()
        if (trimmedUser.isEmpty() || trimmedPass.isEmpty()) return

        val accounts = getAllAccounts().toMutableMap()
        accounts[trimmedUser] = trimmedPass

        val editor = prefs.edit()
        editor.putString(KEY_ACCOUNTS_JSON, gson.toJson(accounts))
        // If this is the only account or no active user set, set as active
        if (getActiveUser() == null || accounts.size == 1) {
            editor.putString(KEY_ACTIVE_USER, trimmedUser)
        }
        editor.apply()
        refreshFlows()
    }

    fun deleteAccount(username: String) {
        val accounts = getAllAccounts().toMutableMap()
        if (!accounts.containsKey(username)) return

        accounts.remove(username)
        val editor = prefs.edit()
        editor.putString(KEY_ACCOUNTS_JSON, gson.toJson(accounts))

        if (getActiveUser() == username) {
            val nextUser = accounts.keys.firstOrNull()
            if (nextUser != null) {
                editor.putString(KEY_ACTIVE_USER, nextUser)
            } else {
                editor.remove(KEY_ACTIVE_USER)
            }
        }
        editor.apply()
        refreshFlows()
    }

    /**
     * Exports accounts to JSON matching desktop pesu-wifi config.json format.
     */
    fun exportConfigJson(): String {
        val data = mapOf(
            "active_user" to (getActiveUser() ?: ""),
            "accounts" to getAllAccounts()
        )
        return gson.toJson(data)
    }

    /**
     * Imports accounts from JSON formatted like desktop pesu-wifi config.json.
     */
    fun importConfigJson(jsonContent: String): Boolean {
        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = gson.fromJson(jsonContent, type)
            val accountsRaw = data["accounts"] as? Map<*, *> ?: return false
            val activeUser = data["active_user"] as? String

            val accounts = mutableMapOf<String, String>()
            for ((k, v) in accountsRaw) {
                if (k is String && v is String) {
                    accounts[k.trim()] = v.trim()
                }
            }

            if (accounts.isNotEmpty()) {
                val editor = prefs.edit()
                editor.putString(KEY_ACCOUNTS_JSON, gson.toJson(accounts))
                val targetActive = if (activeUser != null && accounts.containsKey(activeUser)) {
                    activeUser
                } else {
                    accounts.keys.first()
                }
                editor.putString(KEY_ACTIVE_USER, targetActive)
                editor.apply()
                refreshFlows()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val PREFS_FILENAME = "pesu_wifi_secure_prefs"
        private const val KEY_ACCOUNTS_JSON = "accounts_json"
        private const val KEY_ACTIVE_USER = "active_user"

        @Volatile
        private var instance: AccountRepository? = null

        fun getInstance(context: Context): AccountRepository {
            return instance ?: synchronized(this) {
                instance ?: AccountRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
