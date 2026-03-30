package com.bitbot.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

enum class PanelType(val label: String, val icon: ImageVector) {
    PILOT("Pilot", Icons.Default.SportsEsports),
    DATA("Data", Icons.Default.Dashboard)
}

/**
 * Tap-to-toggle floating panel switcher at bottom-left.
 * Tap the FAB to show/hide a popup with panel options.
 */
@Composable
fun PanelSwitcher(
    activePanel: PanelType,
    onPanelSelected: (PanelType) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Popup menu — appears to the right of the FAB
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + slideInHorizontally { -it },
            exit = fadeOut() + slideOutHorizontally { -it },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 52.dp, bottom = 0.dp)
                .zIndex(1f)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                    .padding(8.dp)
            ) {
                PanelType.entries.forEach { panel ->
                    val isActive = panel == activePanel
                    val bgColor = if (isActive) MaterialTheme.colorScheme.primary
                                  else Color.Transparent
                    val contentColor = if (isActive) Color.White
                                       else MaterialTheme.colorScheme.onSurfaceVariant

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgColor)
                            .clickable {
                                onPanelSelected(panel)
                                isExpanded = false
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = panel.icon,
                            contentDescription = panel.label,
                            modifier = Modifier.size(20.dp),
                            tint = contentColor
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = panel.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = contentColor
                        )
                    }
                }
            }
        }

        // FAB trigger button
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(44.dp)
                .zIndex(2f)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    if (isExpanded) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                )
                .clickable { isExpanded = !isExpanded },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = activePanel.icon,
                contentDescription = "Switch panel",
                modifier = Modifier.size(22.dp),
                tint = if (isExpanded) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
