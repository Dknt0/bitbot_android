package com.bitbot.data.plot

import com.bitbot.data.remote.dto.HeadersResponseDto
import com.bitbot.data.remote.websocket.PollingHandle
import com.bitbot.data.repository.RobotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Locale

/**
 * A monitor-data channel: stable string key, index into the flat frame array,
 * and display names. Frame layout matches DataViewModel parsing:
 * kernel values first, then each device's headers in order, then extras.
 */
data class PlotChannel(
    val key: String,
    val index: Int,
    val group: String,
    val name: String
)

object PlotChannels {
    fun build(headers: HeadersResponseDto?): List<PlotChannel> {
        if (headers == null) return emptyList()
        val out = mutableListOf<PlotChannel>()
        var idx = 0
        headers.kernel.forEach { h ->
            out += PlotChannel("kernel:$h", idx++, "kernel", h)
        }
        headers.bus?.devices?.forEach { dev ->
            dev.headers.forEach { h ->
                out += PlotChannel("dev:${dev.name}:$h", idx++, dev.name, h)
            }
        }
        headers.extra.forEach { h ->
            out += PlotChannel("extra:$h", idx++, "extra", h)
        }
        return out
    }
}

/**
 * Records selected monitor channels into ring buffers while a recording is
 * active. Runs as an app-wide singleton so recording continues in the
 * background when the user switches to other panels.
 *
 * Buffer writes (monitorData collection) and reads (Canvas draw, CSV export)
 * all happen on the main thread, so no synchronization is needed.
 * Bump [version] to invalidate plot redraws.
 */
@Singleton
class PlotRecorder @Inject constructor(
    private val repository: RobotRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val buffers = LinkedHashMap<String, ArrayDeque<Double>>()
    private var channels: List<PlotChannel> = emptyList()
    private var capacity: Int = 1

    private var sampleIdxValue: Long = 0
    /** Step index of the most recent recorded sample (x axis). */
    val sampleIdx: Long get() = sampleIdxValue

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    private var pollHandle: PollingHandle? = null
    private var recordJob: Job? = null

    /**
     * Live config update. Channels added later start recording from now
     * (earlier samples empty); removed channels' buffers are dropped.
     * Rate/horizon changes apply immediately, also while recording.
     */
    fun updateConfig(channels: List<PlotChannel>, rateHz: Int, horizonSamples: Int) {
        this.channels = channels
        capacity = horizonSamples.coerceAtLeast(1)
        buffers.keys.retainAll(channels.map { it.key }.toSet())
        buffers.values.forEach { buf -> while (buf.size > capacity) buf.removeFirst() }

        if (_isRecording.value && pollHandle != null) {
            // Re-acquire so the shared loop picks up the new rate
            pollHandle?.let { repository.releaseDataPolling(it) }
            pollHandle = repository.acquireDataPolling(rateHz)
        }
        _version.value++
    }

    fun start(rateHz: Int) {
        if (_isRecording.value || channels.isEmpty()) return
        pollHandle = repository.acquireDataPolling(rateHz)
        _isRecording.value = true
        recordJob = scope.launch {
            repository.monitorData.collect { frame ->
                if (frame.isEmpty()) return@collect
                appendFrame(frame)
            }
        }
    }

    fun pause() {
        if (!_isRecording.value) return
        _isRecording.value = false
        recordJob?.cancel()
        recordJob = null
        pollHandle?.let { repository.releaseDataPolling(it) }
        pollHandle = null
    }

    /** Drop all recorded samples. */
    fun clear() {
        buffers.clear()
        sampleIdxValue = 0
        _version.value++
    }

    /** Latest-first sample list for a channel (oldest .. newest). Empty if none. */
    fun series(key: String): List<Double> = buffers[key]?.toList() ?: emptyList()

    private fun appendFrame(frame: List<Double>) {
        for (ch in channels) {
            val v = frame.getOrNull(ch.index) ?: continue
            val buf = buffers.getOrPut(ch.key) { ArrayDeque() }
            buf.addLast(v)
            while (buf.size > capacity) buf.removeFirst()
        }
        sampleIdxValue++
        _version.value++
    }

    companion object {
        /**
         * Pure CSV export: `step` column plus one column per channel. Channels
         * that started recording later have empty leading cells.
         */
        fun buildCsv(channels: List<PlotChannel>, buffers: Map<String, List<Double>>): String {
            if (channels.isEmpty()) return ""
            val header = StringBuilder("step,")
                .append(channels.joinToString(",") { escapeCsv("${it.group}.${it.name}") })
                .append('\n')
            val rows = StringBuilder()
            val series = channels.map { buffers[it.key].orEmpty() }
            val maxLen = series.maxOf { it.size }
            for (i in 0 until maxLen) {
                rows.append(i + 1) // 1-based step for spreadsheet friendliness
                for (s in series) {
                    rows.append(',')
                    // Left-pad alignment: newest sample of each series is the same step
                    val idxInSeries = i - (maxLen - s.size)
                    if (idxInSeries >= 0) rows.append(formatNumber(s[idxInSeries]))
                }
                rows.append('\n')
            }
            return header.toString() + rows.toString()
        }

        private fun escapeCsv(field: String): String =
            if (field.contains(',') || field.contains('"')) "\"${field.replace("\"", "\"\"")}\""
            else field

        /** Compact decimal without locale-dependent separators or padded zeros. */
        private fun formatNumber(v: Double): String {
            val s = "%.10g".format(Locale.US, v)
            if ('e' in s || 'E' in s) return s
            return s.trimEnd('0').trimEnd('.').ifEmpty { "0" }
        }
    }
}
