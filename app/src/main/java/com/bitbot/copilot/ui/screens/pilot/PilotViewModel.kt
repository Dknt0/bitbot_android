package com.bitbot.copilot.ui.screens.pilot

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitbot.copilot.data.model.ConnectionState
import com.bitbot.copilot.data.repository.RobotRepository
import com.bitbot.copilot.util.Constants
import com.bitbot.copilot.util.Constants.ButtonValue
import com.bitbot.copilot.util.Constants.Events
import com.bitbot.copilot.util.Constants.PolicyMode
import com.bitbot.copilot.util.Constants.VelocityPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PilotUiState(
    val leftJoystickX: Float = 0f,
    val leftJoystickY: Float = 0f,
    val rightJoystickX: Float = 0f,
    val rightJoystickY: Float = 0f,
    val policyMode: PolicyMode = PolicyMode.STANDING,
    val isPolicyRunning: Boolean = false
)

@HiltViewModel
class PilotViewModel @Inject constructor(
    private val repository: RobotRepository,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val _uiState = MutableStateFlow(PilotUiState())
    val uiState: StateFlow<PilotUiState> = _uiState.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.Disconnected)

    private var velocityJob: Job? = null

    // Cached velocity config from DataStore
    private var velXMax: Double = PolicyMode.STANDING.defaultVelX
    private var velYMax: Double = PolicyMode.STANDING.defaultVelY
    private var velYawMax: Double = PolicyMode.STANDING.defaultVelYaw

    init {
        loadVelocityConfig()
        startVelocityLoop()
    }

    private fun loadVelocityConfig() {
        viewModelScope.launch {
            val mode = _uiState.value.policyMode
            refreshVelocityConfig(mode)
        }
    }

    private fun refreshVelocityConfig(mode: PolicyMode) {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            val (xKey, yKey, yawKey) = when (mode) {
                PolicyMode.STANDING -> Triple(VelocityPrefs.STANDING_X, VelocityPrefs.STANDING_Y, VelocityPrefs.STANDING_YAW)
                PolicyMode.WALKING -> Triple(VelocityPrefs.WALKING_X, VelocityPrefs.WALKING_Y, VelocityPrefs.WALKING_YAW)
                PolicyMode.ROBUST -> Triple(VelocityPrefs.ROBUST_X, VelocityPrefs.ROBUST_Y, VelocityPrefs.ROBUST_YAW)
            }
            velXMax = prefs[doublePreferencesKey(xKey)] ?: mode.defaultVelX
            velYMax = prefs[doublePreferencesKey(yKey)] ?: mode.defaultVelY
            velYawMax = prefs[doublePreferencesKey(yawKey)] ?: mode.defaultVelYaw
        }
    }

    private fun startVelocityLoop() {
        velocityJob?.cancel()
        velocityJob = viewModelScope.launch {
            while (isActive) {
                val state = _uiState.value

                val velX = Constants.scaleVelocity(-state.rightJoystickY, velXMax)
                val velY = Constants.scaleVelocity(-state.rightJoystickX, velYMax)
                val velW = Constants.scaleVelocity(-state.leftJoystickX, velYawMax)

                repository.sendVelocityEvents(listOf(
                    Events.SET_VEL_X to velX,
                    Events.SET_VEL_Y to velY,
                    Events.SET_VEL_W to velW
                ))

                delay(10) // 100Hz
            }
        }
    }

    // --- Joystick Updates ---
    fun updateLeftJoystick(x: Float, y: Float) {
        _uiState.value = _uiState.value.copy(leftJoystickX = x, leftJoystickY = y)
    }

    fun updateRightJoystick(x: Float, y: Float) {
        _uiState.value = _uiState.value.copy(rightJoystickX = x, rightJoystickY = y)
    }

    // --- Button Actions ---
    fun onPressA() {
        repository.sendButtonEvent(Events.INIT_POSE, ButtonValue.FIRE)
    }

    fun onPressB() {
        repository.sendButtonEvent(Events.START, ButtonValue.FIRE)
    }

    fun onPressX() {
        repository.sendButtonEvent(Events.ENABLE_STANDING_POLICY, ButtonValue.FIRE)
        _uiState.value = _uiState.value.copy(policyMode = PolicyMode.STANDING)
        refreshVelocityConfig(PolicyMode.STANDING)
    }

    fun onPressY() {
        repository.sendButtonEvent(Events.ENABLE_RECORD, ButtonValue.FIRE)
        repository.sendButtonEvent(Events.POWER_ON, ButtonValue.FIRE)
    }

    fun onPressLB() {
        repository.sendButtonEvent(Events.ENABLE_WARKING_POLICY, ButtonValue.FIRE)
        _uiState.value = _uiState.value.copy(policyMode = PolicyMode.WALKING)
        refreshVelocityConfig(PolicyMode.WALKING)
    }

    fun onPressRB() {
        repository.sendButtonEvent(Events.ENABLE_ROBUST_POLICY, ButtonValue.FIRE)
        _uiState.value = _uiState.value.copy(policyMode = PolicyMode.ROBUST)
        refreshVelocityConfig(PolicyMode.ROBUST)
    }

    fun onRightTrigger(value: Float) {
        if (value > 0.9f) {
            repository.sendButtonEvent(Events.STOP, ButtonValue.FIRE)
        }
    }

    fun onLeftTrigger(value: Float) {
        if (value > 0.9f) {
            repository.sendButtonEvent(Events.NAV_TRIGGER, ButtonValue.FIRE)
        }
    }

    fun onRunPolicy() {
        repository.sendButtonEvent(Events.RUN_POLICY, ButtonValue.FIRE)
        _uiState.value = _uiState.value.copy(isPolicyRunning = true)
    }

    fun disconnect() {
        repository.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        velocityJob?.cancel()
    }
}
