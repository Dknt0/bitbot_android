package com.bitbot.copilot.domain.usecase

import com.bitbot.copilot.data.model.ConnectionState
import com.bitbot.copilot.data.repository.RobotRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ConnectToRobotUseCase @Inject constructor(
    private val repository: RobotRepository
) {
    suspend fun invoke(host: String, port: Int) {
        repository.configure(host, port)
        repository.connect()
    }

    fun disconnect() {
        repository.disconnect()
    }

    fun observeConnectionState(): Flow<ConnectionState> = repository.connectionState
}
