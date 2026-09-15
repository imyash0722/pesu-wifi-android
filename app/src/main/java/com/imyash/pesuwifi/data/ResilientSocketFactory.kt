package com.imyash.pesuwifi.data

import com.imyash.pesuwifi.util.AppLogger
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * Resilient SocketFactory wrapper that attempts to route through a target network's SocketFactory
 * (such as Wi-Fi Network.socketFactory) and gracefully falls back to the system default SocketFactory
 * if the kernel blocks socket binding (e.g., EPERM / Operation not permitted when a VPN is active).
 */
class ResilientSocketFactory(
    private val delegate: SocketFactory?,
    private val localBindAddress: InetAddress? = null
) : SocketFactory() {

    private val defaultFactory: SocketFactory = getDefault()

    private fun isPermOrBindingFailure(t: Throwable): Boolean {
        if (t is SecurityException) return true
        val msg = t.message?.lowercase() ?: ""
        return msg.contains("eperm") ||
                msg.contains("operation not permitted") ||
                msg.contains("permission denied") ||
                msg.contains("binding socket") ||
                msg.contains("failed to bind")
    }

    private fun createFallbackSocket(): Socket {
        val s = defaultFactory.createSocket()
        if (localBindAddress != null) {
            try {
                s.bind(java.net.InetSocketAddress(localBindAddress, 0))
                AppLogger.d("ResilientSocket", "Bound fallback socket to local Wi-Fi IP $localBindAddress")
            } catch (e: Exception) {
                AppLogger.w("ResilientSocket", "Binding fallback socket to local IP $localBindAddress failed: ${e.message}")
            }
        }
        return s
    }

    override fun createSocket(): Socket {
        val target = delegate ?: return createFallbackSocket()
        return try {
            target.createSocket()
        } catch (t: Throwable) {
            if (isPermOrBindingFailure(t)) {
                AppLogger.w("ResilientSocket", "createSocket() failed with ${t.message}; falling back to direct local socket", t)
                createFallbackSocket()
            } else if (t is IOException) {
                throw t
            } else {
                throw IOException("Socket creation failed", t)
            }
        }
    }

    override fun createSocket(host: String, port: Int): Socket {
        val target = delegate ?: return createFallbackSocket().apply {
            connect(java.net.InetSocketAddress(host, port))
        }
        return try {
            target.createSocket(host, port)
        } catch (t: Throwable) {
            if (isPermOrBindingFailure(t)) {
                AppLogger.w("ResilientSocket", "createSocket($host:$port) failed with ${t.message}; falling back to direct local socket", t)
                createFallbackSocket().apply {
                    connect(java.net.InetSocketAddress(host, port))
                }
            } else if (t is IOException) {
                throw t
            } else {
                throw IOException("Socket creation failed for $host:$port", t)
            }
        }
    }

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket {
        val target = delegate ?: return defaultFactory.createSocket(host, port, localHost, localPort)
        return try {
            target.createSocket(host, port, localHost, localPort)
        } catch (t: Throwable) {
            if (isPermOrBindingFailure(t)) {
                AppLogger.w("ResilientSocket", "createSocket($host:$port, local=$localHost) failed with ${t.message}; falling back to default socket", t)
                defaultFactory.createSocket(host, port, localHost, localPort)
            } else if (t is IOException) {
                throw t
            } else {
                throw IOException("Socket creation failed for $host:$port", t)
            }
        }
    }

    override fun createSocket(host: InetAddress, port: Int): Socket {
        val target = delegate ?: return createFallbackSocket().apply {
            connect(java.net.InetSocketAddress(host, port))
        }
        return try {
            target.createSocket(host, port)
        } catch (t: Throwable) {
            if (isPermOrBindingFailure(t)) {
                AppLogger.w("ResilientSocket", "createSocket($host:$port) failed with ${t.message}; falling back to direct local socket", t)
                createFallbackSocket().apply {
                    connect(java.net.InetSocketAddress(host, port))
                }
            } else if (t is IOException) {
                throw t
            } else {
                throw IOException("Socket creation failed for $host:$port", t)
            }
        }
    }

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket {
        val target = delegate ?: return defaultFactory.createSocket(address, port, localAddress, localPort)
        return try {
            target.createSocket(address, port, localAddress, localPort)
        } catch (t: Throwable) {
            if (isPermOrBindingFailure(t)) {
                AppLogger.w("ResilientSocket", "createSocket($address:$port, local=$localAddress) failed with ${t.message}; falling back to default socket", t)
                defaultFactory.createSocket(address, port, localAddress, localPort)
            } else if (t is IOException) {
                throw t
            } else {
                throw IOException("Socket creation failed for $address:$port", t)
            }
        }
    }
}
