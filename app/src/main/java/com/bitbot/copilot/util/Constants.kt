package com.bitbot.copilot.util

object Constants {
    const val DEFAULT_HOST = "192.168.123.161"
    const val DEFAULT_PORT = 12888

    object Preferences {
        const val HOST = "host"
        const val PORT = "port"
        const val AUTO_CONNECT = "auto_connect"
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

    object ButtonValue {
        const val FIRE = 1L
        const val TOGGLE = 2L
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
