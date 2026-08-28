package com.bitbot.ui.screens.plot

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitbot.data.model.ConnectionState
import com.bitbot.data.plot.PlotChannel
import com.bitbot.data.plot.PlotChannels
import com.bitbot.data.plot.PlotRecorder
import com.bitbot.data.repository.RobotRepository
import com.bitbot.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class PlotUiState(
    val registry: List<PlotChannel> = emptyList(),
    /** Selected channels in selection order (drives curve colors). */
    val selected: List<PlotChannel> = emptyList(),
    val rateHz: Int = Constants.Plot.DEFAULT_RATE_HZ,
    val horizonSeconds: Int = Constants.Plot.DEFAULT_HORIZON_SECONDS,
    val isRecording: Boolean = false,
    /** Recorded time span in seconds (frontend clock: frames / rateHz). */
    val elapsedSeconds: Double = 0.0,
    val hasData: Boolean = false
) {
    /** Follow-window width in x — exactly the configured horizon (seconds). */
    val horizonSpanX: Float get() = horizonSeconds.toFloat()
}

@HiltViewModel
class PlotViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: RobotRepository,
    private val recorder: PlotRecorder,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(PlotUiState())
    val uiState: StateFlow<PlotUiState> = _uiState.asStateFlow()

    /** Redraw trigger from the recorder; collected by the plot canvas. */
    val version: StateFlow<Long> = recorder.version

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.Disconnected)

    init {
        viewModelScope.launch {
            // Headers are fetched on connect, but refresh in case the plot is
            // the first panel opened after a reconnect with changed channels.
            repository.fetchHeaders()
            val registry = PlotChannels.build(repository.headers)

            val prefs = dataStore.data.first()
            val savedKeys = runCatching {
                json.decodeFromString(ListSerializer(String.serializer()), prefs[CHANNELS_KEY] ?: "[]")
            }.getOrDefault(emptyList())
            val rate = (prefs[RATE_KEY] ?: Constants.Plot.DEFAULT_RATE_HZ)
                .let { r -> if (r in Constants.Plot.RATE_CHOICES_HZ) r else Constants.Plot.DEFAULT_RATE_HZ }
            val horizon = (prefs[HORIZON_KEY] ?: Constants.Plot.DEFAULT_HORIZON_SECONDS)
                .coerceIn(Constants.Plot.MIN_HORIZON_SECONDS, Constants.Plot.MAX_HORIZON_SECONDS)

            val keyToChannel = registry.associateBy { it.key }
            val selected = savedKeys.mapNotNull { keyToChannel[it] }

            _uiState.value = PlotUiState(
                registry = registry,
                selected = selected,
                rateHz = rate,
                horizonSeconds = horizon
            )
            recorder.updateConfig(selected, rate, rate * horizon)
        }

        viewModelScope.launch {
            recorder.isRecording.collect { recording ->
                _uiState.value = _uiState.value.copy(isRecording = recording)
            }
        }
        // Throttled UI-state mirror (the canvas redraws from `version` directly);
        // avoids state churn at a 100 Hz sample rate.
        viewModelScope.launch {
            var lastUpdate = 0L
            recorder.version.collect {
                val now = System.currentTimeMillis()
                if (now - lastUpdate >= 200) {
                    lastUpdate = now
                    _uiState.value = _uiState.value.copy(
                        elapsedSeconds = recorder.lastTimeSeconds,
                        hasData = recorder.lastTimeSeconds > 0.0
                    )
                }
            }
        }
    }

    fun toggleRecording() {
        val s = _uiState.value
        if (s.isRecording) {
            recorder.pause()
        } else {
            if (s.selected.isEmpty()) return
            recorder.updateConfig(s.selected, s.rateHz, s.rateHz * s.horizonSeconds)
            recorder.start(s.rateHz)
        }
    }

    fun setSelectedKeys(keys: List<String>) {
        viewModelScope.launch {
            dataStore.edit {
                it[CHANNELS_KEY] = json.encodeToString(ListSerializer(String.serializer()), keys)
            }
        }
        val keyToChannel = _uiState.value.registry.associateBy { it.key }
        val selected = keys.mapNotNull { keyToChannel[it] }
        _uiState.value = _uiState.value.copy(selected = selected)
        recorder.updateConfig(selected, _uiState.value.rateHz, _uiState.value.rateHz * _uiState.value.horizonSeconds)
    }

    fun setRate(rateHz: Int) {
        if (rateHz !in Constants.Plot.RATE_CHOICES_HZ) return
        viewModelScope.launch { dataStore.edit { it[RATE_KEY] = rateHz } }
        _uiState.value = _uiState.value.copy(rateHz = rateHz)
        recorder.updateConfig(_uiState.value.selected, rateHz, rateHz * _uiState.value.horizonSeconds)
    }

    fun setHorizon(seconds: Int) {
        val h = seconds.coerceIn(Constants.Plot.MIN_HORIZON_SECONDS, Constants.Plot.MAX_HORIZON_SECONDS)
        viewModelScope.launch { dataStore.edit { it[HORIZON_KEY] = h } }
        _uiState.value = _uiState.value.copy(horizonSeconds = h)
        recorder.updateConfig(_uiState.value.selected, _uiState.value.rateHz, _uiState.value.rateHz * _uiState.value.horizonSeconds)
    }

    fun clearData() = recorder.clear()

    fun seriesFor(channel: PlotChannel): List<Double> = recorder.series(channel.key)

    /** X value (kernel periods_count) of every recorded frame, oldest..newest. */
    fun xSeries(): List<Double> = recorder.xSeries

    fun dismissSaveMessage() { _saveMessage.value = null }

    /** Save recorded channels as CSV into Downloads/Bitbot/. */
    fun saveCsv() {
        val s = _uiState.value
        if (s.selected.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val buffers = s.selected.associate { it.key to recorder.series(it.key) }
            val csv = PlotRecorder.buildCsv(s.selected, buffers, recorder.xSeries)
            val uri = writeMediaStore("csv", "text/csv") { os -> os.write(csv.toByteArray(Charsets.UTF_8)) }
            _saveMessage.value = if (uri != null) "Saved CSV to Downloads/Bitbot" else "CSV save failed"
        }
    }

    /** Save a plot snapshot bitmap as PNG into Downloads/Bitbot/. */
    fun savePng(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            val uri = writeMediaStore("png", "image/png") { os ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
            }
            _saveMessage.value = if (uri != null) "Saved PNG to Downloads/Bitbot" else "PNG save failed"
        }
    }

    private fun writeMediaStore(
        ext: String,
        mime: String,
        write: (java.io.OutputStream) -> Unit
    ): Uri? = runCatching {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "bitbot_plot_${stamp}.$ext")
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/Bitbot"
            )
        }
        val uri = appContext.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
            ?: return@runCatching null
        appContext.contentResolver.openOutputStream(uri)?.use(write)
        uri
    }.onFailure { Log.e(TAG, "save failed", it) }.getOrNull()

    fun disconnect() = repository.disconnect()

    companion object {
        private const val TAG = "PlotViewModel"
        private val CHANNELS_KEY = stringPreferencesKey(Constants.Preferences.PLOT_CHANNELS)
        private val RATE_KEY = intPreferencesKey(Constants.Preferences.PLOT_RATE_HZ)
        private val HORIZON_KEY = intPreferencesKey(Constants.Preferences.PLOT_HORIZON_SECONDS)
    }
}
