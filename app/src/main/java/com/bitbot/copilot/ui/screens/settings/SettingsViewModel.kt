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
    /** Map of VelocityPrefs key string -> configured value. */
    val velConfig: Map<String, Double> = emptyMap()
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
            val velConfig = mutableMapOf<String, Double>()
            for (mode in PolicyMode.entries) {
                val keys = mode.prefKeys
                velConfig[keys.xPos] = prefs[doublePreferencesKey(keys.xPos)] ?: mode.defaultVelXPos
                velConfig[keys.xNeg] = prefs[doublePreferencesKey(keys.xNeg)] ?: mode.defaultVelXNeg
                velConfig[keys.yPos] = prefs[doublePreferencesKey(keys.yPos)] ?: mode.defaultVelYPos
                velConfig[keys.yNeg] = prefs[doublePreferencesKey(keys.yNeg)] ?: mode.defaultVelYNeg
                velConfig[keys.yawPos] = prefs[doublePreferencesKey(keys.yawPos)] ?: mode.defaultVelYawPos
                velConfig[keys.yawNeg] = prefs[doublePreferencesKey(keys.yawNeg)] ?: mode.defaultVelYawNeg
            }
            _uiState.value = SettingsUiState(
                host = prefs[HOST_KEY] ?: Constants.DEFAULT_HOST,
                port = prefs[PORT_KEY] ?: Constants.DEFAULT_PORT,
                autoConnect = prefs[AUTO_CONNECT_KEY] ?: false,
                velConfig = velConfig
            )
        }
    }

    fun updateHost(host: String) { _uiState.value = _uiState.value.copy(host = host, isSaved = false) }
    fun updatePort(port: Int) { _uiState.value = _uiState.value.copy(port = port, isSaved = false) }
    fun updateAutoConnect(autoConnect: Boolean) { _uiState.value = _uiState.value.copy(autoConnect = autoConnect, isSaved = false) }

    fun updateVelocity(key: String, value: Double) {
        _uiState.value = _uiState.value.copy(
            velConfig = _uiState.value.velConfig + (key to value),
            isSaved = false
        )
    }

    fun saveSettings() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[HOST_KEY] = _uiState.value.host
                prefs[PORT_KEY] = _uiState.value.port
                prefs[AUTO_CONNECT_KEY] = _uiState.value.autoConnect
                for ((key, value) in _uiState.value.velConfig) {
                    prefs[doublePreferencesKey(key)] = value
                }
            }
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    fun resetToDefaults() {
        val velConfig = mutableMapOf<String, Double>()
        for (mode in PolicyMode.entries) {
            val keys = mode.prefKeys
            velConfig[keys.xPos] = mode.defaultVelXPos
            velConfig[keys.xNeg] = mode.defaultVelXNeg
            velConfig[keys.yPos] = mode.defaultVelYPos
            velConfig[keys.yNeg] = mode.defaultVelYNeg
            velConfig[keys.yawPos] = mode.defaultVelYawPos
            velConfig[keys.yawNeg] = mode.defaultVelYawNeg
        }
        _uiState.value = SettingsUiState(velConfig = velConfig)
    }

    companion object {
        private val HOST_KEY = stringPreferencesKey(Constants.Preferences.HOST)
        private val PORT_KEY = intPreferencesKey(Constants.Preferences.PORT)
        private val AUTO_CONNECT_KEY = booleanPreferencesKey(Constants.Preferences.AUTO_CONNECT)
    }
}
