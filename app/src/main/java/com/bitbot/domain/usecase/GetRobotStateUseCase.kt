package com.bitbot.domain.usecase

import com.bitbot.data.repository.RobotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetRobotStateUseCase @Inject constructor(
    private val repository: RobotRepository
) {
    fun observeLastMessage(): Flow<String> = repository.lastMessage
}
