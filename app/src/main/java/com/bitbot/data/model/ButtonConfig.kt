package com.bitbot.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicLong

/**
 * User-configurable control panel button.
 *
 * x / y are the button CENTER in normalized screen coordinates [0, 1] so the
 * layout survives different screen sizes (app is landscape-locked). sizeDp is
 * the button width in dp; height is derived via HEIGHT_RATIO.
 */
@Serializable
data class ButtonConfig(
    val id: Long = nextId(),
    val eventName: String,
    val label: String,
    val x: Float,
    val y: Float,
    val sizeDp: Float = DEFAULT_SIZE_DP,
    val colorARGB: Long = DEFAULT_COLOR
) {
    companion object {
        const val HEIGHT_RATIO = 0.5f
        const val MIN_SIZE_DP = 48f
        const val MAX_SIZE_DP = 180f
        const val DEFAULT_SIZE_DP = 80f
        const val DEFAULT_COLOR = 0xFF2196F3L

        private val idCounter = AtomicLong(System.currentTimeMillis())

        fun nextId(): Long = idCounter.incrementAndGet()
    }
}

/** Palette offered in the button editor. */
object ButtonColors {
    val PALETTE: List<Long> = listOf(
        0xFF4CAF50L, // green
        0xFF2196F3L, // blue
        0xFF9C27B0L, // purple
        0xFFFF9800L, // orange
        0xFFFFC107L, // amber
        0xFFE53935L, // red
        0xFF009688L, // teal
        0xFF607D8BL  // blue grey
    )
}

object ButtonLayouts {

    /** Short display labels for the known events; unknown events get a prettified name. */
    private val KNOWN_LABELS = mapOf(
        "power_on" to "PowerOn",
        "init_pose" to "InitPose",
        "start" to "Start",
        "run_policy" to "Run",
        "stop" to "Stop",
        "enable_record" to "Record",
        "enable_standing_policy" to "Stand",
        "enable_warking_policy" to "Walk",
        "enable_robust_policy" to "Robust"
    )

    fun prettifyLabel(eventName: String): String =
        KNOWN_LABELS[eventName]
            ?: eventName.split('_', ' ').joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }

    /** Default layout mirroring the original hardcoded center panel; only events the server offers are included. */
    fun defaultLayout(availableEvents: Collection<String>): List<ButtonConfig> {
        val entries = listOf(
            Triple("power_on", 0.42f to 0.35f, 0xFF4CAF50L),
            Triple("init_pose", 0.58f to 0.35f, 0xFF9C27B0L),
            Triple("start", 0.42f to 0.47f, 0xFF2196F3L),
            Triple("run_policy", 0.58f to 0.47f, 0xFFFF9800L),
            Triple("enable_standing_policy", 0.42f to 0.60f, 0xFF4CAF50L),
            Triple("enable_warking_policy", 0.50f to 0.60f, 0xFFFFC107L),
            Triple("enable_robust_policy", 0.58f to 0.60f, 0xFF2196F3L)
        )
        return entries.mapNotNull { (event, pos, color) ->
            if (event in availableEvents) {
                ButtonConfig(
                    eventName = event,
                    label = prettifyLabel(event),
                    x = pos.first,
                    y = pos.second,
                    sizeDp = ButtonConfig.DEFAULT_SIZE_DP,
                    colorARGB = color
                )
            } else {
                null
            }
        }
    }

    /** Suggested placement for a newly added button: near center, slightly offset per index. */
    fun placementForNewButton(index: Int): Pair<Float, Float> {
        val dx = (index % 3 - 1) * 0.09f
        val dy = ((index / 3) % 3 - 1) * 0.12f
        return (0.5f + dx) to (0.30f + dy)
    }
}

/** JSON (de)serialization for DataStore persistence; decode failures fall back to null. */
object ButtonLayoutCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(buttons: List<ButtonConfig>): String = json.encodeToString(buttons)

    fun decode(raw: String): List<ButtonConfig>? = runCatching {
        json.decodeFromString<List<ButtonConfig>>(raw)
    }.getOrNull()
}
