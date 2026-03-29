package com.bitbot.copilot.data.remote.websocket

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class WebSocketState {
    data object Disconnected : WebSocketState()
    data object Connecting : WebSocketState()
    data class Connected(val sessionId: String = "") : WebSocketState()
    data class Error(val message: String) : WebSocketState()
}

/**
 * WebSocket client matching the bitbot_xbox protocol.
 *
 * Message format (TEXT, double-serialized JSON):
 *   {"type":"events","data":"{\"events\":[{\"name\":\"EVENT\",\"value\":N}]}"}
 *
 * Button values: 1 (fire) or 2 (toggle)
 * Velocity values: Double.toBits() (int64 bitcast of double)
 *
 * Reference: /home/dknt/Project/bitbot_xbox/include/js_utils.hpp
 */
@Singleton
class WebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var webSocket: WebSocket? = null
    private var currentUrl: String? = null

    private val _state = MutableStateFlow<WebSocketState>(WebSocketState.Disconnected)
    val state: StateFlow<WebSocketState> = _state.asStateFlow()

    private val _lastMessage = MutableStateFlow("")
    val lastMessage: StateFlow<String> = _lastMessage.asStateFlow()

    private val wsClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .pingInterval(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    fun connect(host: String, port: Int) {
        val url = "ws://$host:$port/console"

        disconnect()
        currentUrl = url
        _state.value = WebSocketState.Connecting

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected to $url")
                webSocket.send("""{"type":"request_data","data":""}""")
                _state.value = WebSocketState.Connected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                _lastMessage.value = text
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                this@WebSocketClient.webSocket = null
                currentUrl = null
                _state.value = WebSocketState.Disconnected
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                this@WebSocketClient.webSocket = null
                currentUrl = null
                _state.value = WebSocketState.Disconnected
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                this@WebSocketClient.webSocket = null
                currentUrl = null
                _state.value = WebSocketState.Error(t.message ?: "Connection failed")
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
        currentUrl = null
        _state.value = WebSocketState.Disconnected
    }

    /**
     * Send a button event (value 1 or 2).
     * Format: {"type":"events","data":"{\"events\":[{\"name\":\"NAME\",\"value\":N}]}"}
     */
    fun sendButtonEvent(name: String, value: Long) {
        val ws = webSocket ?: return
        if (_state.value !is WebSocketState.Connected) return

        val inner = """{"events":[{"name":"$name","value":$value}]}"""
        val outer = """{"type":"events","data":"${inner.replace("\"", "\\\"")}"}"""
        ws.send(outer)
    }

    /**
     * Send a velocity event (value is double bitcast to int64).
     * Format: {"data":"{\"events\":[{\"name\":\"NAME\",\"value\":INT64}]}", "type":"events"}
     */
    fun sendVelocityEvent(name: String, value: Double) {
        val ws = webSocket ?: return
        if (_state.value !is WebSocketState.Connected) return

        val int64Value = value.toBits()
        val inner = """{"events":[{"name":"$name","value":$int64Value}]}"""
        val outer = """{"data":"${inner.replace("\"", "\\\"")}","type":"events"}"""
        ws.send(outer)
    }

    /**
     * Send multiple velocity events at once.
     */
    fun sendVelocityEvents(events: List<Pair<String, Double>>) {
        val ws = webSocket ?: return
        if (_state.value !is WebSocketState.Connected) return

        val eventsJson = events.joinToString(",") { (name, value) ->
            """{"name":"$name","value":${value.toBits()}}"""
        }
        val inner = """{"events":[$eventsJson]}"""
        val outer = """{"data":"${inner.replace("\"", "\\\"")}","type":"events"}"""
        ws.send(outer)
    }

    companion object {
        private const val TAG = "WebSocketClient"
    }
}
