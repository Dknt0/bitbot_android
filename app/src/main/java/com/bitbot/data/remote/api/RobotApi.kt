package com.bitbot.data.remote.api

import com.bitbot.data.remote.dto.ControlMappingDto
import com.bitbot.data.remote.dto.HeadersResponseDto
import com.bitbot.data.remote.dto.StatesListResponseDto
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resumeWithException

data class ConnectionResult(
    val headers: HeadersResponseDto?,
    val states: StatesListResponseDto?,
    val controlMappings: List<ControlMappingDto>?
)

@Singleton
class RobotApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {
    suspend fun connectAll(baseUrl: String): Result<ConnectionResult> = coroutineScope {
        try {
            // Launch all 3 HTTP requests in parallel (matching desktop behavior)
            val headersDeferred = async { getHeaders(baseUrl) }
            val statesDeferred = async { getStatesList(baseUrl) }
            val controlDeferred = async { getControlMappings(baseUrl) }

            val headersResult = headersDeferred.await()
            val statesResult = statesDeferred.await()
            val controlResult = controlDeferred.await()

            if (headersResult.isFailure) {
                return@coroutineScope Result.failure(headersResult.exceptionOrNull() ?: Exception("Headers fetch failed"))
            }

            Result.success(ConnectionResult(
                headers = headersResult.getOrNull(),
                states = statesResult.getOrNull(),
                controlMappings = controlResult.getOrNull()
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStatesList(baseUrl: String): Result<StatesListResponseDto> = runCatching {
        val request = Request.Builder()
            .url("$baseUrl/monitor/stateslist")
            .get()
            .header("User-Agent", "BITBOT")
            .build()

        client.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            val body = response.body?.string()
                ?: throw IOException("Empty response body")
            json.decodeFromString<StatesListResponseDto>(body)
        }
    }

    suspend fun getControlMappings(baseUrl: String): Result<List<ControlMappingDto>> = runCatching {
        val request = Request.Builder()
            .url("$baseUrl/setting/control/get")
            .get()
            .header("User-Agent", "BITBOT")
            .build()

        client.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            val body = response.body?.string()
                ?: throw IOException("Empty response body")
            json.decodeFromString<List<ControlMappingDto>>(body)
        }
    }

    suspend fun getHeaders(baseUrl: String): Result<HeadersResponseDto> = runCatching {
        val request = Request.Builder()
            .url("$baseUrl/monitor/headers")
            .get()
            .header("User-Agent", "BITBOT")
            .build()

        client.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            val body = response.body?.string()
                ?: throw IOException("Empty response body")
            json.decodeFromString<HeadersResponseDto>(body)
        }
    }

    suspend fun testConnection(baseUrl: String): Result<Boolean> = runCatching {
        val request = Request.Builder()
            .url("$baseUrl/monitor/headers")
            .get()
            .header("User-Agent", "BITBOT")
            .build()

        client.newCall(request).await().use { response ->
            response.isSuccessful
        }
    }
}

private suspend fun Call.await(): okhttp3.Response {
    return suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                continuation.resumeWith(Result.success(response))
            }
        })

        continuation.invokeOnCancellation {
            try {
                cancel()
            } catch (ex: Exception) {
                // Ignore
            }
        }
    }
}
