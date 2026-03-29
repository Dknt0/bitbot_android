package com.bitbot.copilot

import com.bitbot.copilot.data.model.ControlEvent
import com.bitbot.copilot.data.remote.websocket.WebSocketClient
import com.bitbot.copilot.data.remote.websocket.WebSocketState
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WebSocketClientTest {
    private lateinit var client: WebSocketClient
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Before
    fun setup() {
        val okHttpClient = OkHttpClient.Builder().build()
        client = WebSocketClient(json, okHttpClient)
    }

    @After
    fun tearDown() {
        client.disconnect()
    }

    @Test
    fun `initial state is Disconnected`() {
        assertTrue(client.state.value is WebSocketState.Disconnected)
    }

    @Test
    fun `disconnect does not throw when not connected`() {
        client.disconnect()
        assertTrue(client.state.value is WebSocketState.Disconnected)
    }

    @Test
    fun `sendEvents does not throw when not connected`() {
        client.sendEvents(listOf(ControlEvent("TEST", 1.0)))
        // Should not throw
    }
}
