package com.bitbot.domain.usecase

import com.bitbot.data.repository.RobotRepository
import javax.inject.Inject

class SendControlEventUseCase @Inject constructor(
    private val repository: RobotRepository
) {
    fun sendButton(name: String, value: Long) {
        repository.sendButtonEvent(name, value)
    }

    fun sendVelocity(name: String, value: Double) {
        repository.sendVelocityEvent(name, value)
    }

    fun sendVelocities(events: List<Pair<String, Double>>) {
        repository.sendVelocityEvents(events)
    }
}
