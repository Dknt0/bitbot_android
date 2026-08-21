package com.bitbot.ui.screens.pilot

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bitbot.data.model.ButtonConfig
import com.bitbot.data.model.ConnectionState
import com.bitbot.ui.screens.pilot.components.VirtualJoystick
import com.bitbot.util.Constants.ButtonEvents
import com.bitbot.util.Constants.PolicyMode

@Composable
fun PilotScreen(
    onNavigateBack: () -> Unit,
    onNavigateToButtonEditor: () -> Unit,
    viewModel: PilotViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    var userDisconnecting by remember { mutableStateOf(false) }

    // Suppress overlay during explicit disconnect to avoid flash before navigation
    if (connectionState !is ConnectionState.Connected && !userDisconnecting) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (connectionState) {
                    is ConnectionState.Connecting, is ConnectionState.Error -> {
                        val errorMsg = (connectionState as? ConnectionState.Error)?.message
                        val transition = rememberInfiniteTransition(label = "spin")
                        val rotation by transition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "rotation"
                        )
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).rotate(rotation),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("Connection Lost", style = MaterialTheme.typography.titleLarge)
                        if (errorMsg != null) {
                            Text(
                                errorMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 2
                            )
                        }
                        Text(
                            "Retrying...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> { // Disconnected — explicit user action
                        Icon(
                            Icons.Default.LinkOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text("Not Connected", style = MaterialTheme.typography.titleLarge)
                    }
                }
                // Always show Go Back button when not connected
                Button(onClick = { viewModel.disconnect(); onNavigateBack() }) {
                    Text("Go Back")
                }
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
                onClick = { userDisconnecting = true; viewModel.disconnect(); onNavigateBack() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.ArrowBack, "Back", modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(4.dp))
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
            Spacer(Modifier.width(4.dp))
            Text("Connected", fontSize = 10.sp, color = Color(0xFF4CAF50))
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onNavigateToButtonEditor,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Tune, "Edit buttons", modifier = Modifier.size(18.dp))
            }
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

        // --- Joysticks (fixed) ---
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

            // Reserved center area (configurable buttons float here, see below)
            Spacer(Modifier.weight(0.65f).fillMaxHeight())

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

        // --- User-configurable buttons (free position over the whole panel) ---
        val activePolicyEvent = ButtonEvents.eventForPolicyMode(uiState.policyMode)
        uiState.buttons.forEach { config ->
            ConfigurableButton(
                config = config,
                isActive = config.eventName == activePolicyEvent,
                onPress = { viewModel.onButtonPress(config.eventName) },
                onRelease = { viewModel.onButtonRelease(config.eventName) },
                modifier = Modifier.align(BiasAlignment(config.x * 2f - 1f, config.y * 2f - 1f))
            )
        }

        // --- E-STOP (fixed safety control) ---
        Button(
            onClick = viewModel::onStopPress,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .fillMaxWidth(0.22f)
                .height(52.dp),
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
}

/**
 * A user-configured control button. Press sends event value 1 (key Down),
 * release sends value 2 (key Up). The button matching the active policy mode
 * is rendered highlighted.
 */
@Composable
private fun ConfigurableButton(
    config: ButtonConfig,
    isActive: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val color = Color(config.colorARGB)
    val container = when {
        pressed -> color.copy(alpha = 0.55f)
        isActive -> color
        else -> color.copy(alpha = 0.16f)
    }
    val content = if (pressed || isActive) Color.White else color
    val fontSize = (config.sizeDp * 0.16f).coerceIn(8f, 18f).sp

    Button(
        onClick = {},
        modifier = modifier.size(
            width = config.sizeDp.dp,
            height = (config.sizeDp * ButtonConfig.HEIGHT_RATIO).dp
        ),
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            config.label,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> onPress()
                is PressInteraction.Release, is PressInteraction.Cancel -> onRelease()
            }
        }
    }
}

private fun policyColor(mode: PolicyMode): Color = when (mode) {
    PolicyMode.STANDING -> Color(0xFF4CAF50)
    PolicyMode.WALKING -> Color(0xFFFFC107)
    PolicyMode.ROBUST -> Color(0xFF2196F3)
}
