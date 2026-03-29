package com.bitbot.copilot.util

import kotlin.math.sqrt

fun Float.clamp(min: Float, max: Float): Float = this.coerceIn(min, max)

fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)

fun normalizeJoystickValue(x: Float, y: Float, deadzone: Float = 0.1f): Pair<Float, Float> {
    val magnitude = sqrt(x * x + y * y)

    if (magnitude < deadzone) {
        return 0f to 0f
    }

    val scale = (magnitude - deadzone) / (1f - deadzone)
    val normalizedMag = scale.coerceIn(0f, 1f)

    val normalizedX = (x / magnitude) * normalizedMag
    val normalizedY = (y / magnitude) * normalizedMag

    return normalizedX to normalizedY
}

fun joystickToRobotValue(value: Float): Double = value.toDouble()

fun <T> Result<T>.getOrElse(default: T): T = getOrDefault(default)
