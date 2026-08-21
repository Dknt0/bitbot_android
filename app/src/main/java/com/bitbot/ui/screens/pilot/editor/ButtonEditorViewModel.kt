package com.bitbot.ui.screens.pilot.editor

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitbot.data.model.ButtonConfig
import com.bitbot.data.model.ButtonLayoutCodec
import com.bitbot.data.model.ButtonLayouts
import com.bitbot.data.repository.RobotRepository
import com.bitbot.util.Constants
import com.bitbot.util.Constants.ButtonEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ButtonEditorUiState(
    val buttons: List<ButtonConfig> = emptyList(),
    /** Button-able events offered by the server (filtered allow-list). */
    val availableEvents: List<String> = emptyList(),
    val selectedId: Long? = null,
    val isSaved: Boolean = false
)

@HiltViewModel
class ButtonEditorViewModel @Inject constructor(
    private val repository: RobotRepository,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val _uiState = MutableStateFlow(ButtonEditorUiState())
    val uiState: StateFlow<ButtonEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            val saved = prefs[LAYOUT_KEY]?.let { ButtonLayoutCodec.decode(it) }
            val available = availableButtonEvents()
            _uiState.value = ButtonEditorUiState(
                buttons = saved ?: ButtonLayouts.defaultLayout(available),
                availableEvents = available
            )
        }
    }

    private fun availableButtonEvents(): List<String> =
        repository.availableEvents().filter(ButtonEvents::isButtonEvent).distinct().sorted()

    fun select(id: Long?) {
        _uiState.value = _uiState.value.copy(selectedId = id)
    }

    fun addButton(event: String) {
        val state = _uiState.value
        val (x, y) = ButtonLayouts.placementForNewButton(state.buttons.size)
        val config = ButtonConfig(
            eventName = event,
            label = ButtonLayouts.prettifyLabel(event),
            x = x,
            y = y
        )
        _uiState.value = state.copy(
            buttons = state.buttons + config,
            selectedId = config.id,
            isSaved = false
        )
    }

    fun updateLabel(label: String) = updateSelected { it.copy(label = label.take(24)) }

    fun updateX(x: Float) = updateSelected { it.copy(x = x.coerceIn(0f, 1f)) }

    fun updateY(y: Float) = updateSelected { it.copy(y = y.coerceIn(0f, 1f)) }

    fun updateSize(sizeDp: Float) = updateSelected {
        it.copy(sizeDp = sizeDp.coerceIn(ButtonConfig.MIN_SIZE_DP, ButtonConfig.MAX_SIZE_DP))
    }

    fun updateColor(colorARGB: Long) = updateSelected { it.copy(colorARGB = colorARGB) }

    fun removeSelected() {
        val state = _uiState.value
        val id = state.selectedId ?: return
        _uiState.value = state.copy(
            buttons = state.buttons.filterNot { it.id == id },
            selectedId = null,
            isSaved = false
        )
    }

    fun resetToDefault() {
        val state = _uiState.value
        _uiState.value = state.copy(
            buttons = ButtonLayouts.defaultLayout(state.availableEvents),
            selectedId = null,
            isSaved = false
        )
    }

    fun save() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[LAYOUT_KEY] = ButtonLayoutCodec.encode(_uiState.value.buttons)
            }
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    private fun updateSelected(transform: (ButtonConfig) -> ButtonConfig) {
        val state = _uiState.value
        val id = state.selectedId ?: return
        _uiState.value = state.copy(
            buttons = state.buttons.map { if (it.id == id) transform(it) else it },
            isSaved = false
        )
    }

    companion object {
        private val LAYOUT_KEY = stringPreferencesKey(Constants.Preferences.BUTTON_LAYOUT)
    }
}
