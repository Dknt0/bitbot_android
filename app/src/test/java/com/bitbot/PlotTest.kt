package com.bitbot

import com.bitbot.data.model.ConnectionState
import com.bitbot.data.plot.PlotChannel
import com.bitbot.data.plot.PlotChannels
import com.bitbot.data.plot.PlotRecorder
import com.bitbot.data.remote.dto.DeviceHeadersDto
import com.bitbot.data.remote.dto.HeadersResponseDto
import com.bitbot.data.remote.dto.BusHeadersDto
import com.bitbot.data.remote.websocket.PollArbiter
import com.bitbot.data.remote.websocket.PollingHandle
import com.bitbot.data.repository.RobotRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlotTest {

    // --- PollArbiter: refcounted single-loop rate decision ---

    @Test
    fun `arbiter returns max requested rate`() {
        val a = PollArbiter()
        val h10 = a.add(10)
        assertEquals(10, a.maxRate())
        val h50 = a.add(50)
        assertEquals(50, a.maxRate())
        a.remove(h50)
        assertEquals(10, a.maxRate())
    }

    @Test
    fun `arbiter empty after all released`() {
        val a = PollArbiter()
        val h = a.add(10)
        assertNull(a.remove(h)) // last client removed -> no max remains
        assertNull(a.maxRate())
        assertTrue(a.isEmpty())
    }

    @Test
    fun `arbiter remove of unknown handle is harmless`() {
        val a = PollArbiter()
        val h1 = a.add(2)
        a.add(5)
        a.remove(PollingHandle(9999))
        assertEquals(5, a.maxRate())
        assertNotNull(h1)
    }

    // --- Channel registry ---

    @Test
    fun `registry maps flat frame layout kernel devices extra`() {
        val headers = HeadersResponseDto(
            kernel = listOf("state", "time"),
            bus = BusHeadersDto(
                devices = listOf(
                    DeviceHeadersDto("lf_leg", "leg", listOf("pos", "vel")),
                    DeviceHeadersDto("imu", "sensor", listOf("gyro"))
                )
            ),
            extra = listOf("fps")
        )
        val reg = PlotChannels.build(headers)
        assertEquals(
            listOf("kernel:state", "kernel:time", "dev:lf_leg:pos", "dev:lf_leg:vel", "dev:imu:gyro", "extra:fps"),
            reg.map { it.key }
        )
        assertEquals(List(6) { it }, reg.map { it.index })
        assertEquals(listOf("kernel", "lf_leg", "imu", "extra"), reg.groupBy { it.group }.keys.toList())
        assertTrue(PlotChannels.build(null).isEmpty())
    }

    // --- Recorder ---

    @OptIn(ExperimentalCoroutinesApi::class)
    class RecorderHarness(rateAcquires: MutableList<Int> = mutableListOf()) {
        val monitorData = MutableStateFlow<List<Double>>(emptyList())
        val acquires = rateAcquires
        val repository: RobotRepository = mockk {
            every { monitorData } returns this@RecorderHarness.monitorData
            every { acquireDataPolling(any()) } answers {
                rateAcquires.add(firstArg()); PollingHandle(rateAcquires.size)
            }
            every { releaseDataPolling(any()) } returns Unit
            every { connectionState } returns MutableStateFlow(ConnectionState.Connected("test"))
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun chan(key: String, index: Int) = PlotChannel(key, index, "g", key)

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `recorder advances x by exactly one configured tick per frame`() = runTest {
        val h = RecorderHarness()
        val rec = PlotRecorder(h.repository)
        val channels = listOf(chan("a", 0), chan("b", 2))
        rec.updateConfig(channels, 10, 100)
        rec.start(10)
        h.monitorData.value = listOf(1.0, 9.9, 2.0)
        testScheduler.advanceUntilIdle()
        h.monitorData.value = listOf(3.0, 9.9, 4.0)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(1.0, 3.0), rec.series("a"))
        assertEquals(listOf(2.0, 4.0), rec.series("b"))
        // Frontend clock: one frame = 1/rateHz seconds, independent of backend
        assertEquals(listOf(0.1, 0.2), rec.xSeries)
        assertEquals(0.2, rec.lastTimeSeconds, 1e-9)
        verify(exactly = 1) { h.repository.acquireDataPolling(10) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `x spacing follows the configured rate`() = runTest {
        val h = RecorderHarness()
        val rec = PlotRecorder(h.repository)
        rec.updateConfig(listOf(chan("a", 0)), 50, 500)
        rec.start(50)
        repeat(3) {
            h.monitorData.value = listOf(it.toDouble())
            testScheduler.advanceUntilIdle()
        }
        assertEquals(listOf(0.02, 0.04, 0.06), rec.xSeries)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `pause and resume continues the time axis`() = runTest {
        val h = RecorderHarness()
        val rec = PlotRecorder(h.repository)
        rec.updateConfig(listOf(chan("a", 0)), 10, 100)
        rec.start(10)
        h.monitorData.value = listOf(1.0)
        testScheduler.advanceUntilIdle()
        rec.pause()
        h.monitorData.value = listOf(2.0) // dropped while paused
        testScheduler.advanceUntilIdle()
        rec.start(10)
        h.monitorData.value = listOf(3.0)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(0.1, 0.2), rec.xSeries) // continuous, no reset
        assertEquals(0.2, rec.lastTimeSeconds, 1e-9)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `recorder trims buffers to horizon capacity`() = runTest {
        val h = RecorderHarness()
        val rec = PlotRecorder(h.repository)
        rec.updateConfig(listOf(chan("a", 0)), 10, 3)
        rec.start(10)
        repeat(5) {
            h.monitorData.value = listOf(it.toDouble())
            testScheduler.advanceUntilIdle() // StateFlow conflates; collect each frame
        }
        assertEquals(0.5, rec.lastTimeSeconds, 1e-9) // full time kept, buffer trimmed
        assertEquals(listOf(3, 3), listOf(rec.series("a").size, rec.xSeries.size))
        assertEquals(listOf(2.0, 3.0, 4.0), rec.series("a"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `pause stops capture and releases polling`() = runTest {
        val h = RecorderHarness()
        val rec = PlotRecorder(h.repository)
        rec.updateConfig(listOf(chan("a", 0)), 10, 100)
        rec.start(10)
        h.monitorData.value = listOf(1.0)
        testScheduler.advanceUntilIdle()
        assertEquals(0.1, rec.lastTimeSeconds, 1e-9)

        rec.pause()
        h.monitorData.value = listOf(2.0) // no capture while paused
        testScheduler.advanceUntilIdle()
        assertEquals(0.1, rec.lastTimeSeconds, 1e-9)
        assertEquals(listOf(1.0), rec.series("a"))
        verify(exactly = 1) { h.repository.releaseDataPolling(any()) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `updateConfig while recording re-acquires at new rate`() = runTest {
        val h = RecorderHarness()
        val rec = PlotRecorder(h.repository)
        rec.updateConfig(listOf(chan("a", 0)), 10, 100)
        rec.start(10)
        rec.updateConfig(listOf(chan("a", 0)), 50, 100)
        verify(atLeast = 1) { h.repository.acquireDataPolling(50) }
    }

    // --- CSV export ---

    @Test
    fun `csv carries time_s per row`() {
        val a = PlotChannel("kernel:x", 0, "kernel", "x")
        val b = PlotChannel("dev:leg:pos", 1, "leg", "pos")
        // 10 Hz: consecutive x are 0.1 s apart (frontend clock)
        val csv = PlotRecorder.buildCsv(
            channels = listOf(a, b),
            buffers = mapOf(
                "kernel:x" to listOf(1.0, 2.0, 3.0),
                "dev:leg:pos" to listOf(10.0) // started later
            ),
            xs = listOf(0.1, 0.2, 0.3)
        )
        val lines = csv.trim().lines()
        assertEquals("time_s,kernel.x,leg.pos", lines[0])
        assertEquals("0.100,1,", lines[1])
        assertEquals("0.200,2,", lines[2])
        assertEquals("0.300,3,10", lines[3])
    }

    @Test
    fun `csv empty when no channels or frames`() {
        assertEquals("", PlotRecorder.buildCsv(emptyList(), emptyMap(), emptyList()))
        val ch = PlotChannel("kernel:x", 0, "kernel", "x")
        assertEquals("", PlotRecorder.buildCsv(listOf(ch), mapOf("kernel:x" to listOf(1.0)), emptyList()))
    }

    @Test
    fun `csv quotes names containing commas`() {
        val ch = PlotChannel("dev:a:b, c", 0, "a", "b, c")
        val csv = PlotRecorder.buildCsv(listOf(ch), mapOf("dev:a:b, c" to listOf(1.5)), listOf(0.05))
        assertTrue(csv.lines()[0].contains("\"a.b, c\""))
        assertEquals("0.050,1.5", csv.trim().lines()[1])
    }

}
