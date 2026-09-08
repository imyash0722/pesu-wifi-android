package com.imyash.pesuwifi.service

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.imyash.pesuwifi.R
import com.imyash.pesuwifi.data.PortalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PesuTileService : TileService() {

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var portalRepository: PortalRepository

    override fun onCreate() {
        super.onCreate()
        portalRepository = PortalRepository.getInstance(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            portalRepository.statusFlow.collectLatest { status ->
                updateTileState(status.isWifiConnected, status.isPortalOnline, status.isLoggedIn, status.activeUsername)
            }
        }
        // Also trigger a background probe
        scope.launch(Dispatchers.IO) {
            portalRepository.refreshStatus()
        }
    }

    override fun onStopListening() {
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val currentStatus = portalRepository.statusFlow.value

        if (!currentStatus.isWifiConnected) {
            Toast.makeText(this, "Connect to Wi-Fi first", Toast.LENGTH_SHORT).show()
            return
        }

        if (!currentStatus.isPortalOnline) {
            Toast.makeText(this, "PESU Portal gateway unreachable", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch(Dispatchers.IO) {
            if (currentStatus.isLoggedIn) {
                portalRepository.logout()
                launch(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Logged out", Toast.LENGTH_SHORT).show()
                }
            } else {
                val result = portalRepository.login()
                launch(Dispatchers.Main) {
                    if (result.isSuccess) {
                        Toast.makeText(applicationContext, result.getOrNull() ?: "Logged in", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(applicationContext, result.exceptionOrNull()?.message ?: "Login failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun updateTileState(
        isWifiConnected: Boolean,
        isPortalOnline: Boolean,
        isLoggedIn: Boolean,
        username: String?
    ) {
        val tile = qsTile ?: return

        tile.icon = Icon.createWithResource(this, R.drawable.ic_wifi)
        tile.label = "PESU WiFi"

        when {
            !isWifiConnected || !isPortalOnline -> {
                tile.state = Tile.STATE_UNAVAILABLE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = if (!isWifiConnected) "No Wi-Fi" else "Offline"
                }
            }
            isLoggedIn -> {
                tile.state = Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = username ?: "Active"
                }
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "Logged Out"
                }
            }
        }

        tile.updateTile()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
