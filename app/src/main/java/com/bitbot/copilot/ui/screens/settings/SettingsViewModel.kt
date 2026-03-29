package com.bitbot.copilot.ui.screens.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitbot.copilot.util.Constants
import com.bitbot.copilot.util.Constants.PolicyMode
import com.bitbot.copilot.util.Constants.VelocityPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val host: String = Constants.DEFAULT_HOST,
    val port: Int = Constants.DEFAULT_PORT,
    val autoConnect: Boolean = false,
    val isSaved: Boolean = false,
    // Standing velocity limits
    val standingVelX: Double = PolicyMode.STANDING.defaultVelX,
    val standingVelY: Double = PolicyMode.STANDING.defaultVelY,
    val standingVelYaw: Double = PolicyMode.STANDING.defaultVelYaw,
    // Walking velocity limits
    val walkingVelX: Double = PolicyMode.WALKING.defaultVelX,
    val walkingVelY: Double = PolicyMode.WALKING.defaultVelY,
    val walkingVelYaw: Double = PolicyMode.WALKING.defaultVelYaw,
    // Robust velocity limits
    val robustVelX: Double = PolicyMode.ROBUST.defaultVelX,
    val robustVelY: Double = PolicyMode.ROBUST.defaultVelY,
    val robustVelYaw: Double = PolicyMode.ROBUST.defaultVelYaw
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            _uiState.value = SettingsUiState(
                host = prefs[HOST_KEY] ?: Constants.DEFAULT_HOST,
                port = prefs[PORT_KEY] ?: Constants.DEFAULT_PORT,
                autoConnect = prefs[AUTO_CONNECT_KEY] ?: false,
                standingVelX = prefs[K_STANDING_X] ?: PolicyMode.STANDING.defaultVelX,
                standingVelY = prefs[K_STANDING_Y] ?: PolicyMode.STANDING.defaultVelY,
                standingVelYaw = prefs[K_STANDING_YAW] ?: PolicyMode.STANDING.defaultVelYaw,
                walkingVelX = prefs[K_WALKING_X] ?: PolicyMode.WALKING.defaultVelX,
                walkingVelY = prefs[K_WALKING_Y] ?: PolicyMode.WALKING.defaultVelY,
                walkingVelYaw = prefs[K_WALKING_YAW] ?: PolicyMode.WALKING.defaultVelYaw,
                robustVelX = prefs[K_ROBUST_X] ?: PolicyMode.ROBUST.defaultVelX,
                robustVelY = prefs[K_ROBUST_Y] ?: PolicyMode.ROBUST.defaultVelY,
                robustVelYaw = prefs[K_ROBUST_YAW] ?: PolicyMode.ROBUST.defaultVelYaw
            )
        }
    }

    fun updateHost(host: String) { _uiState.value = _uiState.value.copy(host = host, isSaved = false) }
    fun updatePort(port: Int) { _uiState.value = _uiState.value.copy(port = port, isSaved = false) }
    fun updateAutoConnect(autoConnect: Boolean) { _uiState.value = _uiState.value.copy(autoConnect = autoConnect, isSaved = false) }

    fun updateStandingVelX(v: Double) { _uiState.value = _uiState.value.copy(standingVelX = v, isSaved = false) }
    fun updateStandingVelY(v: Double) { _uiState.value = _uiState.value.copy(standingVelY = v, isSaved = false) }
    fun updateStandingVelYaw(v: Double) { _uiState.value = _uiState.value.copy(standingVelYaw = v, isSaved = false) }
    fun updateWalkingVelX(v: Double) { _uiState.value = _uiState.value.copy(walkingVelX = v, isSaved = false) }
    fun updateWalkingVelY(v: Double) { _uiState.value = _uiState.value.copy(walkingVelY = v, isSaved = false) }
    fun updateWalkingVelYaw(v: Double) { _uiState.value = _uiState.value.copy(walkingVelYaw = v, isSaved = false) }
    fun updateRobustVelX(v: Double) { _uiState.value = _uiState.value.copy(robustVelX = v, isSaved = false) }
    fun updateRobustVelY(v: Double) { _uiState.value = _uiState.value.copy(robustVelY = v, isSaved = false) }
    fun updateRobustVelYaw(v: Double) { _uiState.value = _uiState.value.copy(robustVelYaw = v, isSaved = false) }

    fun saveSettings() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[HOST_KEY] = _uiState.value.host
                prefs[PORT_KEY] = _uiState.value.port
                prefs[AUTO_CONNECT_KEY] = _uiState.value.autoConnect
                prefs[K_STANDING_X] = _uiState.value.standingVelX
                prefs[K_STANDING_Y] = _uiState.value.standingVelY
                prefs[K_STANDING_YAW] = _uiState.value.standingVelYaw
                prefs[K_WALKING_X] = _uiState.value.walkingVelX
                prefs[K_WALKING_Y] = _uiState.value.walkingVelY
                prefs[K_WALKING_YAW] = _uiState.value.walkingVelYaw
                prefs[K_ROBUST_X] = _uiState.value.robustVelX
                prefs[K_ROBUST_Y] = _uiState.value.robustVelY
                prefs[K_ROBUST_YAW] = _uiState.value.robustVelYaw
            }
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    fun resetToDefaults() {
        _uiState.value = SettingsUiState()
    }

    companion object {
        private val HOST_KEY = stringPreferencesKey(Constants.Preferences.HOST)
        private val PORT_KEY = intPreferencesKey(Constants.Preferences.PORT)
        private val AUTO_CONNECT_KEY = booleanPreferencesKey(Constants.Preferences.AUTO_CONNECT)
        private val K_STANDING_X = doublePreferencesKey(VelocityPrefs.STANDING_X)
        private val K_STANDING_Y = doublePreferencesKey(VelocityPrefs.STANDING_Y)
        private val K_STANDING_YAW = doublePreferencesKey(VelocityPrefs.STANDING_YAW)
        private val K_WALKING_X = doublePreferencesKey(VelocityPrefs.WALKING_X)
        private val K_WALKING_Y = doublePreferencesKey(VelocityPrefs.WALKING_Y)
        private val K_WALKING_YAW = doublePreferencesKey(VelocityPrefs.WALKING_YAW)
        private val K_ROBUST_X = doublePreferencesKey(VelocityPrefs.ROBUST_X)
        private val K_ROBUST_Y = doublePreferencesKey(VelocityPrefs.ROBUST_Y)
        private val K_ROBUST_YAW = doublePreferencesKey(VelocityPrefs.ROBUST_YAW)
    }
}
