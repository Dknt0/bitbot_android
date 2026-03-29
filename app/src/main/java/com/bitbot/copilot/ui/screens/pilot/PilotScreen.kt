package com.bitbot.copilot.ui.screens.pilot

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bitbot.copilot.data.model.ConnectionState
import com.bitbot.copilot.ui.screens.pilot.components.VirtualJoystick
import com.bitbot.copilot.util.Constants
import com.bitbot.copilot.util.Constants.PolicyMode

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
                Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
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
        // Top-left: Back + status
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.disconnect(); onNavigateBack() }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, "Back", modifier = Modifier.size(20.dp))
            }
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
            Spacer(Modifier.width(4.dp))
            Text("OK", fontSize = 10.sp, color = Color(0xFF4CAF50))
        }

        // Top-center: Policy mode
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            shape = RoundedCornerShape(12.dp),
            color = when (uiState.policyMode) {
                PolicyMode.STANDING -> Color(0xFF4CAF50)
                PolicyMode.WALKING -> Color(0xFFFFC107)
                PolicyMode.ROBUST -> Color(0xFF2196F3)
            }.copy(alpha = 0.25f)
        ) {
            Text(
                text = uiState.policyMode.label,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        // Debug: velocity readout (top-right)
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = 0.6f)
        ) {
            val mode = uiState.policyMode
            val vx = Constants.scaleVelocity(-uiState.rightJoystickY, mode.defaultVelX)
            val vy = Constants.scaleVelocity(-uiState.rightJoystickX, mode.defaultVelY)
            val vw = Constants.scaleVelocity(-uiState.leftJoystickX, mode.defaultVelYaw)
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text("DEBUG", fontSize = 8.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                Text(
                    text = "LJ: x=%.2f y=%.2f".format(uiState.leftJoystickX, uiState.leftJoystickY),
                    fontSize = 9.sp, color = Color.Green, fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "RJ: x=%.2f y=%.2f".format(uiState.rightJoystickX, uiState.rightJoystickY),
                    fontSize = 9.sp, color = Color.Green, fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "vel_x=%.3f".format(vx),
                    fontSize = 9.sp, color = Color.Yellow, fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "vel_y=%.3f".format(vy),
                    fontSize = 9.sp, color = Color.Yellow, fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "vel_w=%.3f".format(vw),
                    fontSize = 9.sp, color = Color.Yellow, fontFamily = FontFamily.Monospace
                )
            }
        }

        // Main controls row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(top = 40.dp, bottom = 8.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT: Yaw joystick only
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                VirtualJoystick(
                    modifier = Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.6f),
                    label = "Yaw",
                    onValueChange = { viewModel.updateLeftJoystick(it.x, it.y) }
                )
            }

            // CENTER: Action buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(0.5f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                // Top row: Power On, Start, Run Policy
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionBtn("Power On", Color(0xFF4CAF50)) { viewModel.onPressY() }
                    ActionBtn("Start", Color(0xFF2196F3)) { viewModel.onPressB() }
                    ActionBtn("Run Policy", Color(0xFFFF9800)) { viewModel.onRunPolicy() }
                }

                Spacer(Modifier.height(8.dp))

                // Middle row: Standing, Walking, Robust
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PolicyBtn("Standing", PolicyMode.STANDING, uiState.policyMode) { viewModel.onPressX() }
                    PolicyBtn("Walking", PolicyMode.WALKING, uiState.policyMode) { viewModel.onPressLB() }
                    PolicyBtn("Robust", PolicyMode.ROBUST, uiState.policyMode) { viewModel.onPressRB() }
                }

                Spacer(Modifier.weight(1f)) // Large gap before E-STOP

                // E-STOP: big red button
                Button(
                    onClick = { viewModel.onRightTrigger(1f) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp)
                ) {
                    Icon(Icons.Default.Warning, null, modifier = Modifier.size(20.dp), tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("E-STOP", fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
            }

            // RIGHT: Move joystick only
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                VirtualJoystick(
                    modifier = Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.6f),
                    label = "Move",
                    onValueChange = { viewModel.updateRightJoystick(it.x, it.y) }
                )
            }
        }
    }
}

@Composable
private fun ActionBtn(label: String, color: Color, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(44.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun PolicyBtn(label: String, mode: PolicyMode, activeMode: PolicyMode, onClick: () -> Unit) {
    val isActive = mode == activeMode
    if (isActive) {
        Button(
            onClick = onClick,
            modifier = Modifier.height(40.dp),
            colors = ButtonDefaults.buttonColors(containerColor = when (mode) {
                PolicyMode.STANDING -> Color(0xFF4CAF50)
                PolicyMode.WALKING -> Color(0xFFFFC107)
                PolicyMode.ROBUST -> Color(0xFF2196F3)
            }),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.height(40.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = when (mode) {
                PolicyMode.STANDING -> Color(0xFF4CAF50)
                PolicyMode.WALKING -> Color(0xFFFFC107)
                PolicyMode.ROBUST -> Color(0xFF2196F3)
            })
        }
    }
}
