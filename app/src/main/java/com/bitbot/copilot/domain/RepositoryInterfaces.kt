package com.bitbot.copilot.domain

import com.bitbot.copilot.data.model.ConnectionState
import kotlinx.coroutines.flow.Flow

interface IRobotRepository {
    val connectionState: Flow<ConnectionState>
    val lastMessage: Flow<String>

    suspend fun configure(host: String, port: Int): Result<Unit>
    fun connect()
    fun disconnect()

    fun sendButtonEvent(name: String, value: Long)
    fun sendVelocityEvent(name: String, value: Double)
    fun sendVelocityEvents(events: List<Pair<String, Double>>)
}
