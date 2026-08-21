package com.bitbot.data.plot

import com.bitbot.data.remote.dto.HeadersResponseDto
import com.bitbot.data.remote.websocket.PollingHandle
import com.bitbot.data.repository.RobotRepository
import com.bitbot.util.Constants
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

    /** Index of the kernel's control-loop counter in the flat frame; -1 if absent. */
    fun periodsIndex(headers: HeadersResponseDto?): Int =
        headers?.kernel?.indexOfFirst { it == Constants.Plot.PERIODS_COUNT_HEADER } ?: -1
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

    /**
     * X value (kernel periods_count, or app sample count as fallback) of every
     * recorded frame, oldest..newest — all channels share these frames. The
     * kernel loop and our poll rate differ (e.g. 500 Hz vs 10 Hz), so samples
     * are NOT spaced 1 x-unit apart; each carries its own x.
     */
    private val xBuf = ArrayDeque<Double>()

    private var channels: List<PlotChannel> = emptyList()
    private var capacity: Int = 1
    private var periodsIndex: Int = -1

    /**
     * Step index for the x axis: the kernel's own control-loop counter
     * (periods_count) when available, otherwise the app's sample count.
     * Independent of the poll rate — same horizon, same plot length.
     */
    private var sampleIdxValue: Long = 0
    val sampleIdx: Long get() = sampleIdxValue

    /** Estimated kernel control-loop periods per wall second; 0 until measured. */
    var periodsPerSecond: Double = 0.0
        private set
    private var rateWinStartPeriod = -1L
    private var rateWinStartNanos = 0L

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
    fun updateConfig(
        channels: List<PlotChannel>,
        rateHz: Int,
        horizonSamples: Int,
        periodsIndex: Int = this.periodsIndex
    ) {
        this.channels = channels
        this.periodsIndex = periodsIndex
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
        xBuf.clear()
        sampleIdxValue = 0
        _version.value++
    }

    /**
     * Recorded samples for a channel, oldest..newest. Zero-copy view of the
     * ring buffer — do not mutate; reads and writes are main-thread only.
     * The i-th value's x is [xSeries][xSeries] at `xs.size - values.size + i`.
     */
    fun series(key: String): List<Double> = buffers[key] ?: emptyList()

    /** X value of every recorded frame, oldest..newest (zero-copy view). */
    val xSeries: List<Double> get() = xBuf

    private fun appendFrame(frame: List<Double>) {
        val kernelCount = frame.getOrNull(periodsIndex)
            ?.takeIf { periodsIndex >= 0 && it.isFinite() }
            ?.toLong()
        val x: Double = if (kernelCount != null) {
            if (kernelCount < sampleIdxValue) {
                // Kernel restarted — start a fresh recording epoch
                buffers.clear()
                xBuf.clear()
            }
            sampleIdxValue = kernelCount
            updatePeriodRate(kernelCount)
            kernelCount.toDouble()
        } else {
            sampleIdxValue++
            sampleIdxValue.toDouble()
        }

        xBuf.addLast(x)
        while (xBuf.size > capacity) xBuf.removeFirst()

        for (ch in channels) {
            // Out-of-range frames keep alignment via NaN (skipped when drawing)
            val v = frame.getOrNull(ch.index) ?: Double.NaN
            val buf = buffers.getOrPut(ch.key) { ArrayDeque() }
            buf.addLast(v)
            while (buf.size > capacity) buf.removeFirst()
        }
        _version.value++
    }

    /** Rolling ~1s-window estimate of kernel periods per wall second. */
    private fun updatePeriodRate(period: Long) {
        val now = System.nanoTime()
        if (rateWinStartPeriod < 0 || period < rateWinStartPeriod) {
            rateWinStartPeriod = period
            rateWinStartNanos = now
            return
        }
        val dt = (now - rateWinStartNanos) / 1e9
        if (dt >= 1.0) {
            val dp = period - rateWinStartPeriod
            if (dp > 0) periodsPerSecond = dp / dt
            rateWinStartPeriod = period
            rateWinStartNanos = now
        }
    }

    companion object {
        /**
         * Pure CSV export: `kernel_count` column (the true x value of each
         * recorded frame) plus one column per channel. Channels that started
         * recording later have empty leading cells.
         */
        fun buildCsv(
            channels: List<PlotChannel>,
            buffers: Map<String, List<Double>>,
            xs: List<Double>
        ): String {
            if (channels.isEmpty() || xs.isEmpty()) return ""
            val header = StringBuilder("kernel_count,")
                .append(channels.joinToString(",") { escapeCsv("${it.group}.${it.name}") })
                .append('\n')
            val rows = StringBuilder()
            val series = channels.map { buffers[it.key].orEmpty() }
            val maxLen = xs.size // every frame appends an x; series never exceed it
            for (i in 0 until maxLen) {
                rows.append("%.0f".format(Locale.US, xs[i]))
                for (s in series) {
                    rows.append(',')
                    val idxInSeries = i - (maxLen - s.size)
                    if (idxInSeries >= 0 && idxInSeries < s.size && !s[idxInSeries].isNaN()) {
                        rows.append(formatNumber(s[idxInSeries]))
                    }
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
