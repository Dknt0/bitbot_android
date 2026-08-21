package com.bitbot

import com.bitbot.data.plot.PlotChannels
import com.bitbot.data.plot.PlotRecorder
import com.bitbot.data.remote.api.RobotApi
import com.bitbot.data.repository.RobotRepository
import com.bitbot.data.remote.websocket.PollingHandle
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Live integration test against a locally running bitbot-ovinf backend
 * (127.0.0.1:12888). Skips silently when the backend is not reachable.
 * Feeds real monitor frames through the real PlotRecorder and verifies the
 * plotter's window/density math, plus dumps ASCII renders for eyeballing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlotLiveBackendTest {

    private val base = "http://127.0.0.1:12888"
    private val wsUrl = "ws://127.0.0.1:12888/console"
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()

    @Before fun setUpMain() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDownMain() = Dispatchers.resetMain()

    private fun backendReachable(): Boolean = runCatching {
        client.newCall(Request.Builder().url("$base/monitor/headers").build()).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    @Test
    fun `plot pipeline against live backend`() {
        assumeTrue("bitbot-ovinf backend not running", backendReachable())

        // --- 1. real headers -> registry ---
        val api = RobotApi(client, json)
        val headers = kotlinx.coroutines.runBlocking { api.getHeaders(base).getOrThrow() }
        val registry = PlotChannels.build(headers)
        assertEquals("state", registry[0].name)
        assertEquals("periods_count", registry[1].name)
        val periodsIdx = PlotChannels.periodsIndex(headers)
        assertEquals(1, periodsIdx)
        assertTrue("expected joint channels", registry.size > 50)

        val posChannel = registry.first { it.name == "actual_position" }
        val kernelT = registry.first { it.name == "kernel_t(ms)" }

        // --- 2. real recorder ---
        val frameFlow = MutableStateFlow<List<Double>>(emptyList())
        val acquires = mutableListOf<Int>()
        val repository = mockk<RobotRepository> {
            every { monitorData } returns frameFlow
            every { acquireDataPolling(any()) } answers {
                acquires.add(firstArg()); PollingHandle(acquires.size)
            }
            every { releaseDataPolling(any()) } returns Unit
        }
        val recorder = PlotRecorder(repository)
        recorder.updateConfig(listOf(posChannel, kernelT), 20, 20 * 60, periodsIdx)
        recorder.start(20)

        // --- 3. live websocket polling at 20 Hz for ~4 s ---
        val replies = java.util.concurrent.atomic.AtomicInteger()
        val ws = client.newWebSocket(
            Request.Builder().url(wsUrl).build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    val outer = json.parseToJsonElement(text).jsonObject
                    if (outer["type"]?.jsonPrimitive?.content == "monitor_data") {
                        val inner = json.parseToJsonElement(outer["data"]!!.jsonPrimitive.content).jsonObject
                        val arr = inner["data"]!!.jsonArray
                        frameFlow.value = arr.map { it.jsonPrimitive.content.toDouble() }
                        replies.incrementAndGet()
                    }
                }
            }
        )
        val pollStart = System.nanoTime()
        repeat(80) {
            ws.send("""{"type":"request_data","data":""}""")
            Thread.sleep(50)
        }
        val pollSeconds = (System.nanoTime() - pollStart) / 1e9
        Thread.sleep(300) // drain in-flight replies
        ws.close(1000, null)

        println("polled %.1fs, replies=%d (reply rate %.1f Hz)".format(pollSeconds, replies.get(), replies.get() / pollSeconds))

        // --- 4. recorder invariants on real data ---
        val xs = recorder.xSeries.toList()
        assertTrue("no frames captured", xs.size >= 40)
        assertTrue("x must be monotonic", xs.zipWithNext().all { (a, b) -> b > a })
        assertEquals(xs.last().toLong(), recorder.sampleIdx)

        val diffs = xs.zipWithNext().map { (a, b) -> b - a }
        val medianDx = diffs.sorted()[diffs.size / 2]
        val pps = recorder.periodsPerSecond
        println("periodsPerSecond=%.0f, median dx=%.1f, frames=%d".format(pps, medianDx, xs.size))
        assertTrue("kernel rate implausible: $pps", pps in 100.0..2000.0)

        // Sample spacing must reflect kernel-rate/poll-rate mismatch
        val expectedDx = pps / 20.0
        assertTrue(
            "spacing $medianDx not close to kernel/poll mismatch $expectedDx",
            abs(medianDx - expectedDx) < expectedDx * 0.5
        )

        val values = recorder.series(posChannel.key)
        val finite = values.count { it.isFinite() }
        assertTrue("mostly finite samples expected, got $finite/${values.size}", finite >= values.size * 9 / 10)

        // --- 5. window + density math (the regression class we fixed) ---
        val plotWidthPx = 1000f
        for (horizonSeconds in listOf(1, 10, 60)) {
            val xEnd = recorder.sampleIdx.toFloat()
            val xSpan = (pps * horizonSeconds).toFloat()
            val xStart = xEnd - xSpan
            val visible = xs.count { it in xStart.toDouble()..xEnd.toDouble() }
            val samplesPerPixel = visible / plotWidthPx
            val mode = if (samplesPerPixel <= 1.5f) "polyline" else "minmax"
            println("horizon=${horizonSeconds}s: span=$xSpan periods, visible=$visible samples, " +
                "samples/px=%.3f -> $mode".format(samplesPerPixel))
            if (horizonSeconds == 1) {
                // 1s window at 20 Hz holds ~20 samples -> connected polyline
                assertTrue("1s window should use polyline", samplesPerPixel <= 1.5f)
            }
        }

        // --- 6. ASCII render (60s window) for visual inspection ---
        asciiPlot("hip ${posChannel.group}.actual_position", recorder, posChannel.key, pps, 60)
        asciiPlot("${kernelT.group}.kernel_t(ms)", recorder, kernelT.key, pps, 60)
    }

    private fun asciiPlot(title: String, recorder: PlotRecorder, key: String, pps: Double, horizonSeconds: Int) {
        val cols = 90
        val rows = 14
        val xs = recorder.xSeries
        val ys = recorder.series(key)
        if (ys.isEmpty()) return
        val xEnd = recorder.sampleIdx.toDouble()
        val xStart = xEnd - pps * horizonSeconds
        val pts = xs.mapIndexed { i, x -> x to (ys.getOrNull(i - (xs.size - ys.size)) ?: Double.NaN) }
            .filter { (x, v) -> x >= xStart && v.isFinite() }
        if (pts.isEmpty()) return
        var lo = pts.minOf { it.second }
        var hi = pts.maxOf { it.second }
        if (hi - lo < 1e-9) {
            lo -= 1.0; hi += 1.0
        }
        val grid = Array(rows) { CharArray(cols) { ' ' } }
        pts.forEach { (x, v) ->
            val c = ((x - xStart) / (xEnd - xStart) * (cols - 1)).toInt().coerceIn(0, cols - 1)
            val r = (rows - 1 - ((v - lo) / (hi - lo) * (rows - 1)).toInt()).coerceIn(0, rows - 1)
            grid[r][c] = '#'
        }
        val sb = StringBuilder("\n[$title]  window=${horizonSeconds}s  y[%.4g, %.4g]  points=${pts.size}\n".format(lo, hi))
        grid.forEach { row -> sb.append(row).append('\n') }
        sb.append((0 until cols).joinToString("") { if (it % 10 == 0) "|" else " " }).append('\n')
        println(sb)
    }
}
