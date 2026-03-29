package com.bitbot.ui.screens.pilot

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitbot.data.model.ConnectionState
import com.bitbot.data.repository.RobotRepository
import com.bitbot.util.Constants
import com.bitbot.util.Constants.ButtonValue
import com.bitbot.util.Constants.Events
import com.bitbot.util.Constants.PolicyMode
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
    val isPolicyRunning: Boolean = false,
    val velX: Double = 0.0,
    val velY: Double = 0.0,
    val velW: Double = 0.0
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
    private var velXPos: Double = PolicyMode.STANDING.defaultVelXPos
    private var velXNeg: Double = PolicyMode.STANDING.defaultVelXNeg
    private var velYPos: Double = PolicyMode.STANDING.defaultVelYPos
    private var velYNeg: Double = PolicyMode.STANDING.defaultVelYNeg
    private var velYawPos: Double = PolicyMode.STANDING.defaultVelYawPos
    private var velYawNeg: Double = PolicyMode.STANDING.defaultVelYawNeg

    init {
        loadVelocityConfig()
        startVelocityLoop()
    }

    private fun loadVelocityConfig() {
        viewModelScope.launch {
            refreshVelocityConfig(_uiState.value.policyMode)
        }
    }

    private fun refreshVelocityConfig(mode: PolicyMode) {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            val keys = mode.prefKeys
            velXPos = prefs[doublePreferencesKey(keys.xPos)] ?: mode.defaultVelXPos
            velXNeg = prefs[doublePreferencesKey(keys.xNeg)] ?: mode.defaultVelXNeg
            velYPos = prefs[doublePreferencesKey(keys.yPos)] ?: mode.defaultVelYPos
            velYNeg = prefs[doublePreferencesKey(keys.yNeg)] ?: mode.defaultVelYNeg
            velYawPos = prefs[doublePreferencesKey(keys.yawPos)] ?: mode.defaultVelYawPos
            velYawNeg = prefs[doublePreferencesKey(keys.yawNeg)] ?: mode.defaultVelYawNeg
        }
    }

    private fun startVelocityLoop() {
        velocityJob?.cancel()
        velocityJob = viewModelScope.launch {
            while (isActive) {
                val state = _uiState.value

                val velX = Constants.scaleVelocity(-state.rightJoystickY, velXPos, velXNeg)
                val velY = Constants.scaleVelocity(-state.rightJoystickX, velYPos, velYNeg)
                val velW = Constants.scaleVelocity(-state.leftJoystickX, velYawPos, velYawNeg)

                _uiState.value = state.copy(velX = velX, velY = velY, velW = velW)

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
        repository.sendButtonEvent(Events.START, ButtonValue.TOGGLE)
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
