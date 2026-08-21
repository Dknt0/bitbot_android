package com.bitbot

import com.bitbot.data.remote.websocket.WebSocketClient
import com.bitbot.data.remote.websocket.WebSocketState
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
        client = WebSocketClient(okHttpClient, json)
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
    fun `sendVelocityEvents does not throw when not connected`() {
        client.sendVelocityEvents(listOf("set_vel_x" to 1.0))
        // Should not throw
    }
}
