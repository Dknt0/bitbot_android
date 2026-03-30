package com.bitbot.data.repository

import android.util.Log
import com.bitbot.data.model.ConnectionState
import com.bitbot.data.remote.api.RobotApi
import com.bitbot.data.remote.dto.HeadersResponseDto
import com.bitbot.data.remote.dto.StatesListResponseDto
import com.bitbot.data.remote.websocket.WebSocketClient
import com.bitbot.data.remote.websocket.WebSocketState
import com.bitbot.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RobotRepository @Inject constructor(
    private val webSocketClient: WebSocketClient,
    private val robotApi: RobotApi
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _lastMessage = MutableStateFlow("")
    val lastMessage: StateFlow<String> = _lastMessage.asStateFlow()

    val monitorData: StateFlow<List<Double>> = webSocketClient.monitorData

    var headers: HeadersResponseDto? = null
        private set
    var statesList: StatesListResponseDto? = null
        private set
    var headersError: String? = null
        private set

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

        // Auto-reconnect when connection drops involuntarily
        scope.launch {
            webSocketClient.state.collect { wsState ->
                if ((wsState is WebSocketState.Disconnected || wsState is WebSocketState.Error)
                    && currentHost.isNotEmpty()
                    && webSocketClient.hasLastUrl
                ) {
                    delay(2000)
                    val stateAfterDelay = webSocketClient.state.value
                    if ((stateAfterDelay is WebSocketState.Disconnected || stateAfterDelay is WebSocketState.Error)
                        && currentHost.isNotEmpty()
                        && webSocketClient.hasLastUrl
                    ) {
                        Log.d(TAG, "Auto-reconnecting to $currentHost:$currentPort")
                        connect()
                    }
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

    suspend fun fetchHeaders() {
        if (currentHost.isEmpty()) {
            Log.e(TAG, "fetchHeaders: currentHost is empty!")
            headersError = "No host configured"
            return
        }
        val baseUrl = "http://$currentHost:$currentPort"
        Log.d(TAG, "fetchHeaders: fetching from $baseUrl")
        val result = withContext(Dispatchers.IO) { robotApi.connectAll(baseUrl) }
        if (result.isFailure) {
            val ex = result.exceptionOrNull()
            val err = ex?.message ?: "${ex?.javaClass?.simpleName ?: "Unknown error"}"
            Log.e(TAG, "fetchHeaders FAILED: $err", ex)
            headersError = "URL=$baseUrl err=$err"
            return
        }
        result.getOrNull()?.let { r ->
            headers = r.headers
            statesList = r.states
            Log.d(TAG, "fetchHeaders OK: kernel=${r.headers?.kernel?.size}, " +
                    "devices=${r.headers?.bus?.devices?.size}, extra=${r.headers?.extra?.size}")
        }
    }

    fun disconnect() {
        webSocketClient.disconnect()
        _connectionState.value = ConnectionState.Disconnected
    }

    fun startDataPolling() = webSocketClient.startDataPolling()
    fun stopDataPolling() = webSocketClient.stopDataPolling()
    fun getWsStateDebug(): String = webSocketClient.state.value.toString()

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
