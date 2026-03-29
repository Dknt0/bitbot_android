package com.bitbot.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RobotState(
    val timestamp: Long = System.currentTimeMillis(),
    val data: Map<String, Double> = emptyMap(),
    val headers: List<String> = emptyList()
) {
    fun getValue(key: String): Double? = data[key]

    fun getFormattedValue(key: String, decimals: Int = 2): String {
        return data[key]?.let { "%.${decimals}f".format(it) } ?: "--"
    }
}
