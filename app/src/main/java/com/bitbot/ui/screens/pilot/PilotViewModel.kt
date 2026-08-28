package com.bitbot.ui.screens.pilot

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitbot.data.model.ButtonConfig
import com.bitbot.data.model.ButtonLayoutCodec
import com.bitbot.data.model.ButtonLayouts
import com.bitbot.data.model.ConnectionState
import com.bitbot.data.repository.RobotRepository
import com.bitbot.util.Constants
import com.bitbot.util.Constants.ButtonValue
import com.bitbot.util.Constants.ButtonEvents
import com.bitbot.util.Constants.Events
import com.bitbot.util.Constants.PolicyMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    val velX: Double = 0.0,
    val velY: Double = 0.0,
    val velW: Double = 0.0,
    val buttons: List<ButtonConfig> = emptyList()
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
    private var isPanelActive = false

    // Cached velocity config from DataStore
    private var velXPos: Double = PolicyMode.STANDING.defaultVelXPos
    private var velXNeg: Double = PolicyMode.STANDING.defaultVelXNeg
    private var velYPos: Double = PolicyMode.STANDING.defaultVelYPos
    private var velYNeg: Double = PolicyMode.STANDING.defaultVelYNeg
    private var velYawPos: Double = PolicyMode.STANDING.defaultVelYawPos
    private var velYawNeg: Double = PolicyMode.STANDING.defaultVelYawNeg

    init {
        loadVelocityConfig()
        observeButtonLayout()
        // Velocity loop starts when the Pilot panel becomes visible
        // (see setPanelActive, driven by PilotScreen's lifecycle).
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

    /** Saved layout from DataStore; falls back to the default layout until events are known. */
    private fun observeButtonLayout() {
        viewModelScope.launch {
            combine(
                dataStore.data,
                repository.eventListVersion
            ) { prefs, _ -> prefs[stringPreferencesKey(Constants.Preferences.BUTTON_LAYOUT)] }
                .collect { raw ->
                    val buttons = raw?.let { ButtonLayoutCodec.decode(it) }
                        ?: ButtonLayouts.defaultLayout(repository.availableEvents())
                    _uiState.value = _uiState.value.copy(buttons = buttons)
                }
        }
    }

    /**
     * The velocity loop publishes commands only while the Pilot panel is
     * visible — the ViewModel outlives panel switches (nav-entry scoped), so
     * Data/Plot panels must not keep streaming set_vel events. On deactivate
     * one zero-velocity batch is sent so the robot doesn't hold the last
     * commanded velocity.
     */
    fun setPanelActive(active: Boolean) {
        if (active == isPanelActive) return
        isPanelActive = active
        if (active) {
            startVelocityLoop()
        } else {
            velocityJob?.cancel()
            velocityJob = null
            _uiState.value = _uiState.value.copy(
                leftJoystickX = 0f, leftJoystickY = 0f,
                rightJoystickX = 0f, rightJoystickY = 0f,
                velX = 0.0, velY = 0.0, velW = 0.0
            )
            repository.sendVelocityEvents(listOf(
                Events.SET_VEL_X to 0.0,
                Events.SET_VEL_Y to 0.0,
                Events.SET_VEL_W to 0.0
            ))
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

    // --- Configurable Buttons ---
    // Press sends value 1 (key Down), release sends value 2 (key Up) — matches
    // the desktop reference frontend; every event type acts correctly.
    fun onButtonPress(event: String) {
        // Original bitbot_xbox behavior: the power_on button always enables
        // data recording on the robot first.
        if (event == Events.POWER_ON) {
            repository.sendButtonEvent(Events.ENABLE_RECORD, ButtonValue.DOWN)
        }
        repository.sendButtonEvent(event, ButtonValue.DOWN)
        ButtonEvents.policyModeForEvent(event)?.let { mode ->
            _uiState.value = _uiState.value.copy(policyMode = mode)
            refreshVelocityConfig(mode)
        }
    }

    fun onButtonRelease(event: String) {
        repository.sendButtonEvent(event, ButtonValue.UP)
    }

    /** E-STOP: fixed safety control, always sends stop immediately on press. */
    fun onStopPress() {
        repository.sendButtonEvent(Events.STOP, ButtonValue.DOWN)
    }

    fun disconnect() {
        repository.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        velocityJob?.cancel()
    }
}
