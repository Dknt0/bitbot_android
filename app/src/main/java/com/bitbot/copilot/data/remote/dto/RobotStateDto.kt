package com.bitbot.copilot.data.remote.dto

import kotlinx.serialization.Serializable

// Incoming WebSocket messages have double-serialized data:
// {"type":"monitor_data","data":"{\"data\":[0.0,1.0,...]}"}
// The "data" field is a JSON string, not an object.
@Serializable
data class WebSocketMessageDto(
    val type: String,
    // This can be a string (double-serialized JSON) or null
    val data: String? = null
)

@Serializable
data class MonitorDataPayload(
    val data: List<Double> = emptyList()
)

@Serializable
data class StateItemDto(
    val id: Int,
    val name: String
)

@Serializable
data class StatesListResponseDto(
    val states: List<StateItemDto> = emptyList()
)

@Serializable
data class ControlMappingDto(
    val event: String,
    val kb_key: String = ""
)

// Complex headers response structure
@Serializable
data class HeadersResponseDto(
    val kernel: List<String> = emptyList(),
    val bus: BusHeadersDto? = null,
    val extra: List<String> = emptyList()
)

@Serializable
data class BusHeadersDto(
    val devices: List<DeviceHeadersDto> = emptyList()
)

@Serializable
data class DeviceHeadersDto(
    val name: String,
    val type: String? = null,
    val headers: List<String> = emptyList()
)
