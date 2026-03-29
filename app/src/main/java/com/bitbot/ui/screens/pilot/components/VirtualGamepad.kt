package com.bitbot.ui.screens.pilot.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bitbot.ui.theme.JoystickBackground
import com.bitbot.ui.theme.JoystickKnob
import com.bitbot.util.normalizeJoystickValue
import kotlin.math.sqrt

data class JoystickValue(
    val x: Float,  // -1 to 1
    val y: Float   // -1 to 1
)

@Composable
fun VirtualJoystick(
    modifier: Modifier = Modifier,
    label: String = "",
    onValueChange: (JoystickValue) -> Unit = {},
    deadzone: Float = 0.1f,
    knobColor: Color = JoystickKnob,
    backgroundColor: Color = JoystickBackground
) {
    val density = LocalDensity.current

    // Use STATE variables so pointerInput coroutine reads live values
    var viewSize by remember { mutableIntStateOf(0) }
    var normX by remember { mutableFloatStateOf(0f) }
    var normY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    // Derived from state — accessible inside pointerInput
    val maxDragPx = viewSize * 0.35f
    val knobPx = viewSize * 0.18f
    val knobDp = with(density) { (knobPx * 2).toDp() }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxSize()
                .onSizeChanged { viewSize = minOf(it.width, it.height) }
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            backgroundColor.copy(alpha = if (isDragging) 0.8f else 0.5f),
                            backgroundColor
                        )
                    ),
                    shape = CircleShape
                )
                .pointerInput(deadzone) {
                    // Reads viewSize/maxDragPx from outer state — always current
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        isDragging = true

                        val cx = viewSize / 2f
                        val cy = viewSize / 2f
                        val maxR = viewSize * 0.35f

                        fun offsetToNorm(offset: Offset) {
                            val dx = offset.x - cx
                            val dy = offset.y - cy
                            val dist = sqrt(dx * dx + dy * dy)
                            if (maxR > 0 && dist <= maxR) {
                                normX = (dx / maxR).coerceIn(-1f, 1f)
                                normY = (dy / maxR).coerceIn(-1f, 1f)
                            } else if (maxR > 0) {
                                normX = (dx / dist).coerceIn(-1f, 1f)
                                normY = (dy / dist).coerceIn(-1f, 1f)
                            }
                        }

                        // Initial touch position
                        offsetToNorm(down.position)
                        val (fx, fy) = normalizeJoystickValue(normX, normY, deadzone)
                        onValueChange(JoystickValue(fx, fy))

                        // Track pointer movement
                        var current = down
                        while (current.pressed) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            current = change

                            offsetToNorm(change.position)
                            val (nfx, nfy) = normalizeJoystickValue(normX, normY, deadzone)
                            onValueChange(JoystickValue(nfx, nfy))
                        }

                        // Released — return to center
                        isDragging = false
                        normX = 0f
                        normY = 0f
                        onValueChange(JoystickValue(0f, 0f))
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset((normX * maxDragPx).toInt(), (normY * maxDragPx).toInt()) }
                    .size(knobDp)
                    .background(
                        color = if (isDragging) knobColor else knobColor.copy(alpha = 0.7f),
                        shape = CircleShape
                    )
            )
        }

        if (label.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
