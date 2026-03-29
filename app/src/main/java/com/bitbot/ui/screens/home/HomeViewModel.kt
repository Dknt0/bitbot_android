package com.bitbot.ui.screens.home

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitbot.data.model.ConnectionState
import com.bitbot.data.repository.RobotRepository
import com.bitbot.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val host: String = Constants.DEFAULT_HOST,
    val port: Int = Constants.DEFAULT_PORT,
    val autoConnect: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val repository: RobotRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.Disconnected)

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            _uiState.value = _uiState.value.copy(
                host = prefs[HOST_KEY] ?: Constants.DEFAULT_HOST,
                port = prefs[PORT_KEY] ?: Constants.DEFAULT_PORT,
                autoConnect = prefs[AUTO_CONNECT_KEY] ?: false
            )

            if (_uiState.value.autoConnect) {
                connect()
            }
        }
    }

    fun updateHost(host: String) {
        _uiState.value = _uiState.value.copy(host = host)
        viewModelScope.launch {
            dataStore.edit { it[HOST_KEY] = host }
        }
    }

    fun updatePort(port: Int) {
        _uiState.value = _uiState.value.copy(port = port)
        viewModelScope.launch {
            dataStore.edit { it[PORT_KEY] = port }
        }
    }

    fun connect() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            repository.configure(_uiState.value.host, _uiState.value.port)
            repository.connect()
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun disconnect() {
        repository.disconnect()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    companion object {
        private val HOST_KEY = stringPreferencesKey("host")
        private val PORT_KEY = intPreferencesKey("port")
        private val AUTO_CONNECT_KEY = booleanPreferencesKey("auto_connect")
    }
}
