package com.bitbot.copilot.data.model

data class ControlEvent(
    val name: String,
    val value: Double
) {
    fun toMap(): Map<String, Any> = mapOf(
        "name" to name,
        "value" to value
    )
}

data class ControlCommand(
    val events: List<ControlEvent>
) {
    fun toMap(): Map<String, Any> = mapOf(
        "type" to "events",
        "data" to mapOf(
            "events" to events.map { it.toMap() }
        )
    )
}
