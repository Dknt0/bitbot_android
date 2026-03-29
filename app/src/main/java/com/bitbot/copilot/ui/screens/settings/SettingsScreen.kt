package com.bitbot.copilot.ui.screens.settings

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bitbot.copilot.util.Constants.PolicyMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::resetToDefaults) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection Settings
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Connection Settings", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = uiState.host,
                        onValueChange = viewModel::updateHost,
                        label = { Text("Host IP Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Computer, null) }
                    )
                    OutlinedTextField(
                        value = uiState.port.toString(),
                        onValueChange = { text ->
                            text.toIntOrNull()?.let { port ->
                                if (port in 1..65535) viewModel.updatePort(port)
                            }
                        },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        leadingIcon = { Icon(Icons.Default.SettingsEthernet, null) }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-connect on startup")
                            Text("Automatically connect when app starts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = uiState.autoConnect, onCheckedChange = viewModel::updateAutoConnect)
                    }
                }
            }

            // Velocity Limits
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Velocity Limits", style = MaterialTheme.typography.titleMedium)
                    Text("Max velocity per axis. Changes take effect on next connect.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    PolicyVelGroup("Standing", PolicyMode.STANDING,
                        uiState.standingVelX, viewModel::updateStandingVelX,
                        uiState.standingVelY, viewModel::updateStandingVelY,
                        uiState.standingVelYaw, viewModel::updateStandingVelYaw)

                    PolicyVelGroup("Walking", PolicyMode.WALKING,
                        uiState.walkingVelX, viewModel::updateWalkingVelX,
                        uiState.walkingVelY, viewModel::updateWalkingVelY,
                        uiState.walkingVelYaw, viewModel::updateWalkingVelYaw)

                    PolicyVelGroup("Robust", PolicyMode.ROBUST,
                        uiState.robustVelX, viewModel::updateRobustVelX,
                        uiState.robustVelY, viewModel::updateRobustVelY,
                        uiState.robustVelYaw, viewModel::updateRobustVelYaw)
                }
            }

            // Save Button
            Button(
                onClick = viewModel::saveSettings,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !uiState.isSaved
            ) {
                Icon(if (uiState.isSaved) Icons.Default.Check else Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text(if (uiState.isSaved) "Settings Saved" else "Save Settings")
            }

            if (uiState.isSaved) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Settings saved successfully.", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun PolicyVelGroup(
    label: String,
    mode: PolicyMode,
    velX: Double, onUpdateX: (Double) -> Unit,
    velY: Double, onUpdateY: (Double) -> Unit,
    velYaw: Double, onUpdateYaw: (Double) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label (defaults: ${mode.defaultVelX}, ${mode.defaultVelY}, ${mode.defaultVelYaw})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("Vel X", velX, onUpdateX),
                Triple("Vel Y", velY, onUpdateY),
                Triple("Vel Yaw", velYaw, onUpdateYaw)
            ).forEach { (fieldLabel, value, onUpdate) ->
                OutlinedTextField(
                    value = "%.2f".format(value),
                    onValueChange = { text -> text.toDoubleOrNull()?.let(onUpdate) },
                    label = { Text(fieldLabel, fontSize = MaterialTheme.typography.labelSmall.fontSize) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                )
            }
        }
    }
}
