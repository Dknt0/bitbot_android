package com.bitbot.data.model

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data class Connecting(val message: String = "Connecting...") : ConnectionState()
    data class Connected(val host: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()

    val isConnected: Boolean
        get() = this is Connected

    val isConnecting: Boolean
        get() = this is Connecting
}
