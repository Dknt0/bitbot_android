package com.bitbot.ui.screens.data

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitbot.data.model.ConnectionState
import com.bitbot.data.remote.dto.HeadersResponseDto
import com.bitbot.data.repository.RobotRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DataRow(
    val deviceName: String,
    val values: List<Double>,
    val headers: List<String>
)

data class DataUiState(
    val selectedTab: String = "",
    val tabs: List<String> = emptyList(),
    val kernelHeaders: List<String> = emptyList(),
    val kernelValues: List<Double> = emptyList(),
    val stateNames: Map<Int, String> = emptyMap(),
    val rows: List<DataRow> = emptyList(),
    val extraHeaders: List<String> = emptyList(),
    val extraValues: List<Double> = emptyList()
)

@HiltViewModel
class DataViewModel @Inject constructor(
    private val repository: RobotRepository
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.Disconnected)

    private val _uiState = MutableStateFlow(DataUiState())
    val uiState: StateFlow<DataUiState> = _uiState.asStateFlow()

    private var headers: HeadersResponseDto? = null

    init {
        // Fetch headers from HTTP API
        viewModelScope.launch {
            repository.fetchHeaders()
            headers = repository.headers
            Log.d(TAG, "Headers: kernel=${headers?.kernel?.size}, " +
                    "devices=${headers?.bus?.devices?.size}, extra=${headers?.extra?.size}")

            // Build state name map
            val stateMap = repository.statesList?.states?.associate { it.id to it.name } ?: emptyMap()
            _uiState.value = _uiState.value.copy(stateNames = stateMap)

            rebuildTabs()
            repository.startDataPolling()
        }

        // Start polling whenever connection becomes ready (handles reconnects)
        viewModelScope.launch {
            repository.connectionState.collect { cs ->
                if (cs is ConnectionState.Connected && headers != null) {
                    repository.startDataPolling()
                }
            }
        }

        // Observe monitor data and build rows
        viewModelScope.launch {
            repository.monitorData.collect { data ->
                if (data.isEmpty()) return@collect
                val h = headers ?: return@collect
                parseAndUpdate(h, data)
            }
        }
    }

    private fun parseAndUpdate(h: HeadersResponseDto, data: List<Double>) {
        val state = _uiState.value
        val allDevices = h.bus?.devices ?: emptyList()

        var offset = h.kernel.size
        if (offset > data.size) return

        val kernelValues = data.take(offset)

        val allDeviceRows = allDevices.map { dev ->
            val devValues = data.drop(offset).take(dev.headers.size)
            offset += dev.headers.size
            DataRow(dev.name, devValues, dev.headers)
        }

        val extraValues = data.drop(offset).take(h.extra.size)

        val grouped = allDevices.mapIndexedNotNull { i, dev ->
            dev.type?.let { type -> type to allDeviceRows[i] }
        }.groupBy({ it.first }, { it.second })

        val tab = state.selectedTab
        val tabRows = grouped[tab] ?: emptyList()

        _uiState.value = state.copy(
            kernelValues = kernelValues,
            rows = tabRows,
            extraValues = extraValues
        )
    }

    private fun rebuildTabs() {
        val h = headers ?: return
        val devices = h.bus?.devices ?: emptyList()
        val typeOrder = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (dev in devices) {
            val type = dev.type ?: "Other"
            if (seen.add(type)) typeOrder.add(type)
        }
        if (h.extra.isNotEmpty()) typeOrder.add("Extra")

        _uiState.value = _uiState.value.copy(
            tabs = typeOrder,
            kernelHeaders = h.kernel,
            extraHeaders = h.extra,
            selectedTab = typeOrder.firstOrNull() ?: ""
        )
    }

    fun selectTab(tab: String) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
        val h = headers ?: return
        val data = repository.monitorData.value
        if (data.isNotEmpty()) parseAndUpdate(h, data)
    }

    fun disconnect() {
        repository.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopDataPolling()
    }

    companion object {
        private const val TAG = "DataViewModel"
    }
}
