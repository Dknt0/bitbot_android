package com.bitbot.copilot.ui.screens.pilot.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

data class TrackpadValue(
    val x: Float,  // -1 to 1
    val y: Float   // -1 to 1
)

@Composable
fun VirtualTrackpad(
    modifier: Modifier = Modifier,
    onValueChange: (TrackpadValue) -> Unit = {},
    onRelease: () -> Unit = {},
    label: String = "Trackpad",
    sensitivity: Float = 1f
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var currentOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .onSizeChanged { size = it }
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                )
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            currentOffset = offset
                        },
                        onDragEnd = {
                            isDragging = false
                            currentOffset = Offset.Zero
                            onRelease()
                        },
                        onDragCancel = {
                            isDragging = false
                            currentOffset = Offset.Zero
                            onRelease()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            currentOffset += dragAmount

                            val normalizedX = if (size.width > 0) {
                                (currentOffset.x / size.width * 2 - 1).coerceIn(-1f, 1f) * sensitivity
                            } else 0f

                            val normalizedY = if (size.height > 0) {
                                (currentOffset.y / size.height * 2 - 1).coerceIn(-1f, 1f) * sensitivity
                            } else 0f

                            onValueChange(TrackpadValue(normalizedX, normalizedY))
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (isDragging) {
                // Visual indicator for touch position
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(50)
                        )
                )
            }

            Text(
                text = if (isDragging) "X: ${"%.2f".format(currentOffset.x / size.width * 2 - 1)}\nY: ${"%.2f".format(currentOffset.y / size.height * 2 - 1)}" else "Touch to control",
                style = MaterialTheme.typography.bodySmall,
                color = if (isDragging) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
