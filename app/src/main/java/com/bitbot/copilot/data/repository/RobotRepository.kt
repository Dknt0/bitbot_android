package com.bitbot.copilot.data.repository

import android.util.Log
import com.bitbot.copilot.data.model.ConnectionState
import com.bitbot.copilot.data.remote.websocket.WebSocketClient
import com.bitbot.copilot.data.remote.websocket.WebSocketState
import com.bitbot.copilot.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RobotRepository @Inject constructor(
    private val webSocketClient: WebSocketClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _lastMessage = MutableStateFlow("")
    val lastMessage: StateFlow<String> = _lastMessage.asStateFlow()

    private var currentHost: String = ""
    private var currentPort: Int = Constants.DEFAULT_PORT

    init {
        scope.launch {
            webSocketClient.state.collect { wsState ->
                _connectionState.value = when (wsState) {
                    is WebSocketState.Disconnected -> ConnectionState.Disconnected
                    is WebSocketState.Connecting -> ConnectionState.Connecting()
                    is WebSocketState.Connected -> ConnectionState.Connected(currentHost)
                    is WebSocketState.Error -> ConnectionState.Error(wsState.message)
                }
            }
        }

        scope.launch {
            webSocketClient.lastMessage.collect { msg ->
                if (msg.isNotEmpty()) {
                    _lastMessage.value = msg
                }
            }
        }
    }

    suspend fun configure(host: String, port: Int): Result<Unit> {
        currentHost = host
        currentPort = port
        return Result.success(Unit)
    }

    fun connect() {
        _connectionState.value = ConnectionState.Connecting()
        webSocketClient.connect(currentHost, currentPort)
    }

    fun disconnect() {
        webSocketClient.disconnect()
        _connectionState.value = ConnectionState.Disconnected
    }

    /** Send a button event: value 1 (fire) or 2 (toggle) */
    fun sendButtonEvent(name: String, value: Long) {
        webSocketClient.sendButtonEvent(name, value)
    }

    /** Send velocity command: value is double, will be bitcast to int64 */
    fun sendVelocityEvent(name: String, value: Double) {
        webSocketClient.sendVelocityEvent(name, value)
    }

    /** Send multiple velocity commands at once */
    fun sendVelocityEvents(events: List<Pair<String, Double>>) {
        webSocketClient.sendVelocityEvents(events)
    }

    companion object {
        private const val TAG = "RobotRepository"
    }
}
