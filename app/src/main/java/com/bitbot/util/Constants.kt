package com.bitbot.util

object Constants {
    const val DEFAULT_HOST = "127.0.0.1"
    const val DEFAULT_PORT = 12888

    object Preferences {
        const val HOST = "host"
        const val PORT = "port"
        const val AUTO_CONNECT = "auto_connect"
        const val BUTTON_LAYOUT = "button_layout"
        const val PLOT_CHANNELS = "plot_channels"
        const val PLOT_RATE_HZ = "plot_rate_hz"
        const val PLOT_HORIZON_SECONDS = "plot_horizon_seconds"
    }

    /** Realtime plot panel: DataStore-backed settings and rendering constants. */
    object Plot {
        const val DEFAULT_RATE_HZ = 10
        const val DEFAULT_HORIZON_SECONDS = 60
        val RATE_CHOICES_HZ = listOf(10, 20, 50, 100)
        const val MIN_HORIZON_SECONDS = 1
        const val MAX_HORIZON_SECONDS = 60

        /** Curve colors, assigned by selection order (palette cycles). */
        val PALETTE = listOf(
            0xFF2196F3L, 0xFF4CAF50L, 0xFFFFC107L, 0xFFE91E63L, 0xFF00BCD4L,
            0xFFFF9800L, 0xFF9C27B0L, 0xFF009688L, 0xFFF44336L, 0xFF795548L
        )

        fun colorFor(selectionIndex: Int): Long = PALETTE[selectionIndex % PALETTE.size]
    }

    /** DataStore keys for configurable max velocity per policy mode (pos/neg per axis). */
    object VelocityPrefs {
        const val STANDING_X_POS = "standing_vel_x_pos"
        const val STANDING_X_NEG = "standing_vel_x_neg"
        const val STANDING_Y_POS = "standing_vel_y_pos"
        const val STANDING_Y_NEG = "standing_vel_y_neg"
        const val STANDING_YAW_POS = "standing_vel_yaw_pos"
        const val STANDING_YAW_NEG = "standing_vel_yaw_neg"
        const val WALKING_X_POS = "walking_vel_x_pos"
        const val WALKING_X_NEG = "walking_vel_x_neg"
        const val WALKING_Y_POS = "walking_vel_y_pos"
        const val WALKING_Y_NEG = "walking_vel_y_neg"
        const val WALKING_YAW_POS = "walking_vel_yaw_pos"
        const val WALKING_YAW_NEG = "walking_vel_yaw_neg"
        const val ROBUST_X_POS = "robust_vel_x_pos"
        const val ROBUST_X_NEG = "robust_vel_x_neg"
        const val ROBUST_Y_POS = "robust_vel_y_pos"
        const val ROBUST_Y_NEG = "robust_vel_y_neg"
        const val ROBUST_YAW_POS = "robust_vel_yaw_pos"
        const val ROBUST_YAW_NEG = "robust_vel_yaw_neg"

        data class ModeKeys(
            val xPos: String, val xNeg: String,
            val yPos: String, val yNeg: String,
            val yawPos: String, val yawNeg: String
        )

        val STANDING_KEYS = ModeKeys(
            STANDING_X_POS, STANDING_X_NEG,
            STANDING_Y_POS, STANDING_Y_NEG,
            STANDING_YAW_POS, STANDING_YAW_NEG
        )
        val WALKING_KEYS = ModeKeys(
            WALKING_X_POS, WALKING_X_NEG,
            WALKING_Y_POS, WALKING_Y_NEG,
            WALKING_YAW_POS, WALKING_YAW_NEG
        )
        val ROBUST_KEYS = ModeKeys(
            ROBUST_X_POS, ROBUST_X_NEG,
            ROBUST_Y_POS, ROBUST_Y_NEG,
            ROBUST_YAW_POS, ROBUST_YAW_NEG
        )

        /** All 18 key strings for iteration. */
        val ALL_KEYS: List<String> = listOf(
            STANDING_X_POS, STANDING_X_NEG,
            STANDING_Y_POS, STANDING_Y_NEG,
            STANDING_YAW_POS, STANDING_YAW_NEG,
            WALKING_X_POS, WALKING_X_NEG,
            WALKING_Y_POS, WALKING_Y_NEG,
            WALKING_YAW_POS, WALKING_YAW_NEG,
            ROBUST_X_POS, ROBUST_X_NEG,
            ROBUST_Y_POS, ROBUST_Y_NEG,
            ROBUST_YAW_POS, ROBUST_YAW_NEG
        )
    }

    object Events {
        const val STOP = "stop"
        const val POWER_ON = "power_on"
        const val ENABLE_RECORD = "enable_record"
        const val START = "start"
        const val INIT_POSE = "init_pose"
        const val RUN_POLICY = "run_policy"
        const val ENABLE_STANDING_POLICY = "enable_standing_policy"
        const val ENABLE_WARKING_POLICY = "enable_warking_policy"
        const val ENABLE_ROBUST_POLICY = "enable_robust_policy"
        const val NAV_TRIGGER = "nav_trigger"
        const val SET_VEL_X = "set_vel_x"
        const val SET_VEL_Y = "set_vel_y"
        const val SET_VEL_W = "set_vel_w"
    }

    /**
     * Event values follow kernel key semantics (types.hpp):
     * 1 = key Down (press), 2 = key Up (release).
     * Note: "start" only acts on UP, fire-type events act on DOWN.
     */
    object ButtonValue {
        const val DOWN = 1L
        const val UP = 2L
    }

    /**
     * Which server events may become configurable buttons. Allow-list: internal
     * events (policy_switch, velo_*, set_vel_*, nav_trigger, ...) never appear.
     */
    object ButtonEvents {
        val FIXED_EVENTS = setOf(
            Events.STOP,
            Events.POWER_ON,
            Events.ENABLE_RECORD,
            Events.START,
            Events.INIT_POSE,
            Events.RUN_POLICY
        )
        private val POLICY_EVENT_REGEX = Regex("^enable_.+_policy$")

        fun isButtonEvent(name: String): Boolean =
            name in FIXED_EVENTS || POLICY_EVENT_REGEX.matches(name)

        /** Event that switches the app-local policy mode (drives velocity limits). */
        fun policyModeForEvent(name: String): PolicyMode? = when (name) {
            Events.ENABLE_STANDING_POLICY -> PolicyMode.STANDING
            Events.ENABLE_WARKING_POLICY -> PolicyMode.WALKING
            Events.ENABLE_ROBUST_POLICY -> PolicyMode.ROBUST
            else -> null
        }

        fun eventForPolicyMode(mode: PolicyMode): String = when (mode) {
            PolicyMode.STANDING -> Events.ENABLE_STANDING_POLICY
            PolicyMode.WALKING -> Events.ENABLE_WARKING_POLICY
            PolicyMode.ROBUST -> Events.ENABLE_ROBUST_POLICY
        }

        /** Fallback when the server control list could not be fetched. */
        val FALLBACK_EVENTS: List<String> = listOf(
            Events.STOP,
            Events.POWER_ON,
            Events.ENABLE_RECORD,
            Events.START,
            Events.INIT_POSE,
            Events.RUN_POLICY,
            Events.ENABLE_STANDING_POLICY,
            Events.ENABLE_WARKING_POLICY,
            Events.ENABLE_ROBUST_POLICY
        )
    }

    /**
     * Policy modes with separate positive/negative velocity limits per axis.
     * negLimit = magnitude of negative limit (always >= 0).
     * Joystick center = 0 velocity. Positive input scales to posLimit,
     * negative input scales to negLimit.
     *
     * Defaults from bitbot_xbox:
     *   Standing: x[-1, 4]  y[-1, 1]  yaw[-3, 3]
     *   Walking:  x[ 0,.6]  y[ 0, 0]  yaw[-1, 1]
     *   Robust:   x[ 0,1.5] y[ 0, 0]  yaw[-.6,.6]
     */
    enum class PolicyMode(
        val label: String,
        val defaultVelXPos: Double,
        val defaultVelXNeg: Double,
        val defaultVelYPos: Double,
        val defaultVelYNeg: Double,
        val defaultVelYawPos: Double,
        val defaultVelYawNeg: Double,
        val prefKeys: VelocityPrefs.ModeKeys
    ) {
        STANDING(
            label = "Standing",
            defaultVelXPos = 4.0, defaultVelXNeg = 1.0,
            defaultVelYPos = 1.0, defaultVelYNeg = 1.0,
            defaultVelYawPos = 3.0, defaultVelYawNeg = 3.0,
            prefKeys = VelocityPrefs.STANDING_KEYS
        ),
        WALKING(
            label = "Walking",
            defaultVelXPos = 0.6, defaultVelXNeg = 0.0,
            defaultVelYPos = 0.0, defaultVelYNeg = 0.0,
            defaultVelYawPos = 1.0, defaultVelYawNeg = 1.0,
            prefKeys = VelocityPrefs.WALKING_KEYS
        ),
        ROBUST(
            label = "Robust",
            defaultVelXPos = 1.5, defaultVelXNeg = 0.0,
            defaultVelYPos = 0.0, defaultVelYNeg = 0.0,
            defaultVelYawPos = 0.6, defaultVelYawNeg = 0.6,
            prefKeys = VelocityPrefs.ROBUST_KEYS
        )
    }

    /** Scale joystick input [-1, 1] using separate positive/negative limits. */
    fun scaleVelocity(input: Float, posLimit: Double, negLimit: Double): Double {
        if (input == 0f) return 0.0
        val result = if (input > 0f) input * posLimit else input * negLimit
        return if (result == 0.0) 0.0 else result
    }
}
