package com.bitbot

import com.bitbot.data.remote.api.RobotApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RobotApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: RobotApi
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()

        val client = OkHttpClient.Builder().build()
        api = RobotApi(client, json)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getHeaders returns parsed response`() = runBlocking {
        val mockResponse = MockResponse()
            .setBody("""{"kernel":["state","periods_count"],"bus":{"devices":[{"name":"joint1","type":"MujocoJoint","headers":["mode","pos"]}]},"extra":[]}""")
            .setResponseCode(200)
        server.enqueue(mockResponse)

        val baseUrl = server.url("/").toString().trimEnd('/')
        val result = api.getHeaders(baseUrl)

        assertTrue(result.isSuccess)
        val headers = result.getOrNull()
        assertNotNull(headers)
        assertEquals(listOf("state", "periods_count"), headers?.kernel)
        assertEquals(1, headers?.bus?.devices?.size)
        assertEquals("joint1", headers?.bus?.devices?.first()?.name)
    }

    @Test
    fun `getStatesList returns parsed response`() = runBlocking {
        val mockResponse = MockResponse()
            .setBody("""{"states":[{"id":0,"name":"kernel_idle"},{"id":1,"name":"kernel_stopped"}]}""")
            .setResponseCode(200)
        server.enqueue(mockResponse)

        val baseUrl = server.url("/").toString().trimEnd('/')
        val result = api.getStatesList(baseUrl)

        assertTrue(result.isSuccess)
        val states = result.getOrNull()
        assertNotNull(states)
        assertEquals(2, states?.states?.size)
        assertEquals("kernel_idle", states?.states?.first()?.name)
        assertEquals(0, states?.states?.first()?.id)
    }

    @Test
    fun `getControlMappings returns parsed response`() = runBlocking {
        val mockResponse = MockResponse()
            .setBody("""[{"event":"start","kb_key":"8"},{"event":"stop","kb_key":" "}]""")
            .setResponseCode(200)
        server.enqueue(mockResponse)

        val baseUrl = server.url("/").toString().trimEnd('/')
        val result = api.getControlMappings(baseUrl)

        assertTrue(result.isSuccess)
        val mappings = result.getOrNull()
        assertNotNull(mappings)
        assertEquals(2, mappings?.size)
        assertEquals("start", mappings?.first()?.event)
        assertEquals("8", mappings?.first()?.kb_key)
    }

    @Test
    fun `testConnection returns true for successful response`() = runBlocking {
        val mockResponse = MockResponse()
            .setResponseCode(200)
        server.enqueue(mockResponse)

        val baseUrl = server.url("/").toString().trimEnd('/')
        val result = api.testConnection(baseUrl)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
    }

    @Test
    fun `testConnection returns false for failed response`() = runBlocking {
        val mockResponse = MockResponse()
            .setResponseCode(500)
        server.enqueue(mockResponse)

        val baseUrl = server.url("/").toString().trimEnd('/')
        val result = api.testConnection(baseUrl)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == false)
    }
}
