package com.bitbot.copilot.ui.screens.pilot.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.bitbot.copilot.data.model.ConnectionState
import com.bitbot.copilot.ui.theme.StatusConnected
import com.bitbot.copilot.ui.theme.StatusConnecting
import com.bitbot.copilot.ui.theme.StatusDisconnected
import com.bitbot.copilot.ui.theme.StatusError

@Composable
fun ConnectionStatus(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(
                    when (connectionState) {
                        is ConnectionState.Connected -> StatusConnected
                        is ConnectionState.Connecting -> StatusConnecting
                        is ConnectionState.Error -> StatusError
                        is ConnectionState.Disconnected -> StatusDisconnected
                    }
                )
        )

        Text(
            text = when (connectionState) {
                is ConnectionState.Connected -> "Connected to ${connectionState.host}"
                is ConnectionState.Connecting -> connectionState.message
                is ConnectionState.Error -> "Error: ${connectionState.message}"
                is ConnectionState.Disconnected -> "Disconnected"
            },
            style = MaterialTheme.typography.bodySmall,
            color = when (connectionState) {
                is ConnectionState.Connected -> StatusConnected
                is ConnectionState.Connecting -> StatusConnecting
                is ConnectionState.Error -> StatusError
                is ConnectionState.Disconnected -> StatusDisconnected
            }
        )
    }
}
