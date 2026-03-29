package com.bitbot.ui.screens.pilot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bitbot.data.model.ConnectionState
import com.bitbot.ui.screens.pilot.components.VirtualJoystick
import com.bitbot.util.Constants.PolicyMode

@Composable
fun PilotScreen(
    onNavigateBack: () -> Unit,
    viewModel: PilotViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    if (connectionState !is ConnectionState.Connected) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.LinkOff,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text("Not Connected", style = MaterialTheme.typography.titleLarge)
                Button(onClick = onNavigateBack) { Text("Go Back") }
            }
        }
        return
    }

    // Main landscape gamepad layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        // --- Top bar ---
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.disconnect(); onNavigateBack() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.ArrowBack, "Back", modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(4.dp))
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
            Spacer(Modifier.width(4.dp))
            Text("Connected", fontSize = 10.sp, color = Color(0xFF4CAF50))
        }

        // Top-center: Policy mode badge
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = policyColor(uiState.policyMode).copy(alpha = 0.3f)
        ) {
            Text(
                text = uiState.policyMode.label,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = policyColor(uiState.policyMode)
            )
        }

        // Top-right: Debug velocity readout
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = 0.7f)
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text("DEBUG", fontSize = 7.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                Text(
                    text = "RJ: x=%.2f y=%.2f".format(uiState.rightJoystickX, uiState.rightJoystickY),
                    fontSize = 8.sp, color = Color.Green, fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "LJ: x=%.2f y=%.2f".format(uiState.leftJoystickX, uiState.leftJoystickY),
                    fontSize = 8.sp, color = Color.Green, fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "vx=%.3f".format(uiState.velX),
                    fontSize = 8.sp, color = Color.Yellow, fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "vy=%.3f".format(uiState.velY),
                    fontSize = 8.sp, color = Color.Yellow, fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "vw=%.3f".format(uiState.velW),
                    fontSize = 8.sp, color = Color.Yellow, fontFamily = FontFamily.Monospace
                )
            }
        }

        // --- Main controls row ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(top = 36.dp, bottom = 8.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT: Yaw joystick
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                VirtualJoystick(
                    modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight(0.55f),
                    label = "Yaw",
                    onValueChange = { viewModel.updateLeftJoystick(it.x, it.y) }
                )
            }

            // CENTER: Action + Policy buttons + E-STOP
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(0.65f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                // Action buttons: 2x2 grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ActionBtn("PowerOn", Icons.Default.PowerSettingsNew, Color(0xFF4CAF50), Modifier.weight(1f)) {
                        viewModel.onPressY()
                    }
                    ActionBtn("InitPose", Icons.Default.AccessibilityNew, Color(0xFF9C27B0), Modifier.weight(1f)) {
                        viewModel.onPressA()
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ActionBtn("Start", Icons.Default.PlayArrow, Color(0xFF2196F3), Modifier.weight(1f)) {
                        viewModel.onPressB()
                    }
                    ActionBtn("Run", Icons.Default.DirectionsRun, Color(0xFFFF9800), Modifier.weight(1f)) {
                        viewModel.onRunPolicy()
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Policy buttons: row of FilterChips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PolicyBtn("Stand", PolicyMode.STANDING, uiState.policyMode, Modifier.weight(1f)) {
                        viewModel.onPressX()
                    }
                    PolicyBtn("Walk", PolicyMode.WALKING, uiState.policyMode, Modifier.weight(1f)) {
                        viewModel.onPressLB()
                    }
                    PolicyBtn("Robust", PolicyMode.ROBUST, uiState.policyMode, Modifier.weight(1f)) {
                        viewModel.onPressRB()
                    }
                }

                Spacer(Modifier.weight(1f))

                // E-STOP
                Button(
                    onClick = { viewModel.onRightTrigger(1f) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("E-STOP", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
            }

            // RIGHT: Move joystick
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                VirtualJoystick(
                    modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight(0.55f),
                    label = "Move",
                    onValueChange = { viewModel.updateRightJoystick(it.x, it.y) }
                )
            }
        }
    }
}

@Composable
private fun ActionBtn(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = color.copy(alpha = 0.15f),
            contentColor = color
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PolicyBtn(
    label: String,
    mode: PolicyMode,
    activeMode: PolicyMode,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isActive = mode == activeMode
    val color = policyColor(mode)

    FilterChip(
        selected = isActive,
        onClick = onClick,
        label = {
            Text(
                label,
                fontSize = 10.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
        },
        modifier = modifier.height(38.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color,
            selectedLabelColor = Color.White,
            containerColor = color.copy(alpha = 0.12f),
            labelColor = color
        ),
        shape = RoundedCornerShape(10.dp)
    )
}

private fun policyColor(mode: PolicyMode): Color = when (mode) {
    PolicyMode.STANDING -> Color(0xFF4CAF50)
    PolicyMode.WALKING -> Color(0xFFFFC107)
    PolicyMode.ROBUST -> Color(0xFF2196F3)
}
