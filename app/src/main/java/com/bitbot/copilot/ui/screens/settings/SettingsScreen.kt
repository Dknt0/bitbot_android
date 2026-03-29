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
import com.bitbot.copilot.util.Constants.VelocityPrefs

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
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Velocity Limits", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Positive/negative limits per axis. Joystick center = zero velocity.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    for (mode in PolicyMode.entries) {
                        PolicyVelGroup(
                            mode = mode,
                            velConfig = uiState.velConfig,
                            onUpdate = viewModel::updateVelocity
                        )
                    }
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
    mode: PolicyMode,
    velConfig: Map<String, Double>,
    onUpdate: (String, Double) -> Unit
) {
    val keys = mode.prefKeys
    val color = when (mode) {
        PolicyMode.STANDING -> MaterialTheme.colorScheme.primary
        PolicyMode.WALKING -> MaterialTheme.colorScheme.tertiary
        PolicyMode.ROBUST -> MaterialTheme.colorScheme.secondary
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "${mode.label}  (defaults: x[${mode.defaultVelXNeg}, ${mode.defaultVelXPos}]  y[${mode.defaultVelYNeg}, ${mode.defaultVelYPos}]  yaw[${mode.defaultVelYawNeg}, ${mode.defaultVelYawPos}])",
            style = MaterialTheme.typography.titleSmall,
            color = color
        )

        // Vel X
        AxisRow(
            label = "X",
            posValue = velConfig[keys.xPos] ?: mode.defaultVelXPos,
            negValue = velConfig[keys.xNeg] ?: mode.defaultVelXNeg,
            onPosUpdate = { onUpdate(keys.xPos, it) },
            onNegUpdate = { onUpdate(keys.xNeg, it) }
        )

        // Vel Y
        AxisRow(
            label = "Y",
            posValue = velConfig[keys.yPos] ?: mode.defaultVelYPos,
            negValue = velConfig[keys.yNeg] ?: mode.defaultVelYNeg,
            onPosUpdate = { onUpdate(keys.yPos, it) },
            onNegUpdate = { onUpdate(keys.yNeg, it) }
        )

        // Vel Yaw
        AxisRow(
            label = "Yaw",
            posValue = velConfig[keys.yawPos] ?: mode.defaultVelYawPos,
            negValue = velConfig[keys.yawNeg] ?: mode.defaultVelYawNeg,
            onPosUpdate = { onUpdate(keys.yawPos, it) },
            onNegUpdate = { onUpdate(keys.yawNeg, it) }
        )
    }
}

@Composable
private fun AxisRow(
    label: String,
    posValue: Double,
    negValue: Double,
    onPosUpdate: (Double) -> Unit,
    onNegUpdate: (Double) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.width(36.dp),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace
        )
        OutlinedTextField(
            value = "%.2f".format(posValue),
            onValueChange = { text -> text.toDoubleOrNull()?.let(onPosUpdate) },
            label = { Text("+", fontSize = MaterialTheme.typography.labelSmall.fontSize) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
        )
        OutlinedTextField(
            value = "%.2f".format(negValue),
            onValueChange = { text -> text.toDoubleOrNull()?.let(onNegUpdate) },
            label = { Text("-", fontSize = MaterialTheme.typography.labelSmall.fontSize) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
        )
    }
}
