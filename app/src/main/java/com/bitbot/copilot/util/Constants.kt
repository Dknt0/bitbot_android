package com.bitbot.copilot.util

object Constants {
    const val DEFAULT_HOST = "192.168.123.161"
    const val DEFAULT_PORT = 12888

    object Preferences {
        const val HOST = "host"
        const val PORT = "port"
        const val AUTO_CONNECT = "auto_connect"
    }

    /** DataStore keys for configurable max velocity per policy mode */
    object VelocityPrefs {
        const val STANDING_X = "standing_vel_x"
        const val STANDING_Y = "standing_vel_y"
        const val STANDING_YAW = "standing_vel_yaw"
        const val WALKING_X = "walking_vel_x"
        const val WALKING_Y = "walking_vel_y"
        const val WALKING_YAW = "walking_vel_yaw"
        const val ROBUST_X = "robust_vel_x"
        const val ROBUST_Y = "robust_vel_y"
        const val ROBUST_YAW = "robust_vel_yaw"
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
     * Policy modes with default max velocity values (symmetric).
     * Each axis has a single max value used as both positive and negative limit.
     */
    enum class PolicyMode(
        val label: String,
        val defaultVelX: Double,
        val defaultVelY: Double,
        val defaultVelYaw: Double
    ) {
        STANDING(label = "Standing", defaultVelX = 4.0, defaultVelY = 1.0, defaultVelYaw = 3.0),
        WALKING(label = "Walking", defaultVelX = 0.6, defaultVelY = 0.0, defaultVelYaw = 1.0),
        ROBUST(label = "Robust", defaultVelX = 1.5, defaultVelY = 0.0, defaultVelYaw = 0.6)
    }

    /** Scale velocity input [-1, 1] by max value (symmetric). */
    fun scaleVelocity(input: Float, maxVel: Double): Double {
        val result = maxVel * input
        return if (result == 0.0) 0.0 else result
    }
}
