package com.bitbot.data.remote.dto

import kotlinx.serialization.Serializable

// Event value in the inner JSON
@Serializable
data class EventValueDto(
    val name: String,
    val value: Long  // int64 for joystick (scaled by 32768), 1/2 for buttons
)

// Inner JSON: {"events":[...]}
@Serializable
data class EventsPayload(
    val events: List<EventValueDto>
)
