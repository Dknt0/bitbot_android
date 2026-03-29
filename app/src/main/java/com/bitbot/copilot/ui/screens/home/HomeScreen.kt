package com.bitbot.copilot.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bitbot.copilot.data.model.ConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPilot: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) {
            // Optionally auto-navigate to pilot screen
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BitbotCopilot") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // App Logo/Title
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Robot Control Center",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            // Connection Status Card
            ConnectionStatusCard(connectionState = connectionState)

            // Connection Configuration
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Connection Settings",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = uiState.host,
                        onValueChange = viewModel::updateHost,
                        label = { Text("Host IP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )

                    OutlinedTextField(
                        value = uiState.port.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let { port ->
                                viewModel.updatePort(port)
                            }
                        },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (connectionState) {
                            is ConnectionState.Connected -> {
                                Button(
                                    onClick = viewModel::disconnect,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(Icons.Default.LinkOff, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Disconnect")
                                }
                            }
                            is ConnectionState.Connecting -> {
                                Button(
                                    onClick = {},
                                    modifier = Modifier.weight(1f),
                                    enabled = false
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Connecting...")
                                }
                            }
                            else -> {
                                Button(
                                    onClick = {
                                        viewModel.clearError()
                                        viewModel.connect()
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = !uiState.isLoading
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (connectionState is ConnectionState.Error) "Retry" else "Connect")
                                }
                            }
                        }
                    }
                }
            }

            // Error message
            uiState.errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = error,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        IconButton(onClick = viewModel::clearError) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Pilot Control Button
            if (connectionState is ConnectionState.Connected) {
                Button(
                    onClick = onNavigateToPilot,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(Icons.Default.SettingsRemote, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open Pilot Controls", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(connectionState: ConnectionState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (connectionState) {
                is ConnectionState.Connected -> MaterialTheme.colorScheme.primaryContainer
                is ConnectionState.Connecting -> MaterialTheme.colorScheme.secondaryContainer
                is ConnectionState.Error -> MaterialTheme.colorScheme.errorContainer
                is ConnectionState.Disconnected -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (connectionState) {
                    is ConnectionState.Connected -> Icons.Default.CheckCircle
                    is ConnectionState.Connecting -> Icons.Default.Sync
                    is ConnectionState.Error -> Icons.Default.Error
                    is ConnectionState.Disconnected -> Icons.Default.Cancel
                },
                contentDescription = null,
                tint = when (connectionState) {
                    is ConnectionState.Connected -> MaterialTheme.colorScheme.onPrimaryContainer
                    is ConnectionState.Connecting -> MaterialTheme.colorScheme.onSecondaryContainer
                    is ConnectionState.Error -> MaterialTheme.colorScheme.onErrorContainer
                    is ConnectionState.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = when (connectionState) {
                        is ConnectionState.Connected -> "Connected"
                        is ConnectionState.Connecting -> "Connecting"
                        is ConnectionState.Error -> "Error"
                        is ConnectionState.Disconnected -> "Disconnected"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = when (connectionState) {
                        is ConnectionState.Connected -> "Host: ${connectionState.host}"
                        is ConnectionState.Connecting -> connectionState.message
                        is ConnectionState.Error -> connectionState.message
                        is ConnectionState.Disconnected -> "Not connected to robot"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
