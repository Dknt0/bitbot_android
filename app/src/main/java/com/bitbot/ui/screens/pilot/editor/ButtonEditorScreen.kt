@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bitbot.ui.screens.pilot.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bitbot.data.model.ButtonColors
import com.bitbot.data.model.ButtonConfig
import com.bitbot.data.model.ButtonLayouts

/**
 * Editor for the pilot panel's configurable buttons. Only reachable while
 * connected — the add-list comes from the server's event list.
 *
 * Landscape layout: preview + add-list on the left, button list and the
 * selected button's controls (label / position / size / color / delete) on
 * the right. Changes apply to DataStore on Save.
 */
@Composable
fun ButtonEditorScreen(
    onNavigateBack: () -> Unit,
    viewModel: ButtonEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selected = uiState.buttons.find { it.id == uiState.selectedId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Control Buttons") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::resetToDefault) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset to default")
                    }
                }
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Left: live preview + available events ---
            Column(
                modifier = Modifier.weight(0.55f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LayoutPreview(
                    buttons = uiState.buttons,
                    selectedId = uiState.selectedId,
                    onSelect = viewModel::select,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Available events", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val addedEvents = uiState.buttons.map { it.eventName }.toSet()
                    uiState.availableEvents.forEach { event ->
                        AssistChip(
                            onClick = { viewModel.addButton(event) },
                            label = {
                                Text(
                                    "${ButtonLayouts.prettifyLabel(event)} ($event)",
                                    fontSize = 11.sp
                                )
                            },
                            leadingIcon = if (event in addedEvents) {
                                { Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }
                            } else null
                        )
                    }
                }
            }

            // --- Right: button list + selected button controls ---
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Buttons (${uiState.buttons.size})", style = MaterialTheme.typography.titleSmall)

                if (uiState.buttons.isEmpty()) {
                    Text(
                        "No buttons. Add one from the available events on the left.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                uiState.buttons.forEach { button ->
                    ButtonListRow(
                        button = button,
                        isAvailable = button.eventName in uiState.availableEvents,
                        isSelected = button.id == uiState.selectedId,
                        onClick = { viewModel.select(button.id) }
                    )
                }

                if (selected != null) {
                    SelectedButtonEditor(
                        button = selected,
                        viewModel = viewModel
                    )
                }

                Button(
                    onClick = viewModel::save,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !uiState.isSaved
                ) {
                    Icon(if (uiState.isSaved) Icons.Default.Check else Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (uiState.isSaved) "Layout Saved" else "Save Layout")
                }
            }
        }
    }
}

/** Scaled-down rendering of the pilot panel: fixed controls faint, buttons at their positions. */
@Composable
private fun LayoutPreview(
    buttons: List<ButtonConfig>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF212121))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
    ) {
        // Reference panel width the dp sizes are meant for
        val scale = maxWidth / 800.dp
        val joystickSize = maxHeight * 0.45f

        // Joystick / E-STOP placeholders (not configurable)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp * scale)
                .size(joystickSize)
                .clip(CircleShape)
                .background(Color(0xFF2D2D2D))
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp * scale)
                .size(joystickSize)
                .clip(CircleShape)
                .background(Color(0xFF2D2D2D))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .fillMaxWidth(0.22f)
                .height(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFB71C1C).copy(alpha = 0.6f))
        )

        buttons.forEach { button ->
            val color = Color(button.colorARGB)
            val isSelected = button.id == selectedId
            Box(
                modifier = Modifier
                    .align(BiasAlignment(button.x * 2f - 1f, button.y * 2f - 1f))
                    .size(
                        width = button.sizeDp.dp * scale,
                        height = button.sizeDp.dp * ButtonConfig.HEIGHT_RATIO * scale
                    )
                    .clip(RoundedCornerShape(4.dp))
                    .background(color.copy(alpha = 0.8f))
                    .then(
                        if (isSelected) {
                            Modifier.border(2.dp, Color.White, RoundedCornerShape(4.dp))
                        } else Modifier
                    )
                    .clickable { onSelect(button.id) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    button.label,
                    fontSize = 7.sp,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ButtonListRow(
    button: ButtonConfig,
    isAvailable: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = Color(button.colorARGB)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
            Text(button.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (isAvailable) button.eventName else "${button.eventName} (unavailable)",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = if (isAvailable) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun SelectedButtonEditor(
    button: ButtonConfig,
    viewModel: ButtonEditorViewModel
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Edit: ${button.label}", style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value = button.label,
                onValueChange = viewModel::updateLabel,
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            LabeledSlider(
                label = "X",
                value = button.x,
                valueText = "%.2f".format(button.x),
                onValueChange = viewModel::updateX
            )
            LabeledSlider(
                label = "Y",
                value = button.y,
                valueText = "%.2f".format(button.y),
                onValueChange = viewModel::updateY
            )
            LabeledSlider(
                label = "Size",
                value = button.sizeDp,
                valueText = "%.0f dp".format(button.sizeDp),
                onValueChange = viewModel::updateSize,
                valueRange = ButtonConfig.MIN_SIZE_DP..ButtonConfig.MAX_SIZE_DP
            )

            Text("Color", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ButtonColors.PALETTE.forEach { c ->
                    val isSelected = c == button.colorARGB
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .then(
                                if (isSelected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                } else Modifier
                            )
                            .clickable { viewModel.updateColor(c) }
                    )
                }
            }

            OutlinedButton(
                onClick = viewModel::removeSelected,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Remove Button")
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueText: String,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(36.dp), style = MaterialTheme.typography.labelMedium)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f)
        )
        Text(
            valueText,
            modifier = Modifier.width(48.dp),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace
        )
    }
}
